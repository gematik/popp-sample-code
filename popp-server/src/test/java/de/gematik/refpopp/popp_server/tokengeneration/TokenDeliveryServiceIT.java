/*
 * Copyright (Date see Readme), gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */

package de.gematik.refpopp.popp_server.tokengeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.controller.dto.TokenDeliveryItem;
import de.gematik.refpopp.popp_server.model.PoppMessageRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import de.gematik.refpopp.popp_server.repository.PoppMessageRecordRepository;
import de.gematik.refpopp.popp_server.repository.PoppTokenGenerationRecordRepository;
import de.gematik.refpopp.popp_server.scenario.BaseIntegrationTest;
import de.gematik.refpopp.popp_server.scenario.common.token.PoppTokenCreator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class TokenDeliveryServiceIT extends BaseIntegrationTest {

  @Autowired private TokenDeliveryService service;
  @Autowired private PoppMessageRecordRepository messageRepository;
  @Autowired private PoppTokenGenerationRecordRepository recordRepository;
  @MockitoBean private PoppTokenCreator tokenCreator;
  @MockitoSpyBean private PoppTokenGenerationRecordService recordService;

  private final List<UUID> createdIds = new ArrayList<>();

  @AfterEach
  void removeTestRecords() {
    recordRepository.deleteAllById(createdIds);
    messageRepository.deleteAllById(createdIds);
  }

  @Test
  void distinguishesUnknownRecordsFromKnownRecordsWithoutPendingTokens() {
    final var actor = UUID.randomUUID().toString();
    final var unknownWorkplaces = List.of("unknown-workplace");
    assertThatThrownBy(() -> service.deliver(actor, null, 1))
        .isInstanceOf(TokenDeliveryNotFoundException.class);

    final var id = persist(actor, "workplace-a");
    assertThatThrownBy(() -> service.deliver(actor, unknownWorkplaces, 1))
        .isInstanceOf(TokenDeliveryNotFoundException.class);
    final var message = messageRepository.findById(id).orElseThrow();
    message.setStatus(PoppTokenGenerationStatus.SUCCESS);
    messageRepository.saveAndFlush(message);

    final var response = service.deliver(actor, List.of(), 1);
    assertThat(response.poppTokens()).isEmpty();
    assertThat(response.hasMore()).isFalse();
  }

  @Test
  void deliversOnlyMatchingRecordsOnceAndReportsRemaining() {
    final var actor = UUID.randomUUID().toString();
    final var first = persist(actor, "workplace-a");
    final var second = persist(actor, "workplace-a");
    persist(actor, "workplace-b");
    when(tokenCreator.createPoppToken(any(PoppTokenGenerationRecord.class)))
        .thenAnswer(
            invocation ->
                "jwt-" + invocation.<PoppTokenGenerationRecord>getArgument(0).getMessageId());

    final var firstBatch = service.deliver(actor, List.of("workplace-a"), 1);
    assertThat(firstBatch.poppTokens()).hasSize(1);
    assertThat(firstBatch.poppTokens().getFirst().token())
        .isEqualTo("jwt-" + firstBatch.poppTokens().getFirst().poppStatusRecordId());
    assertThat(firstBatch.poppTokens().getFirst().workplaceId()).isEqualTo("workplace-a");
    assertThat(firstBatch.hasMore()).isTrue();

    final var secondBatch = service.deliver(actor, List.of("workplace-a"), 10);
    assertThat(secondBatch.poppTokens()).hasSize(1);
    assertThat(secondBatch.hasMore()).isFalse();
    assertThat(
            List.of(
                firstBatch.poppTokens().getFirst().poppStatusRecordId(),
                secondBatch.poppTokens().getFirst().poppStatusRecordId()))
        .containsExactlyInAnyOrder(first, second);
    assertThat(service.deliver(actor, List.of("workplace-a"), 10).poppTokens()).isEmpty();
    assertThat(service.deliver(actor, List.of(), 10).poppTokens()).hasSize(1);
    assertThat(messageRepository.findById(first))
        .get()
        .satisfies(
            message -> {
              assertThat(message.getStatus()).isEqualTo(PoppTokenGenerationStatus.SUCCESS);
              assertThat(message.getTokenDeliveredAt()).isNotNull();
            });
  }

  @Test
  void retainsPendingRecordsIfSigningFails() {
    final var actor = UUID.randomUUID().toString();
    final var first = persist(actor, "workplace-a");
    final var second = persist(actor, "workplace-a");
    final var signed = new AtomicInteger();
    when(tokenCreator.createPoppToken(any(PoppTokenGenerationRecord.class)))
        .thenAnswer(
            invocation -> {
              if (signed.incrementAndGet() == 2) {
                throw new IllegalStateException("signing failed");
              }
              return "jwt";
            });

    assertThatThrownBy(() -> service.deliver(actor, null, 2))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("signing failed");
    for (final var id : List.of(first, second)) {
      assertThat(messageRepository.findById(id))
          .get()
          .satisfies(
              message -> {
                assertThat(message.getStatus()).isEqualTo(PoppTokenGenerationStatus.PENDING);
                assertThat(message.getTokenDeliveredAt()).isNull();
              });
    }
  }

  @Test
  void rollsBackStatusWhenProcessingFailsAfterFirstUpdate() {
    final var actor = UUID.randomUUID().toString();
    final var first = persist(actor, "workplace-a");
    final var second = persist(actor, "workplace-a");
    when(tokenCreator.createPoppToken(any(PoppTokenGenerationRecord.class))).thenReturn("jwt");
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("delivery failed");
            })
        .when(recordService)
        .markTokenDelivered(any(UUID.class));

    assertThatThrownBy(() -> service.deliver(actor, null, 2))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("delivery failed");
    for (final var id : List.of(first, second)) {
      assertThat(messageRepository.findById(id))
          .get()
          .satisfies(
              message -> {
                assertThat(message.getStatus()).isEqualTo(PoppTokenGenerationStatus.PENDING);
                assertThat(message.getTokenDeliveredAt()).isNull();
              });
    }
  }

  @Test
  void parallelDeliveriesDoNotSelectTheSamePendingRecord() throws Exception {
    final var actor = UUID.randomUUID().toString();
    final var id = persist(actor, "workplace-a");
    final var signingStarted = new CountDownLatch(1);
    final var resumeSigning = new CountDownLatch(1);
    when(tokenCreator.createPoppToken(any(PoppTokenGenerationRecord.class)))
        .thenAnswer(
            invocation -> {
              signingStarted.countDown();
              if (!resumeSigning.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Signing was not released");
              }
              return "jwt";
            });

    try (final var executor = Executors.newFixedThreadPool(2)) {
      final var first = executor.submit(() -> service.deliver(actor, null, 1));
      try {
        assertThat(signingStarted.await(15, TimeUnit.SECONDS)).isTrue();
        final var second = executor.submit(() -> service.deliver(actor, null, 1));
        final var skipped = second.get(15, TimeUnit.SECONDS);
        assertThat(skipped.poppTokens()).isEmpty();
        assertThat(skipped.hasMore()).isTrue();
      } finally {
        resumeSigning.countDown();
      }
      assertThat(first.get(15, TimeUnit.SECONDS).poppTokens())
          .singleElement()
          .extracting(TokenDeliveryItem::poppStatusRecordId)
          .isEqualTo(id);
    }
  }

  private UUID persist(String actor, String workplace) {
    final var id = UUID.randomUUID();
    createdIds.add(id);
    final var now = Instant.now();
    messageRepository.saveAndFlush(new PoppMessageRecord(id, "client-id", now));
    recordRepository.saveAndFlush(
        new PoppTokenGenerationRecord(id, "A123456789", "123456789", actor, workplace, now));
    return id;
  }
}
