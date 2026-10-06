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

import de.gematik.refpopp.popp_server.model.PoppMessageRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import de.gematik.refpopp.popp_server.repository.PoppMessageRecordRepository;
import de.gematik.refpopp.popp_server.repository.PoppTokenGenerationRecordRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the persistence and lifecycle of PoPP token generation and message records.
 *
 * <p>A PoPP record and its associated message share the same identifier. The PoPP data is retained
 * until the token has been delivered and the push notification has been sent, or until the 72-hour
 * retention period expires.
 */
@Service
public class PoppTokenGenerationRecordService {

  private final PoppMessageRecordRepository messageRepository;
  private final PoppTokenGenerationRecordRepository poppRecordRepository;
  private final PoppRecordCreationLock creationLock;

  public PoppTokenGenerationRecordService(
      final PoppMessageRecordRepository messageRepository,
      final PoppTokenGenerationRecordRepository poppRecordRepository,
      final PoppRecordCreationLock creationLock) {
    this.messageRepository = messageRepository;
    this.poppRecordRepository = poppRecordRepository;
    this.creationLock = creationLock;
  }

  /**
   * Returns an existing pending message for the patient and actor, or creates and persists a new
   * message together with its associated PoPP data.
   *
   * @param patientId the insured person's identifier
   * @param insurerId the health insurer identifier
   * @param actorId the actor's Telematik ID
   * @param workplaceId the workplace identifier
   * @param clientId the identifier of the requesting client
   * @return the existing or newly persisted pending message
   */
  @Transactional
  public PoppMessageRecord create(
      final String patientId,
      final String insurerId,
      final String actorId,
      final String workplaceId,
      final String clientId) {
    creationLock.acquire(patientId, actorId);
    final var pendingMessage =
        poppRecordRepository.findMessage(patientId, actorId, PoppTokenGenerationStatus.PENDING);
    if (pendingMessage.isPresent()) {
      return pendingMessage.get();
    }

    final var messageId = UUID.randomUUID();
    final var createdAt = Instant.now();
    final var message = new PoppMessageRecord(messageId, clientId, createdAt);
    final var persistedMessage = messageRepository.save(message);
    final var poppRecord =
        new PoppTokenGenerationRecord(
            persistedMessage.getId(), patientId, insurerId, actorId, workplaceId, createdAt);
    poppRecordRepository.save(poppRecord);
    return persistedMessage;
  }

  /**
   * Marks the token as delivered to the primary system.
   *
   * <p>If the push notification has already been sent, the associated PoPP record is deleted. The
   * message record is retained as a journal entry.
   *
   * @param messageId the shared message and PoPP record identifier
   * @return the updated message record
   * @throws PoppTokenGenerationRecordNotFoundException if no message exists for the identifier
   */
  @Transactional
  public PoppMessageRecord markTokenDelivered(final UUID messageId) {
    final var message =
        messageRepository
            .findById(messageId)
            .orElseThrow(() -> new PoppTokenGenerationRecordNotFoundException(messageId));
    if (message.getStatus() != PoppTokenGenerationStatus.PENDING) {
      return message;
    }

    message.setStatus(PoppTokenGenerationStatus.SUCCESS);
    message.setTokenDeliveredAt(Instant.now());
    final var savedMessage = messageRepository.save(message);
    deletePoppRecordIfCompleted(savedMessage);
    return savedMessage;
  }

  /**
   * Marks the push notification as sent.
   *
   * <p>If the token has already been delivered, the associated PoPP record is deleted. The message
   * record is retained as a journal entry.
   *
   * @param messageId the shared message and PoPP record identifier
   * @return the updated message record
   * @throws PoppTokenGenerationRecordNotFoundException if no message exists for the identifier
   */
  @Transactional
  public PoppMessageRecord markPushSent(final UUID messageId) {
    final var message =
        messageRepository
            .findById(messageId)
            .orElseThrow(() -> new PoppTokenGenerationRecordNotFoundException(messageId));
    if (message.getStatus() == PoppTokenGenerationStatus.CANCELED
        || message.getPushSentAt() != null) {
      return message;
    }

    message.setPushSentAt(Instant.now());
    final var savedMessage = messageRepository.save(message);
    deletePoppRecordIfCompleted(savedMessage);
    return savedMessage;
  }

  /**
   * Expires records whose 72-hour retention period has elapsed.
   *
   * <p>The message records are retained with status {@code canceled}; only their associated PoPP
   * records are deleted.
   */
  @Transactional
  public void expireRecords() {
    final var expiredMessages = poppRecordRepository.findExpiredMessages(Instant.now());
    expiredMessages.forEach(message -> message.setStatus(PoppTokenGenerationStatus.CANCELED));
    messageRepository.saveAllAndFlush(expiredMessages);
    poppRecordRepository.deleteAllById(
        expiredMessages.stream().map(PoppMessageRecord::getId).toList());
  }

  private void deletePoppRecordIfCompleted(final PoppMessageRecord message) {
    if (message.getTokenDeliveredAt() != null && message.getPushSentAt() != null) {
      poppRecordRepository.deleteById(message.getId());
    }
  }
}
