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
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "popp_message_records")
public class PoppMessageRecord {

  private static final Duration RETENTION_PERIOD = Duration.ofHours(72);

  @Id private UUID id;

  @Column(name = "status", length = 8, nullable = false)
  @Convert(converter = PoppTokenGenerationStatusConverter.class)
  private PoppTokenGenerationStatus status = PoppTokenGenerationStatus.PENDING;

  @Column(name = "client_id", nullable = false)
  private String clientId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "token_delivered_at")
  private Instant tokenDeliveredAt;

  @Column(name = "push_sent_at")
  private Instant pushSentAt;

  public PoppMessageRecord() {}

  public PoppMessageRecord(final UUID id, final String clientId, final Instant createdAt) {
    this.id = id;
    this.clientId = clientId;
    this.createdAt = createdAt;
    this.expiresAt = createdAt.plus(RETENTION_PERIOD);
  }
}
