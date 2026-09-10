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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.controller.dto.FhirVzdSearchResponse;
import de.gematik.refpopp.popp_server.controller.dto.FhirVzdSearchResultItem;
import de.gematik.refpopp.popp_server.vzd.VzdSearchService;
import de.gematik.refpopp.popp_server.vzd.dto.VzdAddress;
import de.gematik.refpopp.popp_server.vzd.dto.VzdEntry;
import de.gematik.refpopp.popp_server.vzd.dto.VzdSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class MobileVzdSearchControllerTest {

  private static final String SEARCH_REQUEST = "https://example.org/fhir/Bundle?_count=1";

  @Mock private VzdSearchService vzdSearchService;

  @InjectMocks private MobileVzdSearchController controller;

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
  void loadFhirVzdInformation_shouldReturnOkWithMappedResponse() {
    // given
    var result = new VzdSearchResult(1, List.of(entry()), null);
    when(vzdSearchService.searchByPageUrl(SEARCH_REQUEST)).thenReturn(result);

    // when
    ResponseEntity<FhirVzdSearchResponse> response =
        controller.loadFhirVzdInformation(SEARCH_REQUEST);

    // then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    var body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.practitioners()).hasSize(1);
    var item = body.practitioners().getFirst();
    assertThat(item.name()).isEqualTo("Apotheke am Hauptbahnhof");
    assertThat(item.telematikId()).isEqualTo("1-SMC-B-Testkarte--883110000168757");
    assertThat(item.iknr()).isEqualTo("123456789");
    assertThat(item.address()).isEqualTo("Hauptbahnhofstraße 1, 12345 Musterstadt");
    assertThat(item.contact()).isEqualTo("Telefon: 01234 567890");
    verify(vzdSearchService).searchByPageUrl(SEARCH_REQUEST);
  }

  @Test
  void loadFhirVzdInformation_shouldMapAllEntriesWhenSearchHasMultipleResults() {
    // given
    var secondEntry = new VzdEntry("tid-2", "987654321", "Zweite Apotheke", List.of(), null, null);
    var result = new VzdSearchResult(2, List.of(entry(), secondEntry), null);
    when(vzdSearchService.searchByPageUrl(SEARCH_REQUEST)).thenReturn(result);

    // when
    ResponseEntity<FhirVzdSearchResponse> response =
        controller.loadFhirVzdInformation(SEARCH_REQUEST);

    // then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().practitioners())
        .extracting(FhirVzdSearchResultItem::name)
        .containsExactly("Apotheke am Hauptbahnhof", "Zweite Apotheke");
  }

  @Test
  void loadFhirVzdInformation_shouldMapNullEntryToNullItem() {
    // given
    when(vzdSearchService.searchByPageUrl(SEARCH_REQUEST))
        .thenReturn(new VzdSearchResult(1, java.util.Collections.singletonList(null), null));

    // when
    ResponseEntity<FhirVzdSearchResponse> response =
        controller.loadFhirVzdInformation(SEARCH_REQUEST);

    // then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().practitioners()).hasSize(1);
    assertThat(response.getBody().practitioners().getFirst()).isNull();
  }

  @Test
  void loadFhirVzdInformation_shouldReturnNotFoundWhenResultIsNull() {
    // given
    when(vzdSearchService.searchByPageUrl(SEARCH_REQUEST)).thenReturn(null);

    // when
    ResponseEntity<FhirVzdSearchResponse> response =
        controller.loadFhirVzdInformation(SEARCH_REQUEST);

    // then
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertNull(response.getBody());
  }

  @Test
  void loadFhirVzdInformation_shouldReturnNotFoundWhenResultHasNoEntries() {
    // given
    var result = new VzdSearchResult(0, List.of(), null);
    when(vzdSearchService.searchByPageUrl(SEARCH_REQUEST)).thenReturn(result);

    // when
    ResponseEntity<FhirVzdSearchResponse> response =
        controller.loadFhirVzdInformation(SEARCH_REQUEST);

    // then
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertNull(response.getBody());
  }

  @Test
  void loadFhirVzdInformation_shouldPropagateExceptionFromService() {
    // given
    when(vzdSearchService.searchByPageUrl(""))
        .thenThrow(new IllegalArgumentException("URL must not be blank"));

    // when / then
    var ex =
        assertThrows(IllegalArgumentException.class, () -> controller.loadFhirVzdInformation(""));
    assertThat(ex.getMessage()).contains("URL must not be blank");
    verify(vzdSearchService).searchByPageUrl("");
  }

  @Test
  void loadFhirVzdInformation_shouldDelegateExactSearchRequestToService() {
    // given
    var result = new VzdSearchResult(1, List.of(entry()), null);
    when(vzdSearchService.searchByPageUrl(SEARCH_REQUEST)).thenReturn(result);

    // when
    controller.loadFhirVzdInformation(SEARCH_REQUEST);

    // then
    verify(vzdSearchService).searchByPageUrl(SEARCH_REQUEST);
  }
}
