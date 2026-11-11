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

package de.gematik.refpopp.popp_server.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.refpopp.popp_server.model.PoppMessageRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import de.gematik.refpopp.popp_server.scenario.BaseIntegrationTest;
import de.gematik.refpopp.popp_server.tokengeneration.PendingTokenDeliveryLookupService;
import de.gematik.refpopp.popp_server.tokengeneration.PoppRecordCreationResult;
import de.gematik.refpopp.popp_server.tokengeneration.PoppTokenGenerationRecordService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class PoppRecordRepositoryIT extends BaseIntegrationTest {

  @Autowired private PoppMessageRecordRepository messageRepository;
  @Autowired private PoppTokenGenerationRecordRepository poppRecordRepository;
  @Autowired private PoppTokenGenerationRecordService service;
  @Autowired private PendingTokenDeliveryLookupService lookupService;

  private final Set<UUID> createdIds = ConcurrentHashMap.newKeySet();

  @AfterEach
  void removeTestRecords() {
    poppRecordRepository.deleteAllById(createdIds);
    messageRepository.deleteAllById(createdIds);
  }

  @Test
  void detectsOnlyPendingUnexpiredRecordsForActor() {
    final var createdAt = Instant.now().minus(Duration.ofHours(1));
    final var first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    final var second = UUID.fromString("00000000-0000-0000-0000-000000000002");
    persistRecord(
        first, "actor-lookup", "workplace-a", createdAt, PoppTokenGenerationStatus.PENDING);
    persistRecord(
        second, "actor-lookup", "workplace-b", createdAt, PoppTokenGenerationStatus.PENDING);
    persistRecord(
        UUID.randomUUID(),
        "actor-lookup",
        "workplace-a",
        createdAt.minus(Duration.ofHours(73)),
        PoppTokenGenerationStatus.PENDING);
    persistRecord(
        UUID.randomUUID(),
        "actor-lookup",
        "workplace-a",
        createdAt,
        PoppTokenGenerationStatus.SUCCESS);
    persistRecord(
        UUID.randomUUID(),
        "another-actor",
        "workplace-a",
        createdAt,
        PoppTokenGenerationStatus.PENDING);

    assertThat(lookupService.hasPending("actor-lookup", null)).isTrue();
    assertThat(lookupService.hasPending("actor-lookup", List.of())).isTrue();
    service.markTokenDelivered(first);
    assertThat(lookupService.hasPending("actor-lookup", null)).isTrue();
    service.markTokenDelivered(second);
    assertThat(lookupService.hasPending("actor-lookup", null)).isFalse();
  }

  @Test
  void filtersByWorkplaceAndReturnsNoResultsForUnknownWorkplaces() {
    final var createdAt = Instant.now().minus(Duration.ofMinutes(5));
    persistRecord(
        UUID.randomUUID(),
        "actor-workplace-filter",
        "workplace-b",
        createdAt,
        PoppTokenGenerationStatus.PENDING);
    persistRecord(
        UUID.randomUUID(),
        "actor-workplace-filter",
        "workplace-a",
        createdAt,
        PoppTokenGenerationStatus.PENDING);
    persistRecord(
        UUID.randomUUID(),
        "actor-workplace-filter",
        null,
        createdAt,
        PoppTokenGenerationStatus.PENDING);

    assertThat(
            lookupService.hasPending(
                "actor-workplace-filter", List.of("workplace-b", "workplace-c")))
        .isTrue();
    assertThat(lookupService.hasPending("actor-workplace-filter", List.of("unknown"))).isFalse();
    assertThat(lookupService.hasPending("actor-workplace-filter-unknown", null)).isFalse();
  }

  private UUID persistRecord(
      UUID id,
      String actorId,
      String workplaceId,
      Instant createdAt,
      PoppTokenGenerationStatus status) {
    createdIds.add(id);
    final var message = new PoppMessageRecord(id, "client-id", createdAt);
    message.setStatus(status);
    messageRepository.saveAndFlush(message);
    poppRecordRepository.saveAndFlush(
        new PoppTokenGenerationRecord(
            id, "A123456780", "123456789", actorId, workplaceId, createdAt));
    return id;
  }

  @Test
  void persistsRecordsLinkedBySharedMessageId() {
    final var messageId = UUID.randomUUID();
    createdIds.add(messageId);
    final var timestamp = Instant.parse("2026-09-22T10:00:00Z");
    final var message = new PoppMessageRecord(messageId, "client-id", timestamp);
    final var persistedMessage = messageRepository.saveAndFlush(message);
    poppRecordRepository.saveAndFlush(
        new PoppTokenGenerationRecord(
            persistedMessage.getId(),
            "A123456780",
            "123456789",
            "actor-persistence",
            "workplace-id",
            timestamp));

    assertThat(poppRecordRepository.findById(messageId))
        .get()
        .extracting(PoppTokenGenerationRecord::getMessageId)
        .isEqualTo(messageId);

    assertThat(messageRepository.findById(messageId)).isPresent();
  }

  @Test
  void completionKeepsMessageAndDeletesOnlyPoppRecord() {
    final var result = createRecord("A123456781", "actor-completion", "workplace-id", "client-id");
    final var message = result.message();

    assertThat(result.created()).isTrue();

    service.markTokenDelivered(message.getId());
    service.markPushSent(message.getId());

    assertThat(messageRepository.findById(message.getId()))
        .get()
        .satisfies(
            persistedMessage -> {
              assertThat(persistedMessage.getStatus()).isEqualTo(PoppTokenGenerationStatus.SUCCESS);
              assertThat(persistedMessage.getTokenDeliveredAt()).isNotNull();
              assertThat(persistedMessage.getPushSentAt()).isNotNull();
            });
    assertThat(poppRecordRepository.findById(message.getId())).isEmpty();
  }

  @Test
  void createReturnsExistingPendingMessageForPatientAndActor() {
    final var first = createRecord("A123456782", "actor-pending", null, "client-id");

    final var second =
        createRecord("A123456782", "actor-pending", "other-workplace", "other-client");

    assertThat(first.created()).isTrue();
    assertThat(second.created()).isFalse();
    assertThat(second.message().getId()).isEqualTo(first.message().getId());
    assertThat(poppRecordRepository.findById(first.message().getId()))
        .get()
        .extracting(PoppTokenGenerationRecord::getWorkplaceId)
        .isNull();
  }

  @Test
  void concurrentCreateReturnsSinglePendingMessage() throws Exception {
    final var requestCount = 6;
    final var ready = new CountDownLatch(requestCount);
    final var start = new CountDownLatch(1);
    final var executor = Executors.newFixedThreadPool(requestCount);
    try {
      final var futures = new ArrayList<Future<PoppRecordCreationResult>>();
      for (var request = 0; request < requestCount; request++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return createRecord(
                      "A123456784", "actor-concurrent", "workplace-id", "client-id");
                }));
      }
      ready.await();
      start.countDown();

      final var messageIds = new HashSet<UUID>();
      var createdCount = 0;
      for (final var future : futures) {
        final var result = future.get();
        messageIds.add(result.message().getId());
        if (result.created()) {
          createdCount++;
        }
      }

      assertThat(messageIds).hasSize(1);
      assertThat(createdCount).isEqualTo(1);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  @Transactional
  void expirationKeepsCanceledMessageAndDeletesOnlyPoppRecord() {
    final var messageId = UUID.randomUUID();
    final var timestamp = Instant.now().minus(Duration.ofHours(73));
    final var completedMessage =
        new PoppMessageRecord(UUID.randomUUID(), "completed-client", timestamp);
    createdIds.add(messageId);
    createdIds.add(completedMessage.getId());
    completedMessage.setStatus(PoppTokenGenerationStatus.SUCCESS);
    messageRepository.saveAndFlush(completedMessage);
    messageRepository.saveAndFlush(new PoppMessageRecord(messageId, "client-id", timestamp));

    poppRecordRepository.saveAndFlush(
        new PoppTokenGenerationRecord(
            messageId, "A123456783", "123456789", "actor-expiration", "workplace-id", timestamp));

    service.expireRecords();

    assertThat(messageRepository.findById(messageId))
        .get()
        .extracting(PoppMessageRecord::getStatus)
        .isEqualTo(PoppTokenGenerationStatus.CANCELED);
    assertThat(poppRecordRepository.findById(messageId)).isEmpty();
    assertThat(messageRepository.findById(completedMessage.getId()))
        .get()
        .extracting(PoppMessageRecord::getStatus)
        .isEqualTo(PoppTokenGenerationStatus.SUCCESS);
  }

  private PoppRecordCreationResult createRecord(
      String patientId, String actorId, String workplaceId, String clientId) {
    final var result = service.create(patientId, "123456789", actorId, workplaceId, clientId);
    if (result.created()) {
      createdIds.add(result.message().getId());
    }
    return result;
  }
}
