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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.refpopp.popp_server.controller.dto.TokenDeliveryItem;
import de.gematik.refpopp.popp_server.controller.dto.TokenDeliveryResponse;
import de.gematik.refpopp.popp_server.tokengeneration.TokenDeliveryNotFoundException;
import de.gematik.refpopp.popp_server.tokengeneration.TokenDeliveryRequestValidator;
import de.gematik.refpopp.popp_server.tokengeneration.TokenDeliveryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TokenDeliveryController.class)
@Import(TokenDeliveryRequestValidator.class)
class TokenDeliveryControllerTest {

  private static final String ENDPOINT = "/popp/practitioner/api/v1/token-deliveries";
  private static final String REQUEST =
      "{\"telematikId\":\"actor-1\",\"workplaceIds\":[\"workplace-a\"],\"limit\":1}";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private TokenDeliveryService deliveryService;

  @Test
  void returnsDeliveredTokensAndHasMore() throws Exception {
    final var id = UUID.randomUUID();
    when(deliveryService.deliver("actor-1", List.of("workplace-a"), 1))
        .thenReturn(
            new TokenDeliveryResponse(
                List.of(new TokenDeliveryItem("signed.jwt.token", "workplace-a", id)), true));

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-useragent", "CLIENT-1/2.1")
                .content(REQUEST))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.popptokens[0].token").value("signed.jwt.token"))
        .andExpect(jsonPath("$.popptokens[0].workplaceId").value("workplace-a"))
        .andExpect(jsonPath("$.popptokens[0].poppStatusRecordId").value(id.toString()))
        .andExpect(jsonPath("$.hasmore").value(true));
  }

  @Test
  void returnsEmptyArrayWhenKnownRecordsHaveNoPendingTokens() throws Exception {
    when(deliveryService.deliver("actor-1", List.of(), 100))
        .thenReturn(new TokenDeliveryResponse(List.of(), false));

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-useragent", "CLIENT-1/2.1")
                .content("{\"telematikId\":\"actor-1\",\"workplaceIds\":[],\"limit\":100}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.popptokens").isEmpty())
        .andExpect(jsonPath("$.hasmore").value(false));
  }

  @Test
  void mapsUnknownInstitutionToNotFound() throws Exception {
    when(deliveryService.deliver("actor-1", List.of("workplace-a"), 1))
        .thenThrow(new TokenDeliveryNotFoundException());

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-useragent", "CLIENT-1/2.1")
                .content(REQUEST))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("noResource"));
  }

  @Test
  void mapsInternalFailureWithoutExposingExceptionMessage() throws Exception {
    when(deliveryService.deliver("actor-1", List.of("workplace-a"), 1))
        .thenThrow(new IllegalStateException("signing key is unavailable"));

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-useragent", "CLIENT-1/2.1")
                .content(REQUEST))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.errorCode").value("internalError"))
        .andExpect(jsonPath("$.errorDetail").value("PoPP token delivery failed"));
  }

  @Test
  void rejectsInvalidHeaderAndRequestFields() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-useragent", "invalid")
                .content(REQUEST))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("malformedRequest"));

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-useragent", "CLIENT-1/2.1")
                .content(
                    "{\"telematikId\":\"actor-1\",\"workplaceIds\":[\"bad/id\"],\"limit\":101}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorDetail").value(org.hamcrest.Matchers.containsString("limit")))
        .andExpect(
            jsonPath("$.errorDetail").value(org.hamcrest.Matchers.containsString("workplaceIds")));
    verifyNoInteractions(deliveryService);
  }

  @Test
  void mapsMalformedOrMissingJsonAndHeaderToBadRequest() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("malformedRequest"));

    mockMvc
        .perform(
            post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).header("x-useragent", "A/1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("malformedRequest"));

    for (final var body :
        List.of(
            "{\"telematikId\":\"actor-1\",\"limit\":1.5}",
            "{\"telematikId\":\"actor-1\",\"limit\":\"1\"}",
            "{\"telematikId\":\"actor-1\",\"limit\":null}",
            "{invalid json}")) {
      mockMvc
          .perform(
              post(ENDPOINT)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("x-useragent", "A/1")
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("malformedRequest"));
    }
    verifyNoInteractions(deliveryService);
  }
}
