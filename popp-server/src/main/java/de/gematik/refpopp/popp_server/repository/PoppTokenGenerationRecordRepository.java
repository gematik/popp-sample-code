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

import de.gematik.refpopp.popp_server.model.PoppMessageRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PoppTokenGenerationRecordRepository
    extends JpaRepository<PoppTokenGenerationRecord, UUID> {

  boolean existsByActorId(String actorId);

  boolean existsByActorIdAndWorkplaceIdIn(String actorId, List<String> workplaceIds);

  @Query(
      """
      SELECT message
      FROM PoppTokenGenerationRecord poppRecord, PoppMessageRecord message
      WHERE poppRecord.messageId = message.id
        AND poppRecord.patientId = :patientId
        AND poppRecord.actorId = :actorId
        AND message.status = :status
      """)
  Optional<PoppMessageRecord> findMessage(
      @Param("patientId") String patientId,
      @Param("actorId") String actorId,
      @Param("status") PoppTokenGenerationStatus status);

  @Query(
      """
      SELECT message.id
      FROM PoppTokenGenerationRecord poppRecord, PoppMessageRecord message
      WHERE poppRecord.messageId = message.id
        AND poppRecord.actorId = :actorId
        AND message.status = :status
        AND message.expiresAt > :now
      """)
  List<UUID> findPendingMessageIdsForActor(
      @Param("actorId") String actorId,
      @Param("status") PoppTokenGenerationStatus status,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(
      """
      SELECT message.id
      FROM PoppTokenGenerationRecord poppRecord, PoppMessageRecord message
      WHERE poppRecord.messageId = message.id
        AND poppRecord.actorId = :actorId
        AND poppRecord.workplaceId IN :workplaceIds
        AND message.status = :status
        AND message.expiresAt > :now
      """)
  List<UUID> findPendingMessageIdsForActorAndWorkplaces(
      @Param("actorId") String actorId,
      @Param("workplaceIds") List<String> workplaceIds,
      @Param("status") PoppTokenGenerationStatus status,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(
      value =
          """
          SELECT record.*
          FROM popp_token_generation_records record
          JOIN popp_message_records message ON message.id = record.message_id
          WHERE record.actor_id = :actorId
            AND message.status = 'pending'
            AND message.expires_at > :now
          ORDER BY message.created_at, message.id
          LIMIT :limit
          FOR UPDATE OF message SKIP LOCKED
          """,
      nativeQuery = true)
  List<PoppTokenGenerationRecord> lockPendingForDelivery(
      @Param("actorId") String actorId, @Param("now") Instant now, @Param("limit") int limit);

  @Query(
      value =
          """
          SELECT record.*
          FROM popp_token_generation_records record
          JOIN popp_message_records message ON message.id = record.message_id
          WHERE record.actor_id = :actorId
            AND record.workplace_id IN (:workplaceIds)
            AND message.status = 'pending'
            AND message.expires_at > :now
          ORDER BY message.created_at, message.id
          LIMIT :limit
          FOR UPDATE OF message SKIP LOCKED
          """,
      nativeQuery = true)
  List<PoppTokenGenerationRecord> lockPendingForDeliveryAtWorkplaces(
      @Param("actorId") String actorId,
      @Param("workplaceIds") List<String> workplaceIds,
      @Param("now") Instant now,
      @Param("limit") int limit);

  @Query(
      """
      SELECT message
      FROM PoppTokenGenerationRecord poppRecord, PoppMessageRecord message
      WHERE poppRecord.messageId = message.id
        AND message.expiresAt <= :timestamp
      """)
  List<PoppMessageRecord> findExpiredMessages(@Param("timestamp") Instant timestamp);
}
