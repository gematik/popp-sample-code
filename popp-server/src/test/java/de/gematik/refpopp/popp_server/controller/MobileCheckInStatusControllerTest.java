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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.refpopp.popp_server.model.PoppMessageRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import de.gematik.refpopp.popp_server.tokengeneration.PoppTokenGenerationRecordNotFoundException;
import de.gematik.refpopp.popp_server.tokengeneration.PoppTokenGenerationRecordService;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MobileCheckInStatusController.class)
class MobileCheckInStatusControllerTest {

  private static final String ENDPOINT = "/popp/patient/api/v1/mobile/checkin/poppStatusRecordId";
  private static final UUID ID = UUID.fromString("237cc7b7-f85e-42c3-981c-ad7f9d97ddca");

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PoppTokenGenerationRecordService recordService;

  @ParameterizedTest
  @CsvSource({"PENDING,pending", "SUCCESS,success", "CANCELED,canceled"})
  void returnsStoredStateAndRecordIdWithoutRequiringHeaders(
      PoppTokenGenerationStatus state, String expectedState) throws Exception {
    final var message = new PoppMessageRecord(ID, "client-id", Instant.now());
    message.setStatus(state);
    when(recordService.readStatus(ID)).thenReturn(message);

    mockMvc
        .perform(get(ENDPOINT).param("statusrequest", ID.toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    {"state":"%s","poppStatusRecordId":"237cc7b7-f85e-42c3-981c-ad7f9d97ddca"}
                    """
                        .formatted(expectedState)));

    verify(recordService).readStatus(ID);
    verifyNoMoreInteractions(recordService);
    assertThat(message.getStatus()).isEqualTo(state);
    assertThat(message.getTokenDeliveredAt()).isNull();
    assertThat(message.getPushSentAt()).isNull();
  }

  @Test
  void acceptsUppercaseUuid() throws Exception {
    when(recordService.readStatus(ID))
        .thenReturn(new PoppMessageRecord(ID, "client-id", Instant.now()));

    mockMvc
        .perform(get(ENDPOINT).param("statusrequest", ID.toString().toUpperCase(Locale.ROOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.poppStatusRecordId").value(ID.toString()));
  }

  @Test
  void rejectsMissingQueryParameter() throws Exception {
    mockMvc
        .perform(get(ENDPOINT))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value("malformedRequest"));

    verifyNoInteractions(recordService);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        " ",
        "not-a-uuid",
        "1-1-1-1-1",
        "237cc7b7f85e42c3981cad7f9d97ddca",
        "237cc7b7-f85e-42c3-981c-ad7f9d97ddcg"
      })
  void rejectsMalformedUuidWithoutReadingStatus(String value) throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("statusrequest", value))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value("malformedRequest"));

    verifyNoInteractions(recordService);
  }

  @Test
  void returnsNotFoundForUnknownUuid() throws Exception {
    when(recordService.readStatus(ID))
        .thenThrow(new PoppTokenGenerationRecordNotFoundException(ID));

    mockMvc
        .perform(get(ENDPOINT).param("statusrequest", ID.toString()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value("noResource"));
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void logsInternalFailureWithoutExposingItsDetails(CapturedOutput output) throws Exception {
    when(recordService.readStatus(ID)).thenThrow(new IllegalStateException("database unavailable"));

    mockMvc
        .perform(get(ENDPOINT).param("statusrequest", ID.toString()))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value("internalError"))
        .andExpect(jsonPath("$.errorDetail").value("Failed to read check-in status"));

    assertThat(output).contains("Failed to read PoPP check-in status", "database unavailable");
  }

  @Test
  void doesNotAcceptPostOnTheStatusPath() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("statusrequest", ID.toString()))
        .andExpect(status().isMethodNotAllowed());

    verifyNoInteractions(recordService);
  }
}
