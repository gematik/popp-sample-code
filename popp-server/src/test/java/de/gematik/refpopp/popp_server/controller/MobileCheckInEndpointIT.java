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

import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import de.gematik.refpopp.popp_server.repository.PoppMessageRecordRepository;
import de.gematik.refpopp.popp_server.repository.PoppTokenGenerationRecordRepository;
import de.gematik.refpopp.popp_server.scenario.BaseIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class MobileCheckInEndpointIT extends BaseIntegrationTest {

  private static final String ENDPOINT = "/popp/patient/api/v1/mobile/checkin";

  @Autowired private MockMvc mockMvc;
  @Autowired private PoppMessageRecordRepository messageRepository;
  @Autowired private PoppTokenGenerationRecordRepository recordRepository;

  private final ObjectMapper mapper = new ObjectMapper();
  private UUID messageId;

  @AfterEach
  void removeTestRecords() {
    if (messageId != null) {
      recordRepository.deleteById(messageId);
      messageRepository.deleteById(messageId);
    }
  }

  @Test
  void createsAndReusesPendingRecordThroughHttp() throws Exception {
    final var actor = "actor-" + UUID.randomUUID();
    final var first =
        mockMvc
            .perform(request(actor, "workplace-a", "CLIENT-1/2.1"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.state").value("pending"))
            .andReturn();
    messageId =
        UUID.fromString(
            mapper
                .readTree(first.getResponse().getContentAsString())
                .get("poppStatusRecordId")
                .asString());

    mockMvc
        .perform(request(actor, "workplace-b", "CLIENT-2/3.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("pending"))
        .andExpect(jsonPath("$.poppStatusRecordId").value(messageId.toString()));

    final var message = messageRepository.findById(messageId).orElseThrow();
    final var poppRecord = recordRepository.findById(messageId).orElseThrow();
    assertThat(message.getStatus()).isEqualTo(PoppTokenGenerationStatus.PENDING);
    assertThat(message.getClientId()).isEqualTo("CLIENT-1");
    assertThat(poppRecord.getPatientId()).isEqualTo("L123456789");
    assertThat(poppRecord.getInsurerId()).isEqualTo("123456789");
    assertThat(poppRecord.getActorId()).isEqualTo(actor);
    assertThat(poppRecord.getWorkplaceId()).isEqualTo("workplace-a");
  }

  private MockHttpServletRequestBuilder request(
      final String actor, final String workplace, final String userAgent) {
    return post(ENDPOINT)
        .contentType(MediaType.APPLICATION_JSON)
        .header("x-useragent", userAgent)
        .header("x-kvnr", "L123456789")
        .header("x-iknr", "123456789")
        .content(
            """
            {"telematikId":"%s","workplaceId":"%s"}
            """
                .formatted(actor, workplace));
  }
}
