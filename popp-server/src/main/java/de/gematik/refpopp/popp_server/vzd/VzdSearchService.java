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

import de.gematik.refpopp.popp_server.vzd.dto.VzdAddress;
import de.gematik.refpopp.popp_server.vzd.dto.VzdEntry;
import de.gematik.refpopp.popp_server.vzd.dto.VzdLocation;
import de.gematik.refpopp.popp_server.vzd.dto.VzdSearchResult;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirBundle;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirEntry;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirIdentifier;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirLink;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirReference;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirResource;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirTelecom;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class VzdSearchService {

  private static final String TELEMATIK_ID_SYSTEM = "https://gematik.de/fhir/sid/telematik-id";
  private static final String RT_HEALTHCARE_SERVICE = "HealthcareService";
  private static final String SEARCH_PATH = "search";
  private static final String ORGANIZATION_ACTIVE = "organization.active";
  private static final String INCLUDE = "_include";
  private static final String ORGANIZATION_IDENTIFIER = "organization.identifier";
  private static final String COUNT = "_count";
  private static final String FORMAT = "_format";
  private static final String HEALTHCARE_SERVICE_ORGANIZATION = "HealthcareService:organization";
  private static final String HEALTHCARE_SERVICE_LOCATION = "HealthcareService:location";
  private static final String APPLICATION_FHIR_JSON = "application/fhir+json";
  private static final String BEARER = "Bearer ";
  private static final String OFFSET = "_offset";
  private static final int MAX_MOBILE_SEARCH_RESULTS = 100;
  private final VzdTokenService vzdTokenService;
  private final RestClient restClient;
  private final VzdTokenProperties properties;

  public VzdSearchService(
      VzdTokenService vzdTokenService,
      ObjectProvider<RestClient> restClientProvider,
      VzdTokenProperties properties) {
    this.vzdTokenService = vzdTokenService;
    this.restClient = restClientProvider.getIfAvailable(RestClient::create);
    this.properties = properties;
  }

  public VzdSearchResult searchByTelematikId(String telematikId) {
    var accessToken = vzdTokenService.getAccessToken();

    try {
      var uri =
          createSearchUriBuilder()
              .queryParam(ORGANIZATION_IDENTIFIER, TELEMATIK_ID_SYSTEM + "|" + telematikId)
              .queryParam(COUNT, "1")
              .queryParam(FORMAT, "json")
              .build()
              .toUri();

      log.info("| Searching VZD for telematikId {} at {}", telematikId, uri);
      var bundle = executeFhirSearch(uri, accessToken);

      return toDto(bundle);
    } catch (RestClientException e) {
      throw new VzdSearchException("VZD search failed for telematikId " + telematikId, e);
    }
  }

  /** Searches LEI entries by organization/service name and/or location radius. */
  public VzdSearchResult searchByNameOrLocation(
      String name, String fulltext, Double lat, Double lon, Integer radiusKm, Integer count) {
    boolean hasLocation = lat != null && lon != null;
    boolean hasPartialLocation = lat != null || lon != null;
    if (hasPartialLocation && !hasLocation) {
      throw new IllegalArgumentException("Latitude and longitude must be provided together.");
    }

    var accessToken = vzdTokenService.getAccessToken();
    int effectiveCount = count != null ? count : 20;

    try {
      var builder = createSearchUriBuilder();

      var text = StringUtils.hasText(fulltext) ? fulltext : name;
      if (StringUtils.hasText(text)) {
        builder.queryParam("_text", text);
      }

      if (hasLocation) {
        builder.queryParam(
            "location.near", "%s|%s|%s|km".formatted(lat, lon, radiusKm != null ? radiusKm : 10));
        builder.queryParam("_sort", "location.near");
      }

      builder.queryParam(COUNT, effectiveCount).queryParam(OFFSET, "0").queryParam(FORMAT, "json");

      var uri = builder.build().toUri();

      log.info("| Searching VZD for name/location at {}", uri);
      var bundle = executeFhirSearch(uri, accessToken);

      return toDto(withSyntheticNextLinkIfFullPage(bundle, uri, effectiveCount));
    } catch (RestClientException e) {
      throw new VzdSearchException("VZD search by name or location failed", e);
    }
  }

  public VzdSearchResult searchByLocationText(String text) {
    if (!StringUtils.hasText(text)) {
      throw new IllegalArgumentException("Location text must not be blank");
    }

    var accessToken = vzdTokenService.getAccessToken();
    int count = 5;

    try {
      var builder = createSearchUriBuilder();

      var trimmed = text.trim();
      // consider German 5-digit postal codes
      if (trimmed.matches("\\d{5}")) {
        builder.queryParam("location.address-postalcode", trimmed);
      } else {
        builder.queryParam("location.address", trimmed);
      }

      builder.queryParam(COUNT, Integer.toString(count)).queryParam(FORMAT, "json");

      var uri = builder.build().toUri();

      log.info("| Searching VZD by location text at {}", uri);
      var bundle = executeFhirSearch(uri, accessToken);

      return toDto(bundle);
    } catch (RestClientException e) {
      throw new VzdSearchException("VZD search by location text failed for " + text, e);
    }
  }

  public VzdSearchResult searchByPageUrl(String url) {
    if (!StringUtils.hasText(url)) {
      throw new IllegalArgumentException("URL must not be blank");
    }

    var accessToken = vzdTokenService.getAccessToken();

    try {
      // Extract only safe, typed scalars from the user-supplied URL.
      // The URI that reaches executeFhirSearch is rebuilt entirely from the
      // trusted configured base — no user-controlled string ever flows into
      // the HTTP sink, which eliminates the SSRF taint path.
      var userParams = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
      var count = parseIntOrNull(userParams.getFirst(COUNT));
      var offset = parseIntOrNull(userParams.getFirst(OFFSET));

      var builder = createSearchUriBuilder();
      if (count != null) {
        builder.queryParam(COUNT, count);
      }
      if (offset != null) {
        builder.queryParam(OFFSET, offset);
      }
      builder.queryParam(FORMAT, "json");

      var safeUri = builder.build().toUri();

      log.info("| Searching VZD by page URL at {}", safeUri);
      var bundle = executeFhirSearch(safeUri, accessToken);

      var result = toDto(withSyntheticNextLinkIfFullPage(bundle, safeUri, count));
      if (result.total() > MAX_MOBILE_SEARCH_RESULTS) {
        throw new TooManyVzdSearchResultsException(result.total());
      }
      return result;
    } catch (RestClientException e) {
      throw new VzdSearchException("VZD search by page URL failed for " + url, e);
    }
  }

  private UriComponentsBuilder createSearchUriBuilder() {
    return UriComponentsBuilder.fromUriString(getBaseUrl())
        .pathSegment(SEARCH_PATH, RT_HEALTHCARE_SERVICE)
        .queryParam(ORGANIZATION_ACTIVE, "true")
        .queryParam(INCLUDE, HEALTHCARE_SERVICE_ORGANIZATION)
        .queryParam(INCLUDE, HEALTHCARE_SERVICE_LOCATION);
  }

  private FhirBundle executeFhirSearch(URI uri, String accessToken) {
    return restClient
        .get()
        .uri(uri)
        .header(HttpHeaders.ACCEPT, MediaType.parseMediaType(APPLICATION_FHIR_JSON).toString())
        .header(HttpHeaders.AUTHORIZATION, BEARER + accessToken)
        .retrieve()
        .body(FhirBundle.class);
  }

  private String getBaseUrl() {
    return UriComponentsBuilder.fromUriString(properties.getServiceAuthUrl())
        .replacePath(null)
        .build()
        .toUriString();
  }

  /**
   * Appends a synthetic "next" link to the bundle if the result page appears to be full, i.e. the
   * number of returned {@code HealthcareService} entries reached the requested {@code _count} and
   * the upstream server did not already provide a "next" link itself.
   */
  private FhirBundle withSyntheticNextLinkIfFullPage(
      FhirBundle bundle, URI requestUri, Integer explicitCount) {
    if (bundle == null) {
      return null;
    }

    List<FhirLink> currentLinks = bundle.link() == null ? List.of() : bundle.link();
    var queryParams = UriComponentsBuilder.fromUri(requestUri).build().getQueryParams();
    var count = explicitCount != null ? explicitCount : parseIntOrNull(queryParams.getFirst(COUNT));
    var serviceCount =
        bundle.entry() == null
            ? 0
            : bundle.entry().stream()
                .filter(
                    e ->
                        e.resource() != null
                            && RT_HEALTHCARE_SERVICE.equalsIgnoreCase(e.resource().resourceType()))
                .count();
    boolean shouldAppendNextLink =
        count != null
            && currentLinks.stream().noneMatch(l -> "next".equalsIgnoreCase(l.relation()))
            && serviceCount >= count;

    if (!shouldAppendNextLink) {
      return bundle;
    }

    var parsedOffset = parseIntOrNull(queryParams.getFirst(OFFSET));
    int offset = parsedOffset != null ? parsedOffset : 0;
    var nextUri =
        UriComponentsBuilder.fromUri(requestUri)
            .replaceQueryParam(OFFSET, offset + count)
            .build(true)
            .toUri();

    List<FhirLink> newLinks = new ArrayList<>(currentLinks);
    newLinks.add(new FhirLink("next", nextUri.toString()));

    return new FhirBundle(
        bundle.resourceType(),
        bundle.id(),
        bundle.type(),
        bundle.total(),
        bundle.entry(),
        newLinks);
  }

  private Integer parseIntOrNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Maps the internal FHIR bundle into the public {@link VzdSearchResult} DTO. */
  private VzdSearchResult toDto(FhirBundle bundle) {
    if (bundle == null || bundle.entry() == null) {
      return new VzdSearchResult(0, List.of(), extractNextPageUrl(bundle));
    }

    Map<String, FhirResource> byReference = new HashMap<>();
    for (FhirEntry entry : bundle.entry()) {
      var resource = entry.resource();
      if (resource != null && resource.resourceType() != null && resource.id() != null) {
        byReference.put(resource.resourceType() + "/" + resource.id(), resource);
      }
    }

    List<VzdEntry> entries = new ArrayList<>();
    for (FhirEntry entry : bundle.entry()) {
      var resource = entry.resource();
      if (resource == null || !RT_HEALTHCARE_SERVICE.equals(resource.resourceType())) {
        continue;
      }
      entries.add(toEntry(resource, byReference));
    }

    return new VzdSearchResult(
        bundle.total() == null ? 0 : bundle.total(), entries, extractNextPageUrl(bundle));
  }

  private String extractNextPageUrl(FhirBundle bundle) {
    if (bundle == null || bundle.link() == null) {
      return null;
    }
    return bundle.link().stream()
        .filter(l -> "next".equalsIgnoreCase(l.relation()))
        .map(FhirLink::url)
        .findFirst()
        .orElse(null);
  }

  private VzdEntry toEntry(FhirResource healthcareService, Map<String, FhirResource> byReference) {
    var organization = resolve(healthcareService.providedBy(), byReference);
    var location = resolveFirst(healthcareService.location(), byReference);

    return new VzdEntry(
        extractTelematikId(organization),
        extractIknr(organization),
        organization != null ? organization.name() : null,
        extractPhoneNumbers(organization),
        resolveAddress(location, organization),
        toLocation(location));
  }

  private VzdAddress resolveAddress(FhirResource location, FhirResource organization) {
    var locationAddress = toAddress(location);
    return locationAddress != null ? locationAddress : toAddress(organization);
  }

  private String extractIknr(FhirResource organization) {
    if (organization == null || organization.identifier() == null) {
      return null;
    }
    return organization.identifier().stream()
        .map(FhirIdentifier::value)
        .filter(StringUtils::hasText)
        .filter(v -> v.matches("^\\d{9}$"))
        .findFirst()
        .orElse(null);
  }

  private String extractTelematikId(FhirResource organization) {
    if (organization == null || organization.identifier() == null) {
      return null;
    }
    return organization.identifier().stream()
        .filter(id -> TELEMATIK_ID_SYSTEM.equals(id.system()))
        .map(FhirIdentifier::value)
        .filter(StringUtils::hasText)
        .findFirst()
        .orElse(null);
  }

  private List<String> extractPhoneNumbers(FhirResource organization) {
    if (organization == null || organization.telecom() == null) {
      return List.of();
    }
    return organization.telecom().stream()
        .filter(t -> "phone".equalsIgnoreCase(t.system()))
        .map(FhirTelecom::value)
        .filter(StringUtils::hasText)
        .toList();
  }

  private VzdAddress toAddress(FhirResource resource) {
    if (resource == null || resource.address() == null || resource.address().isEmpty()) {
      return null;
    }
    var address = resource.address().getFirst();
    var line = address.line() == null ? null : String.join(" ", address.line());
    return new VzdAddress(line, address.postalCode(), address.city());
  }

  private VzdLocation toLocation(FhirResource location) {
    if (location == null || location.position() == null) {
      return null;
    }
    var position = location.position();
    return new VzdLocation(position.latitude(), position.longitude());
  }

  private FhirResource resolve(FhirReference reference, Map<String, FhirResource> byReference) {
    if (reference == null || !StringUtils.hasText(reference.reference())) {
      return null;
    }
    return byReference.get(reference.reference());
  }

  private FhirResource resolveFirst(
      List<FhirReference> references, Map<String, FhirResource> byReference) {
    if (references == null || references.isEmpty()) {
      return null;
    }
    return resolve(references.getFirst(), byReference);
  }
}
