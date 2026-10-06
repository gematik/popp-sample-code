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

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PoppTokenGenerationRecordTest {

  @Test
  void constructorCreatesPoppRecordLinkedByMessageId() {
    final var messageId = UUID.randomUUID();
    final var timestamp = Instant.parse("2026-09-21T12:00:00Z");
    final var generationRecord =
        new PoppTokenGenerationRecord(
            messageId, "A123456789", "123456789", "actor-id", "workplace-id", timestamp);

    assertThat(generationRecord.getMessageId()).isEqualTo(messageId);
    assertThat(generationRecord.getPatientId()).isEqualTo("A123456789");
    assertThat(generationRecord.getInsurerId()).isEqualTo("123456789");
    assertThat(generationRecord.getActorId()).isEqualTo("actor-id");
    assertThat(generationRecord.getWorkplaceId()).isEqualTo("workplace-id");
    assertThat(generationRecord.getProofMethod()).isEqualTo("healthid");
    assertThat(generationRecord.getTimestamp()).isEqualTo(timestamp);
  }
}
