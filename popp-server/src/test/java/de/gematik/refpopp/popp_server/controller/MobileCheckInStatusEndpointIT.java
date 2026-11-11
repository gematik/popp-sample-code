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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.refpopp.popp_server.model.PoppMessageRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import de.gematik.refpopp.popp_server.repository.PoppMessageRecordRepository;
import de.gematik.refpopp.popp_server.repository.PoppTokenGenerationRecordRepository;
import de.gematik.refpopp.popp_server.scenario.BaseIntegrationTest;
import de.gematik.refpopp.popp_server.tokengeneration.PoppTokenGenerationRecordService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = "popp.token-generation.expiration.cron=-")
class MobileCheckInStatusEndpointIT extends BaseIntegrationTest {

  private static final String ENDPOINT = "/popp/patient/api/v1/mobile/checkin/poppStatusRecordId";

  @Autowired private MockMvc mockMvc;
  @Autowired private PoppMessageRecordRepository messageRepository;
  @Autowired private PoppTokenGenerationRecordRepository recordRepository;
  @Autowired private PoppTokenGenerationRecordService recordService;

  private final List<UUID> createdIds = new ArrayList<>();

  @AfterEach
  void removeTestRecords() {
    recordRepository.deleteAllById(createdIds);
    messageRepository.deleteAllById(createdIds);
  }

  @ParameterizedTest
  @CsvSource({"PENDING,pending", "SUCCESS,success", "CANCELED,canceled"})
  void readsStoredStateWithoutPoppDataOrChangingTheMessage(
      PoppTokenGenerationStatus state, String expectedState) throws Exception {
    final var id = persistMessage(state, Instant.now().minusSeconds(60));
    final var before = messageRepository.findById(id).orElseThrow();
    assertThat(recordRepository.findById(id)).isEmpty();

    assertStatus(id, expectedState);
    assertStatus(id, expectedState);

    assertThat(messageRepository.findById(id)).contains(before);
    assertThat(recordRepository.findById(id)).isEmpty();
  }

  @Test
  void reportsDeliveryAndRetainsSuccessAfterPoppDataIsDeleted() throws Exception {
    final var id = persistLinkedRecord(Instant.now().minusSeconds(60));
    final var pendingMessage = messageRepository.findById(id).orElseThrow();
    final var pendingRecord = recordRepository.findById(id).orElseThrow();

    assertStatus(id, "pending");

    assertThat(messageRepository.findById(id)).contains(pendingMessage);
    assertThat(recordRepository.findById(id)).contains(pendingRecord);

    mockMvc
        .perform(
            post("/popp/practitioner/api/v1/token-deliveries")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-useragent", "CLIENT-1/2.1")
                .content(
                    """
                    {"telematikId":"actor-%s","limit":1}
                    """
                        .formatted(id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.popptokens[0].poppStatusRecordId").value(id.toString()))
        .andExpect(jsonPath("$.popptokens[0].token").isNotEmpty());

    final var deliveredMessage = messageRepository.findById(id).orElseThrow();
    assertThat(deliveredMessage.getStatus()).isEqualTo(PoppTokenGenerationStatus.SUCCESS);
    assertThat(deliveredMessage.getTokenDeliveredAt()).isNotNull();
    assertThat(deliveredMessage.getPushSentAt()).isNull();

    assertStatus(id, "success");

    assertThat(messageRepository.findById(id)).contains(deliveredMessage);
    assertThat(recordRepository.findById(id)).contains(pendingRecord);

    recordService.markPushSent(id);
    final var completedMessage = messageRepository.findById(id).orElseThrow();
    assertThat(completedMessage.getPushSentAt()).isNotNull();
    assertThat(recordRepository.findById(id)).isEmpty();

    assertStatus(id, "success");
    assertStatus(id, "success");

    assertThat(messageRepository.findById(id)).contains(completedMessage);
    assertThat(recordRepository.findById(id)).isEmpty();
  }

  @Test
  void reportsCancellationAfterExpirationWithoutChangingStatusDuringReads() throws Exception {
    final var id = persistLinkedRecord(Instant.now().minus(Duration.ofHours(73)));
    final var pendingMessage = messageRepository.findById(id).orElseThrow();
    final var pendingRecord = recordRepository.findById(id).orElseThrow();

    assertStatus(id, "pending");

    assertThat(messageRepository.findById(id)).contains(pendingMessage);
    assertThat(recordRepository.findById(id)).contains(pendingRecord);

    recordService.expireRecords();
    final var canceledMessage = messageRepository.findById(id).orElseThrow();
    assertThat(canceledMessage.getStatus()).isEqualTo(PoppTokenGenerationStatus.CANCELED);
    assertThat(recordRepository.findById(id)).isEmpty();

    assertStatus(id, "canceled");
    assertStatus(id, "canceled");

    assertThat(messageRepository.findById(id)).contains(canceledMessage);
    assertThat(recordRepository.findById(id)).isEmpty();
  }

  @Test
  void returnsNotFoundForUnknownUuidWithoutCreatingRecords() throws Exception {
    final var id = UUID.randomUUID();
    assertThat(messageRepository.findById(id)).isEmpty();

    mockMvc
        .perform(get(ENDPOINT).param("statusrequest", id.toString()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value("noResource"));

    assertThat(messageRepository.findById(id)).isEmpty();
    assertThat(recordRepository.findById(id)).isEmpty();
  }

  private void assertStatus(UUID id, String expectedState) throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("statusrequest", id.toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.state").value(expectedState))
        .andExpect(jsonPath("$.poppStatusRecordId").value(id.toString()));
  }

  private UUID persistMessage(PoppTokenGenerationStatus state, Instant createdAt) {
    final var id = UUID.randomUUID();
    createdIds.add(id);
    final var message = new PoppMessageRecord(id, "client-id", createdAt);
    message.setStatus(state);
    if (state == PoppTokenGenerationStatus.SUCCESS) {
      message.setTokenDeliveredAt(createdAt.plusSeconds(30));
    }
    messageRepository.saveAndFlush(message);
    return id;
  }

  private UUID persistLinkedRecord(Instant createdAt) {
    final var id = persistMessage(PoppTokenGenerationStatus.PENDING, createdAt);
    recordRepository.saveAndFlush(
        new PoppTokenGenerationRecord(
            id, "A123456789", "123456789", "actor-" + id, "workplace-id", createdAt));
    return id;
  }
}
