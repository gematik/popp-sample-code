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

package de.gematik.refpopp.popp_server.vzd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.vzd.dto.VzdLocation;
import de.gematik.refpopp.popp_server.vzd.dto.VzdSearchResult;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirAddress;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirBundle;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirEntry;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirIdentifier;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirLink;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirPosition;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirReference;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirResource;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirTelecom;
import java.net.URI;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class VzdSearchServiceTest {

  @Mock private RestClient restClientMock;
  @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpecMock;
  @Mock private RestClient.RequestHeadersSpec requestHeadersSpecMock;
  @Mock private RestClient.ResponseSpec responseSpecMock;
  @Mock private ObjectProvider<RestClient> restClientProviderMock;
  @Mock private VzdTokenService vzdTokenServiceMock;

  private AutoCloseable closeable;
  private VzdTokenProperties properties;
  private VzdSearchService service;

  @BeforeEach
  void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
    when(restClientProviderMock.getIfAvailable(any())).thenReturn(restClientMock);
    when(restClientMock.get()).thenReturn(requestHeadersUriSpecMock);
    when(requestHeadersUriSpecMock.uri(any(URI.class))).thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.header(anyString(), anyString()))
        .thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);

    properties = new VzdTokenProperties();
    properties.setServiceAuthUrl("https://example.org/service-authenticate");
    service = new VzdSearchService(vzdTokenServiceMock, restClientProviderMock, properties);
  }

  @AfterEach
  void tearDown() throws Exception {
    closeable.close();
  }

  @Test
  void searchByTelematikIdReturnsMappedResult() {
    // given
    var telematikId = "T-123";
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");

    var bundle = getFhirBundle(telematikId);
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByTelematikId(telematikId);

    // then
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.entries()).hasSize(1);
    var entry = result.entries().getFirst();
    assertThat(entry.telematikId()).isEqualTo(telematikId);
    assertThat(entry.organizationName()).isEqualTo("OrgName");
    assertThat(entry.phoneNumbers()).containsExactly("+49-123");
    assertThat(entry.address()).isNotNull();
    assertThat(entry.location()).isEqualTo(new VzdLocation(22.22, 11.11));

    verify(restClientMock).get();
    verify(requestHeadersUriSpecMock).uri(uriContaining("HealthcareService"));
  }

  @Test
  void searchByTelematikIdThrowsOnRestClientError() {
    // given
    var telematikId = "T-ERR";
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    when(responseSpecMock.body(FhirBundle.class)).thenThrow(new RestClientException("boom"));

    // when / then
    var ex = assertThrows(VzdSearchException.class, () -> service.searchByTelematikId(telematikId));
    assertThat(ex.getMessage()).contains(telematikId);
  }

  @Test
  void searchByNameOrLocationValidatesLatLonPair() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");

    // when / then
    assertThrows(
        IllegalArgumentException.class,
        () -> service.searchByNameOrLocation("Name", null, 51.0, null, null, null));
  }

  @Test
  void searchByNameOrLocationRejectsLongitudeWithoutLatitude() {
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> service.searchByNameOrLocation(null, null, null, 7.0, null, null));

    assertThat(ex).hasMessageContaining("Latitude and longitude must be provided together");
  }

  @Test
  void searchByLocationTextRejectsNullOrWhitespaceOnlyText() {
    assertThrows(IllegalArgumentException.class, () -> service.searchByLocationText(null));
    assertThrows(IllegalArgumentException.class, () -> service.searchByLocationText(" \t\n"));
  }

  @Test
  void searchByPageUrlRejectsNullOrWhitespaceOnlyUrl() {
    assertThrows(IllegalArgumentException.class, () -> service.searchByPageUrl(null));
    assertThrows(IllegalArgumentException.class, () -> service.searchByPageUrl(" \t"));
  }

  @Test
  void searchByNameUsesTextQueryAndMapsResult() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-123");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result =
        service.searchByNameOrLocation("OrgName", null, null, null, null, null);

    // then
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.entries()).hasSize(1);
    var entry = result.entries().getFirst();
    assertThat(entry.organizationName()).isEqualTo("OrgName");

    verify(restClientMock).get();
    verify(requestHeadersUriSpecMock).uri(uriContaining("_text"));
  }

  @Test
  void searchByNameOrLocationPrefersFulltextAndAppliesDefaultPagination() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-123");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result =
        service.searchByNameOrLocation("IgnoredName", "PreferredText", 51.0, 7.0, null, null);

    // then
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.entries()).hasSize(1);

    verify(requestHeadersUriSpecMock).uri(uriContaining("_text=PreferredText"));
    verify(requestHeadersUriSpecMock).uri(uriContaining("location.near=51.0%7C7.0%7C10%7Ckm"));
    verify(requestHeadersUriSpecMock).uri(uriContaining("_count=20"));
    verify(requestHeadersUriSpecMock).uri(uriContaining("_offset=0"));
  }

  @Test
  void searchByNameOrLocationAddsLocationNearAndGeneratesNextLinkWhenPageFull() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-LOC");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByNameOrLocation(null, null, 11.11, 22.22, 5, 1);

    // then
    assertThat(result.entries()).hasSize(1);
    // next page url should be synthesized because page appears full and no next link provided
    assertThat(result.nextPageUrl()).isNotNull();
    assertThat(result.nextPageUrl()).contains("_offset=1");

    verify(requestHeadersUriSpecMock).uri(uriContaining("location.near"));
    verify(requestHeadersUriSpecMock).uri(uriContaining("_sort"));
  }

  @Test
  void searchByNameOrLocationReturnsEmptyResultForNullBundle() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(null);

    // when
    VzdSearchResult result = service.searchByNameOrLocation("Name", null, null, null, null, null);

    // then
    assertThat(result.total()).isZero();
    assertThat(result.entries()).isEmpty();
    assertThat(result.nextPageUrl()).isNull();
  }

  @Test
  void searchByNameOrLocationReturnsEmptyResultForBundleWithoutEntries() {
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    when(responseSpecMock.body(FhirBundle.class))
        .thenReturn(new FhirBundle("Bundle", "empty", "searchset", 4, null, null));

    VzdSearchResult result = service.searchByNameOrLocation(null, null, null, null, 5, 5);

    assertThat(result.total()).isZero();
    assertThat(result.entries()).isEmpty();
    assertThat(result.nextPageUrl()).isNull();
  }

  @Test
  void searchByLocationTextUsesPostalCodeAndMapsResult() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-LOC");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByLocationText("12345");

    // then
    assertThat(result.entries()).hasSize(1);
    assertThat(result.total()).isEqualTo(1);
    var entry = result.entries().getFirst();
    assertThat(entry.location()).isEqualTo(new VzdLocation(22.22, 11.11));

    verify(restClientMock).get();
    verify(requestHeadersUriSpecMock).uri(uriContaining("location.address-postalcode"));
  }

  @Test
  void searchByLocationTextTrimsInputBeforePostalCodeDetection() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle =
        new FhirBundle(
            "Bundle",
            "b2",
            "searchset",
            0,
            null,
            List.of(new FhirLink("self", "https://example.org/fhir/Bundle")));
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByLocationText(" 12345 ");

    // then
    assertThat(result.total()).isZero();
    assertThat(result.entries()).isEmpty();
    verify(requestHeadersUriSpecMock).uri(uriContaining("location.address-postalcode=12345"));
  }

  @Test
  void searchByLocationTextUsesAddressAndMapsResult() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-ADDR");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByLocationText("Some Street");

    // then
    assertThat(result.entries()).hasSize(1);
    var entry = result.entries().getFirst();
    assertThat(entry.organizationName()).isEqualTo("OrgName");

    verify(requestHeadersUriSpecMock).uri(uriContaining("location.address"));
  }

  @Test
  void searchByLocationTextThrowsOnBlankText() {
    // when / then
    assertThrows(IllegalArgumentException.class, () -> service.searchByLocationText(""));
  }

  @Test
  void searchByLocationTextThrowsOnRestClientError() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    when(responseSpecMock.body(FhirBundle.class)).thenThrow(new RestClientException("boom"));

    // when / then
    var ex = assertThrows(VzdSearchException.class, () -> service.searchByLocationText("Berlin"));
    assertThat(ex.getMessage()).contains("Berlin");
  }

  @Test
  void searchByPageUrlThrowsOnBlankUrl() {
    // when / then
    assertThrows(IllegalArgumentException.class, () -> service.searchByPageUrl(""));
  }

  @Test
  void searchByPageUrlReturnsMappedResultAndSynthesizesNextLink() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-PAGE");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    String url = "https://example.org/fhir/Bundle?_count=1";
    VzdSearchResult result = service.searchByPageUrl(url);

    // then
    assertThat(result.entries()).hasSize(1);
    assertThat(result.nextPageUrl()).isNotNull();
    assertThat(result.nextPageUrl()).contains("_offset=1");

    verify(restClientMock).get();
    verify(requestHeadersUriSpecMock).uri(uriContaining("example.org"));
  }

  @Test
  void searchByPageUrlKeepsExistingNextLinkAndDoesNotSynthesizeAnotherOne() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle =
        new FhirBundle(
            "Bundle",
            "b3",
            "searchset",
            1,
            List.of(
                new FhirEntry(
                    new FhirResource(
                        "HealthcareService",
                        "hs1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))),
            List.of(new FhirLink("next", "https://example.org/fhir/Bundle?_count=1&_offset=5")));
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result =
        service.searchByPageUrl("https://example.org/fhir/Bundle?_count=1&_offset=5");

    // then
    assertThat(result.nextPageUrl())
        .isEqualTo("https://example.org/fhir/Bundle?_count=1&_offset=5");
  }

  @Test
  void searchByPageUrlRejectsMoreThanOneHundredResults() {
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle =
        new FhirBundle(
            "Bundle",
            "too-many",
            "searchset",
            101,
            List.of(
                new FhirEntry(
                    new FhirResource(
                        "HealthcareService",
                        "hs1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))),
            null);
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    var ex =
        assertThrows(
            TooManyVzdSearchResultsException.class,
            () -> service.searchByPageUrl("https://example.org/fhir/Bundle?_count=20"));

    assertThat(ex).hasMessageContaining("101 entries");
  }

  @Test
  void searchByPageUrlSynthesizesNextLinkUsingExistingOffset() {
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(getFhirBundle("T-OFFSET"));

    var result = service.searchByPageUrl("https://example.org/fhir/Bundle?_count=1&_offset=5");

    assertThat(result.nextPageUrl()).contains("_offset=6");
  }

  @Test
  void searchByPageUrlDoesNotSynthesizeNextLinkWhenPageIsNotFull() {
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle =
        new FhirBundle(
            "Bundle",
            "partial",
            "searchset",
            1,
            List.of(
                new FhirEntry(
                    new FhirResource(
                        "HealthcareService",
                        "hs1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))),
            null);
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    var result = service.searchByPageUrl("https://example.org/fhir/Bundle?_count=2");

    assertThat(result.nextPageUrl()).isNull();
  }

  @Test
  void searchByPageUrlDoesNotSynthesizeWithoutCountParameter() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-PAGE");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByPageUrl("https://example.org/fhir/Bundle");

    // then
    assertThat(result.entries()).hasSize(1);
    assertThat(result.nextPageUrl()).isNull();
  }

  @Test
  void searchByPageUrlIgnoresInvalidCountValues() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-PAGE");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByPageUrl("https://example.org/fhir/Bundle?_count=abc");

    // then
    assertThat(result.entries()).hasSize(1);
    assertThat(result.nextPageUrl()).isNull();
  }

  @Test
  void searchByPageUrlThrowsOnRestClientError() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    when(responseSpecMock.body(FhirBundle.class)).thenThrow(new RestClientException("boom"));

    // when / then
    String url = "https://example.org/fhir/Bundle";
    var ex = assertThrows(VzdSearchException.class, () -> service.searchByPageUrl(url));
    assertThat(ex.getMessage()).contains(url);
  }

  @Test
  void searchByTelematikIdMapsMissingOptionalFieldsToNullOrEmptyValues() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var organization =
        new FhirResource(
            "Organization", "org1", "OrgName", null, null, null, null, null, null, null);
    var location =
        new FhirResource("Location", "loc1", null, null, null, null, null, null, null, null);
    var healthcare =
        new FhirResource(
            "HealthcareService",
            "hs1",
            null,
            null,
            null,
            null,
            null,
            new FhirReference("Organization/org1"),
            List.of(new FhirReference("Location/loc1")),
            null);
    var bundle =
        new FhirBundle(
            "Bundle",
            "b4",
            "searchset",
            1,
            List.of(
                new FhirEntry(healthcare), new FhirEntry(organization), new FhirEntry(location)),
            null);
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByTelematikId("T-EMPTY");

    // then
    assertThat(result.entries()).hasSize(1);
    var entry = result.entries().getFirst();
    assertThat(entry.telematikId()).isNull();
    assertThat(entry.organizationName()).isEqualTo("OrgName");
    assertThat(entry.phoneNumbers()).isEmpty();
    assertThat(entry.address()).isNull();
    assertThat(entry.location()).isNull();
  }

  @Test
  void mapsOnlyHealthcareServicesAndLeavesUnresolvedReferencesEmpty() {
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var healthcareService =
        new FhirResource(
            "HealthcareService",
            "hs1",
            null,
            null,
            null,
            null,
            null,
            new FhirReference("Organization/missing"),
            List.of(new FhirReference("Location/missing")),
            null);
    var bundle =
        new FhirBundle(
            "Bundle",
            "unresolved",
            "searchset",
            2,
            List.of(
                new FhirEntry(
                    new FhirResource(
                        "Organization",
                        "org1",
                        "Ignored",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)),
                new FhirEntry(healthcareService)),
            null);
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    var result = service.searchByTelematikId("T-UNRESOLVED");

    assertThat(result.entries()).hasSize(1);
    assertThat(result.entries().getFirst().organizationName()).isNull();
    assertThat(result.entries().getFirst().address()).isNull();
    assertThat(result.entries().getFirst().location()).isNull();
  }

  @Test
  void mapsNullTotalAndCaseInsensitiveNextLink() {
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle =
        new FhirBundle(
            "Bundle",
            "links",
            "searchset",
            null,
            List.of(
                new FhirEntry(
                    new FhirResource(
                        "Organization", "org1", "Org", null, null, null, null, null, null, null))),
            List.of(new FhirLink("NEXT", "https://example.org/next")));
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    var result = service.searchByTelematikId("T-LINK");

    assertThat(result.total()).isZero();
    assertThat(result.entries()).isEmpty();
    assertThat(result.nextPageUrl()).isEqualTo("https://example.org/next");
  }

  @Test
  void filtersIdentifiersAndTelecomAndMapsMultipleAddressLines() {
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var organization =
        new FhirResource(
            "Organization",
            "org1",
            "Filtered Org",
            null,
            List.of(
                new FhirTelecom("email", "ignored@example.org"),
                new FhirTelecom("Phone", "+49-111"),
                new FhirTelecom("phone", " "),
                new FhirTelecom("phone", "+49-222")),
            List.of(
                new FhirAddress(List.of("Street 1", "Building A"), "12345", "Berlin", null, null)),
            List.of(
                new FhirIdentifier("wrong", "123456789"),
                new FhirIdentifier(null, "12345678"),
                new FhirIdentifier(null, "123456789"),
                new FhirIdentifier("https://gematik.de/fhir/sid/telematik-id", "T-FILTERED")),
            null,
            null,
            null);
    var healthcareService =
        new FhirResource(
            "HealthcareService",
            "hs1",
            null,
            null,
            null,
            null,
            null,
            new FhirReference("Organization/org1"),
            null,
            null);
    when(responseSpecMock.body(FhirBundle.class))
        .thenReturn(
            new FhirBundle(
                "Bundle",
                "filtered",
                "searchset",
                1,
                List.of(new FhirEntry(healthcareService), new FhirEntry(organization)),
                null));

    var result = service.searchByTelematikId("T-FILTERED");
    var entry = result.entries().getFirst();

    assertThat(entry.telematikId()).isEqualTo("T-FILTERED");
    assertThat(entry.iknr()).isEqualTo("123456789");
    assertThat(entry.phoneNumbers()).containsExactly("+49-111", "+49-222");
    assertThat(entry.address().line()).isEqualTo("Street 1 Building A");
  }

  @Test
  void usesIncludedLocationAddressWhenOrganizationHasNoAddress() {
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var organization =
        new FhirResource(
            "Organization",
            "org1",
            "OrgName",
            null,
            null,
            null,
            List.of(
                new FhirIdentifier(
                    "https://gematik.de/fhir/sid/telematik-id", "1-2arvtst-ap000000")),
            null,
            null,
            null);
    var location =
        new FhirResource(
            "Location",
            "loc1",
            null,
            null,
            null,
            List.of(new FhirAddress(List.of("Teststrasse 1"), "10115", "Berlin", null, null)),
            null,
            null,
            null,
            null);
    var healthcareService =
        new FhirResource(
            "HealthcareService",
            "hs1",
            null,
            null,
            null,
            null,
            null,
            new FhirReference("Organization/org1"),
            List.of(new FhirReference("Location/loc1")),
            null);
    when(responseSpecMock.body(FhirBundle.class))
        .thenReturn(
            new FhirBundle(
                "Bundle",
                "location-address",
                "searchset",
                1,
                List.of(
                    new FhirEntry(healthcareService),
                    new FhirEntry(organization),
                    new FhirEntry(location)),
                null));

    var result = service.searchByTelematikId("1-2arvtst-ap000000");

    assertThat(result.entries().getFirst().address().line()).isEqualTo("Teststrasse 1");
    assertThat(result.entries().getFirst().address().postalCode()).isEqualTo("10115");
    assertThat(result.entries().getFirst().address().city()).isEqualTo("Berlin");
  }

  @Test
  void sendsFhirAcceptAndBearerAuthorizationHeaders() {
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(null);

    service.searchByTelematikId("T-HEADERS");

    verify(requestHeadersSpecMock).header("Accept", "application/fhir+json");
    verify(requestHeadersSpecMock).header("Authorization", "Bearer atk");
  }

  private static URI uriContaining(String fragment) {
    return org.mockito.ArgumentMatchers.argThat(
        uri -> uri != null && uri.toString().contains(fragment));
  }

  private static @NonNull FhirBundle getFhirBundle(String telematikId) {
    var organization =
        new FhirResource(
            "Organization",
            "org1",
            "OrgName",
            null,
            List.of(new FhirTelecom("phone", "+49-123")),
            List.of(new FhirAddress(List.of("Street 1"), "12345", "City", null, null)),
            List.of(new FhirIdentifier("https://gematik.de/fhir/sid/telematik-id", telematikId)),
            null,
            null,
            null);

    var location =
        new FhirResource(
            "Location",
            "loc1",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new FhirPosition(11.11, 22.22, null));

    var healthcare =
        new FhirResource(
            "HealthcareService",
            "hs1",
            null,
            null,
            null,
            null,
            null,
            new FhirReference("Organization/org1"),
            List.of(new FhirReference("Location/loc1")),
            null);

    return new FhirBundle(
        "Bundle",
        "b1",
        "searchset",
        1,
        List.of(new FhirEntry(healthcare), new FhirEntry(organization), new FhirEntry(location)),
        null);
  }
}
