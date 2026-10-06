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

package de.gematik.refpopp.popp_server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "popp_token_generation_records")
public class PoppTokenGenerationRecord {

  private static final String PROOF_METHOD = "healthid";

  @Id
  @Column(name = "message_id")
  private UUID messageId;

  @Column(name = "patient_id", length = 10, nullable = false)
  private String patientId;

  @Column(name = "insurer_id", length = 9, nullable = false)
  private String insurerId;

  @Column(name = "actor_id", nullable = false)
  private String actorId;

  @Column(name = "workplace_id", length = 64)
  private String workplaceId;

  @Column(name = "proof_method", length = 8, nullable = false)
  private String proofMethod = PROOF_METHOD;

  @Column(name = "timestamp", nullable = false)
  private Instant timestamp;

  public PoppTokenGenerationRecord() {}

  public PoppTokenGenerationRecord(
      final UUID messageId,
      final String patientId,
      final String insurerId,
      final String actorId,
      final String workplaceId,
      final Instant timestamp) {
    this.messageId = messageId;
    this.patientId = patientId;
    this.insurerId = insurerId;
    this.actorId = actorId;
    this.workplaceId = workplaceId;
    this.timestamp = timestamp;
  }
}
