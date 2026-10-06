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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PoppMessageRecordTest {

  @Test
  void constructorCreatesPendingMessageWithSeventyTwoHourRetentionPeriod() {
    final var id = UUID.randomUUID();
    final var createdAt = Instant.parse("2026-09-21T12:00:00Z");

    final var message = new PoppMessageRecord(id, "client-id", createdAt);

    assertThat(message.getId()).isEqualTo(id);
    assertThat(message.getClientId()).isEqualTo("client-id");
    assertThat(message.getStatus()).isEqualTo(PoppTokenGenerationStatus.PENDING);
    assertThat(message.getCreatedAt()).isEqualTo(createdAt);
    assertThat(message.getExpiresAt()).isEqualTo(createdAt.plus(Duration.ofHours(72)));
    assertThat(message.getTokenDeliveredAt()).isNull();
    assertThat(message.getPushSentAt()).isNull();
  }
}
