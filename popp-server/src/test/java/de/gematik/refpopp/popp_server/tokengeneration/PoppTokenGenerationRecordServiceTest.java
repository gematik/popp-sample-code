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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.model.PoppMessageRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import de.gematik.refpopp.popp_server.repository.PoppMessageRecordRepository;
import de.gematik.refpopp.popp_server.repository.PoppTokenGenerationRecordRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PoppTokenGenerationRecordServiceTest {

  private PoppMessageRecordRepository messageRepositoryMock;
  private PoppTokenGenerationRecordRepository poppRecordRepositoryMock;
  private PoppRecordCreationLock creationLockMock;
  private PoppTokenGenerationRecordService sut;

  @BeforeEach
  void setUp() {
    messageRepositoryMock = mock(PoppMessageRecordRepository.class);
    poppRecordRepositoryMock = mock(PoppTokenGenerationRecordRepository.class);
    creationLockMock = mock(PoppRecordCreationLock.class);
    sut =
        new PoppTokenGenerationRecordService(
            messageRepositoryMock, poppRecordRepositoryMock, creationLockMock);
  }

  @Test
  void createReturnsMessageAndPersistsLinkedRecords() {
    when(poppRecordRepositoryMock.findMessage(
            "A123456789", "actor-id", PoppTokenGenerationStatus.PENDING))
        .thenReturn(Optional.empty());
    when(messageRepositoryMock.save(any(PoppMessageRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(poppRecordRepositoryMock.save(any(PoppTokenGenerationRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    final var earliestCreationTime = Instant.now();

    final var result =
        sut.create("A123456789", "123456789", "actor-id", "workplace-id", "client-id");
    final var latestCreationTime = Instant.now();

    final var messageCaptor = ArgumentCaptor.forClass(PoppMessageRecord.class);
    final var poppRecordCaptor = ArgumentCaptor.forClass(PoppTokenGenerationRecord.class);
    verify(messageRepositoryMock).save(messageCaptor.capture());
    verify(poppRecordRepositoryMock).save(poppRecordCaptor.capture());
    verify(creationLockMock).acquire("A123456789", "actor-id");
    final var message = messageCaptor.getValue();
    final var poppRecord = poppRecordCaptor.getValue();
    assertThat(result).isSameAs(message);
    assertThat(poppRecord.getMessageId()).isEqualTo(message.getId());
    assertThat(message.getClientId()).isEqualTo("client-id");
    assertThat(message.getStatus()).isEqualTo(PoppTokenGenerationStatus.PENDING);
    assertThat(message.getCreatedAt()).isBetween(earliestCreationTime, latestCreationTime);
    assertThat(message.getExpiresAt()).isEqualTo(message.getCreatedAt().plus(Duration.ofHours(72)));
    assertThat(poppRecord.getTimestamp()).isEqualTo(message.getCreatedAt());
    assertThat(poppRecord.getPatientId()).isEqualTo("A123456789");
    assertThat(poppRecord.getProofMethod()).isEqualTo("healthid");
  }

  @Test
  void createReturnsExistingPendingMessageWithoutPersistingNewRecords() {
    final var existingMessage = createMessage();
    when(poppRecordRepositoryMock.findMessage(
            "A123456789", "actor-id", PoppTokenGenerationStatus.PENDING))
        .thenReturn(Optional.of(existingMessage));

    final var result = sut.create("A123456789", "123456789", "actor-id", null, "client-id");

    assertThat(result).isSameAs(existingMessage);
    verify(creationLockMock).acquire("A123456789", "actor-id");
    verify(messageRepositoryMock, never()).save(any());
    verify(poppRecordRepositoryMock, never()).save(any());
  }

  @Test
  void markTokenDeliveredMarksPendingMessageAsSuccessful() {
    final var message = createMessage();
    when(messageRepositoryMock.findById(message.getId())).thenReturn(Optional.of(message));
    when(messageRepositoryMock.save(message)).thenReturn(message);
    final var earliestDeliveryTime = Instant.now();

    final var result = sut.markTokenDelivered(message.getId());
    final var latestDeliveryTime = Instant.now();

    assertThat(result).isSameAs(message);
    assertThat(message.getStatus()).isEqualTo(PoppTokenGenerationStatus.SUCCESS);
    assertThat(message.getTokenDeliveredAt()).isBetween(earliestDeliveryTime, latestDeliveryTime);
    verify(messageRepositoryMock).save(message);
    verify(messageRepositoryMock, never()).delete(message);
  }

  @Test
  void markPushSentRetainsMessageAndDeletesCompletedPoppRecord() {
    final var message = createMessage();
    message.setStatus(PoppTokenGenerationStatus.SUCCESS);
    message.setTokenDeliveredAt(Instant.parse("2026-09-21T13:00:00Z"));
    when(messageRepositoryMock.findById(message.getId())).thenReturn(Optional.of(message));
    when(messageRepositoryMock.save(message)).thenReturn(message);
    final var earliestPushTime = Instant.now();

    sut.markPushSent(message.getId());
    final var latestPushTime = Instant.now();

    assertThat(message.getPushSentAt()).isBetween(earliestPushTime, latestPushTime);
    final var order = inOrder(messageRepositoryMock, poppRecordRepositoryMock);
    order.verify(messageRepositoryMock).save(message);
    order.verify(poppRecordRepositoryMock).deleteById(message.getId());
    verify(messageRepositoryMock, never()).delete(message);
  }

  @Test
  void markTokenDeliveredRetainsMessageAndDeletesCompletedPoppRecord() {
    final var message = createMessage();
    message.setPushSentAt(Instant.parse("2026-09-21T13:00:00Z"));
    when(messageRepositoryMock.findById(message.getId())).thenReturn(Optional.of(message));
    when(messageRepositoryMock.save(message)).thenReturn(message);

    sut.markTokenDelivered(message.getId());

    verify(poppRecordRepositoryMock).deleteById(message.getId());
    verify(messageRepositoryMock, never()).delete(message);
  }

  @Test
  void markTokenDeliveredDoesNotChangeCanceledMessage() {
    final var message = createMessage();
    message.setStatus(PoppTokenGenerationStatus.CANCELED);
    when(messageRepositoryMock.findById(message.getId())).thenReturn(Optional.of(message));

    final var result = sut.markTokenDelivered(message.getId());

    assertThat(result).isSameAs(message);
    assertThat(message.getTokenDeliveredAt()).isNull();
    verify(messageRepositoryMock, never()).save(any());
  }

  @Test
  void markPushSentDoesNotChangeCanceledMessage() {
    final var message = createMessage();
    message.setStatus(PoppTokenGenerationStatus.CANCELED);
    when(messageRepositoryMock.findById(message.getId())).thenReturn(Optional.of(message));

    final var result = sut.markPushSent(message.getId());

    assertThat(result).isSameAs(message);
    assertThat(message.getPushSentAt()).isNull();
    verify(messageRepositoryMock, never()).save(any());
  }

  @Test
  void markTokenDeliveredRejectsUnknownMessageId() {
    final var messageId = UUID.randomUUID();
    when(messageRepositoryMock.findById(messageId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.markTokenDelivered(messageId))
        .isInstanceOf(PoppTokenGenerationRecordNotFoundException.class)
        .hasMessage("PoPP token generation record not found for messageId: " + messageId);
  }

  @Test
  void markPushSentRejectsUnknownMessageId() {
    final var messageId = UUID.randomUUID();
    when(messageRepositoryMock.findById(messageId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.markPushSent(messageId))
        .isInstanceOf(PoppTokenGenerationRecordNotFoundException.class)
        .hasMessage("PoPP token generation record not found for messageId: " + messageId);
  }

  @Test
  void expireRecordsCancelsMessagesAndDeletesOnlyPoppRecords() {
    final var first = createMessage();
    final var second = createMessage();
    when(poppRecordRepositoryMock.findExpiredMessages(any(Instant.class)))
        .thenReturn(List.of(first, second));

    sut.expireRecords();

    assertThat(first.getStatus()).isEqualTo(PoppTokenGenerationStatus.CANCELED);
    assertThat(second.getStatus()).isEqualTo(PoppTokenGenerationStatus.CANCELED);
    final var order = inOrder(messageRepositoryMock, poppRecordRepositoryMock);
    order.verify(messageRepositoryMock).saveAllAndFlush(List.of(first, second));
    order.verify(poppRecordRepositoryMock).deleteAllById(List.of(first.getId(), second.getId()));
    verify(messageRepositoryMock, never()).deleteAll(any());
  }

  private PoppMessageRecord createMessage() {
    return new PoppMessageRecord(
        UUID.randomUUID(), "client-id", Instant.parse("2026-09-21T12:00:00Z"));
  }
}
