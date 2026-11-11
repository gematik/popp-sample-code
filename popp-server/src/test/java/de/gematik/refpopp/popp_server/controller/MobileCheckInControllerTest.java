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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.refpopp.popp_server.model.PoppMessageRecord;
import de.gematik.refpopp.popp_server.tokengeneration.MobileCheckInRequestValidator;
import de.gematik.refpopp.popp_server.tokengeneration.PoppRecordCreationResult;
import de.gematik.refpopp.popp_server.tokengeneration.PoppTokenGenerationRecordService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(MobileCheckInController.class)
@Import(MobileCheckInRequestValidator.class)
class MobileCheckInControllerTest {

  private static final String ENDPOINT = "/popp/patient/api/v1/mobile/checkin";
  private static final String BODY =
      "{\"telematikId\":\"1-SMC-B-Testkarte--883110000168757\",\"workplaceId\":\"Arbeitsplatz 1\"}";
  private static final String ACTOR = "1-SMC-B-Testkarte--883110000168757";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PoppTokenGenerationRecordService recordService;

  @Test
  void createsPendingRecordAndReturns201() throws Exception {
    final var message = message();
    when(recordService.create("L123456789", "123456789", ACTOR, "Arbeitsplatz 1", "CLIENT-1"))
        .thenReturn(new PoppRecordCreationResult(message, true));

    mockMvc
        .perform(request().content(BODY))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.state").value("pending"))
        .andExpect(jsonPath("$.poppStatusRecordId").value(message.getId().toString()));

    verify(recordService).create("L123456789", "123456789", ACTOR, "Arbeitsplatz 1", "CLIENT-1");
  }

  @Test
  void reusesPendingRecordAndReturns200() throws Exception {
    final var message = message();
    when(recordService.create("L123456789", "123456789", ACTOR, null, "CLIENT-1"))
        .thenReturn(new PoppRecordCreationResult(message, false));

    mockMvc
        .perform(request().content("{\"telematikId\":\"" + ACTOR + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("pending"))
        .andExpect(jsonPath("$.poppStatusRecordId").value(message.getId().toString()));

    verify(recordService).create("L123456789", "123456789", ACTOR, null, "CLIENT-1");
  }

  @Test
  void rejectsInvalidHeadersAndRequestFields() throws Exception {
    for (final var invalidHeader : new String[] {"invalid", "CLIENT-1/"}) {
      mockMvc
          .perform(request(invalidHeader, "L123456789", "123456789").content(BODY))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("malformedRequest"));
    }
    mockMvc
        .perform(request("CLIENT-1/2.1", "invalid", "123456789").content(BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("malformedRequest"));
    mockMvc
        .perform(request("CLIENT-1/2.1", "L123456789", "invalid").content(BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("malformedRequest"));
    for (final var body :
        new String[] {
          "{}",
          "{\"telematikId\":\"  \"}",
          "{\"telematikId\":\"actor\",\"workplaceId\":\"bad/name\"}"
        }) {
      mockMvc
          .perform(request().content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("malformedRequest"));
    }
    verifyNoInteractions(recordService);
  }

  @Test
  void rejectsNonAsciiDigitsInIdentityHeaders() throws Exception {
    mockMvc
        .perform(request("CLIENT-1/2.1", "L12345678\u0661", "123456789").content(BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("malformedRequest"));
    mockMvc
        .perform(request("CLIENT-1/2.1", "L123456789", "12345678\u0661").content(BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("malformedRequest"));
    verifyNoInteractions(recordService);
  }

  @Test
  void rejectsMissingHeadersAndMalformedBody() throws Exception {
    for (final var missingHeader : new String[] {"x-useragent", "x-kvnr", "x-iknr"}) {
      final var request = post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(BODY);
      if (!"x-useragent".equals(missingHeader)) {
        request.header("x-useragent", "CLIENT-1/2.1");
      }
      if (!"x-kvnr".equals(missingHeader)) {
        request.header("x-kvnr", "L123456789");
      }
      if (!"x-iknr".equals(missingHeader)) {
        request.header("x-iknr", "123456789");
      }
      mockMvc
          .perform(request)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("malformedRequest"));
    }
    mockMvc
        .perform(request().content("{invalid json}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("malformedRequest"));
    mockMvc
        .perform(request())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("malformedRequest"));
    verifyNoInteractions(recordService);
  }

  @Test
  void mapsPersistenceFailureWithoutExposingDetails() throws Exception {
    when(recordService.create("L123456789", "123456789", ACTOR, "Arbeitsplatz 1", "CLIENT-1"))
        .thenThrow(new IllegalStateException("database connection failed"));

    mockMvc
        .perform(request().content(BODY))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.errorCode").value("internalError"))
        .andExpect(jsonPath("$.errorDetail").value("Mobile check-in failed"));
  }

  private MockHttpServletRequestBuilder request() {
    return request("CLIENT-1/2.1", "L123456789", "123456789");
  }

  private MockHttpServletRequestBuilder request(
      final String userAgent, final String kvnr, final String iknr) {
    return post(ENDPOINT)
        .contentType(MediaType.APPLICATION_JSON)
        .header("x-useragent", userAgent)
        .header("x-kvnr", kvnr)
        .header("x-iknr", iknr);
  }

  private PoppMessageRecord message() {
    return new PoppMessageRecord(UUID.randomUUID(), "CLIENT-1", Instant.now());
  }
}
