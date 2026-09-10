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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.refpopp.popp_server.vzd.VzdSearchException;
import de.gematik.refpopp.popp_server.vzd.VzdSearchService;
import de.gematik.refpopp.popp_server.vzd.dto.VzdAddress;
import de.gematik.refpopp.popp_server.vzd.dto.VzdEntry;
import de.gematik.refpopp.popp_server.vzd.dto.VzdSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MobileVzdSearchController.class)
class MobileVzdSearchControllerIntegrationTest {

  private static final String ENDPOINT = "/popp/patient/api/v1/mobile/fhirvzdsearch";
  private static final String SEARCH_REQUEST = "https://example.org/fhir/Bundle?_count=1";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private VzdSearchService vzdSearchService;

  private static VzdEntry entry() {
    return new VzdEntry(
        "1-SMC-B-Testkarte--883110000168757",
        "123456789",
        "Apotheke am Hauptbahnhof",
        List.of("01234 567890"),
        new VzdAddress("Hauptbahnhofstraße 1", "12345", "Musterstadt"),
        null);
  }

  @Test
  void fhirVzdSearchReturnsOkWithMappedJsonBody() throws Exception {
    // given
    var result = new VzdSearchResult(1, List.of(entry()), null);
    given(vzdSearchService.searchByPageUrl(SEARCH_REQUEST)).willReturn(result);

    // when / then
    mockMvc
        .perform(
            get(ENDPOINT).param("searchrequest", SEARCH_REQUEST).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.practitioners").isArray())
        .andExpect(jsonPath("$.practitioners", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$.practitioners[0].name").value("Apotheke am Hauptbahnhof"))
        .andExpect(
            jsonPath("$.practitioners[0].telematikId").value("1-SMC-B-Testkarte--883110000168757"))
        .andExpect(jsonPath("$.practitioners[0].iknr").value("123456789"))
        .andExpect(
            jsonPath("$.practitioners[0].address").value("Hauptbahnhofstraße 1, 12345 Musterstadt"))
        .andExpect(jsonPath("$.practitioners[0].contact").value("Telefon: 01234 567890"));

    verify(vzdSearchService).searchByPageUrl(SEARCH_REQUEST);
  }

  @Test
  void fhirVzdSearchReturnsNotFoundWhenServiceYieldsNoResult() throws Exception {
    // given
    given(vzdSearchService.searchByPageUrl(anyString())).willReturn(null);

    // when / then
    mockMvc
        .perform(
            get(ENDPOINT).param("searchrequest", SEARCH_REQUEST).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  void fhirVzdSearchReturnsNotFoundWhenResultHasNoEntries() throws Exception {
    // given
    given(vzdSearchService.searchByPageUrl(anyString()))
        .willReturn(new VzdSearchResult(0, List.of(), null));

    // when / then
    mockMvc
        .perform(
            get(ENDPOINT).param("searchrequest", SEARCH_REQUEST).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  void fhirVzdSearchReturnsBadRequestWhenServiceThrowsIllegalArgument() throws Exception {
    // given
    given(vzdSearchService.searchByPageUrl(anyString()))
        .willThrow(new IllegalArgumentException("URL must not be blank"));

    // when / then
    mockMvc
        .perform(get(ENDPOINT).param("searchrequest", " ").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
        .andExpect(jsonPath("$.errorDetail").value("URL must not be blank"));
  }

  @Test
  void fhirVzdSearchReturnsInternalServerErrorWhenServiceThrowsVzdSearchException()
      throws Exception {
    // given
    given(vzdSearchService.searchByPageUrl(anyString()))
        .willThrow(new VzdSearchException("VZD search by page URL failed for " + SEARCH_REQUEST));

    // when / then
    mockMvc
        .perform(
            get(ENDPOINT).param("searchrequest", SEARCH_REQUEST).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value("VZD_SEARCH_FAILED"));
  }

  @Test
  void fhirVzdSearchWithMissingSearchRequestParameterIsMappedByGenericHandlerTo500()
      throws Exception {
    // A missing required request parameter raises a MissingServletRequestParameterException while
    // the handler method is already resolved, so the controller-scoped generic exception handler
    // maps it to 500 / INTERNAL_ERROR.
    mockMvc
        .perform(get(ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
  }

  @Test
  void fhirVzdSearchPostRequestReturnsMethodNotAllowed() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("searchrequest", SEARCH_REQUEST))
        .andExpect(status().isMethodNotAllowed());
  }
}
