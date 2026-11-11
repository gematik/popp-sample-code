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

package de.gematik.refpopp.popp_server.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.refpopp.popp_server.model.PoppMessageRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import de.gematik.refpopp.popp_server.repository.PoppMessageRecordRepository;
import de.gematik.refpopp.popp_server.repository.PoppTokenGenerationRecordRepository;
import de.gematik.refpopp.popp_server.scenario.BaseIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class TokenDeliverySeedIT extends BaseIntegrationTest {

  private static final List<UUID> SEED_IDS =
      List.of(
          UUID.fromString("ad969c8e-c84f-4e3f-bcc9-41766bc1b174"),
          UUID.fromString("bd969c8e-c84f-4e3f-bcc9-41766bc1b174"),
          UUID.fromString("cd969c8e-c84f-4e3f-bcc9-41766bc1b174"));

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private MockMvc mockMvc;

  @Autowired private PoppMessageRecordRepository messageRepository;
  @Autowired private PoppTokenGenerationRecordRepository tokenRepository;

  /**
   * Checks the three Liquibase seed records independently of unrelated records, and delivers the
   * first pending token with a 72-hour validity. Test changes are rolled back after each
   * invocation.
   */
  @ParameterizedTest
  @ValueSource(ints = {0, 18})
  void seededPendingRecordsCanBeDelivered(int unrelatedRecordCount) throws Exception {
    for (var index = 0; index < unrelatedRecordCount; index++) {
      final var id = UUID.randomUUID();
      final var now = Instant.now();
      messageRepository.saveAndFlush(new PoppMessageRecord(id, "unrelated-client", now));
      tokenRepository.saveAndFlush(
          new PoppTokenGenerationRecord(
              id, "A123456789", "123456789", "unrelated-actor", "unrelated-workplace", now));
    }

    assertThat(tokenRepository.findAllById(SEED_IDS))
        .extracting(PoppTokenGenerationRecord::getMessageId)
        .containsExactlyInAnyOrderElementsOf(SEED_IDS);
    final var firstId = SEED_IDS.getFirst();
    final var firstMessage = messageRepository.findById(firstId).orElseThrow();
    assertThat(firstMessage.getStatus()).isEqualTo(PoppTokenGenerationStatus.PENDING);
    assertThat(firstMessage.getExpiresAt())
        .isEqualTo(firstMessage.getCreatedAt().plus(Duration.ofHours(72)))
        .isAfter(Instant.now());

    mockMvc
        .perform(
            post("/popp/practitioner/api/v1/token-deliveries")
                .header("x-useragent", "CLIENT-1/2.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"telematikId":"1-SMC-B-Testkarte--88311000016875","limit":1}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.popptokens[0].poppStatusRecordId").value(firstId.toString()))
        .andExpect(jsonPath("$.popptokens[0].workplaceId").value("Arbeitsplatz 1"))
        .andExpect(jsonPath("$.hasmore").value(true));
  }
}
