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

package de.gematik.refpopp.popp_server.controller.dto;

import de.gematik.refpopp.popp_server.vzd.dto.VzdAddress;
import de.gematik.refpopp.popp_server.vzd.dto.VzdEntry;
import de.gematik.refpopp.popp_server.vzd.dto.VzdSearchResult;
import java.util.List;
import java.util.Optional;
import org.springframework.util.StringUtils;

/**
 * Maps the internal {@link VzdSearchResult}/{@link VzdEntry} model onto the public {@link
 * FhirVzdSearchResponse} API contract. The internal address/contact structures are flattened into
 * single display strings as required by the OpenAPI schema.
 */
public final class FhirVzdSearchResponseMapper {

  private FhirVzdSearchResponseMapper() {}

  /**
   * @return the mapped response, or {@code null} if the search yielded no result.
   */
  public static FhirVzdSearchResponse toResponse(final VzdSearchResult result) {
    if (result == null || result.entries() == null || result.entries().isEmpty()) {
      return null;
    }
    return new FhirVzdSearchResponse(
        result.entries().stream().map(FhirVzdSearchResponseMapper::toItem).toList());
  }

  /** Converts a single VZD entry into the public API response item. */
  private static FhirVzdSearchResultItem toItem(final VzdEntry entry) {
    if (entry == null) {
      return null;
    }
    return new FhirVzdSearchResultItem(
        entry.organizationName(),
        entry.telematikId(),
        entry.iknr(),
        formatAddress(entry.address()),
        formatContact(entry.phoneNumbers()));
  }

  private static String formatAddress(final VzdAddress address) {
    if (address == null) {
      return null;
    }
    var cityLine = joinNonBlank(" ", address.postalCode(), address.city());
    var full = joinNonBlank(", ", address.line(), cityLine);
    return StringUtils.hasText(full) ? full : null;
  }

  private static String formatContact(final List<String> phoneNumbers) {
    if (phoneNumbers == null || phoneNumbers.isEmpty()) {
      return null;
    }
    var numbers = String.join(", ", phoneNumbers.stream().filter(StringUtils::hasText).toList());
    return StringUtils.hasText(numbers) ? "Telefon: " + numbers : null;
  }

  private static String joinNonBlank(final String delimiter, final String... parts) {
    return String.join(
        delimiter,
        java.util.Arrays.stream(parts)
            .map(part -> Optional.ofNullable(part).orElse(""))
            .filter(StringUtils::hasText)
            .toList());
  }
}
