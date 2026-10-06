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
import de.gematik.refpopp.popp_server.tokengeneration.PoppTokenGenerationRecordService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PoppRecordRepositoryIT extends BaseIntegrationTest {

  @Autowired private PoppMessageRecordRepository messageRepository;
  @Autowired private PoppTokenGenerationRecordRepository poppRecordRepository;
  @Autowired private PoppTokenGenerationRecordService service;

  @Test
  void persistsRecordsLinkedBySharedMessageId() {
    final var messageId = UUID.randomUUID();
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
    final var message =
        service.create("A123456781", "123456789", "actor-completion", "workplace-id", "client-id");

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
    final var first = service.create("A123456782", "123456789", "actor-pending", null, "client-id");

    final var second =
        service.create(
            "A123456782", "123456789", "actor-pending", "other-workplace", "other-client");

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(poppRecordRepository.findById(first.getId()))
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
      final var futures = new ArrayList<Future<PoppMessageRecord>>();
      for (var request = 0; request < requestCount; request++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return service.create(
                      "A123456784", "123456789", "actor-concurrent", "workplace-id", "client-id");
                }));
      }
      ready.await();
      start.countDown();

      final var messageIds = new HashSet<UUID>();
      for (final var future : futures) {
        messageIds.add(future.get().getId());
      }

      assertThat(messageIds).hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void expirationKeepsCanceledMessageAndDeletesOnlyPoppRecord() {
    final var messageId = UUID.randomUUID();
    final var timestamp = Instant.now().minus(Duration.ofHours(73));
    final var completedMessage =
        new PoppMessageRecord(UUID.randomUUID(), "completed-client", timestamp);
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
}
