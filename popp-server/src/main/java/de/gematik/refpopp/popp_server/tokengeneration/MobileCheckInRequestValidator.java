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

package de.gematik.refpopp.popp_server.tokengeneration;

import de.gematik.refpopp.popp_server.controller.dto.MobileCheckInRequest;
import jakarta.validation.Validator;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MobileCheckInRequestValidator {

  private static final Pattern KVNR_PATTERN = Pattern.compile("^[A-Z]\\d{9}$");
  private static final Pattern IKNR_PATTERN = Pattern.compile("^\\d{9}$");

  private final Validator validator;

  public MobileCheckInRequestValidator(final Validator validator) {
    this.validator = validator;
  }

  public String validate(
      final MobileCheckInRequest request,
      final String userAgent,
      final String kvnr,
      final String iknr) {
    if (!UserAgentHeader.isValid(userAgent)) {
      throw new InvalidMobileCheckInRequestException("Invalid x-useragent header");
    }
    if (kvnr == null || !KVNR_PATTERN.matcher(kvnr).matches()) {
      throw new InvalidMobileCheckInRequestException("Invalid x-kvnr header");
    }
    if (iknr == null || !IKNR_PATTERN.matcher(iknr).matches()) {
      throw new InvalidMobileCheckInRequestException("Invalid x-iknr header");
    }
    if (request == null) {
      throw new InvalidMobileCheckInRequestException("Request body is required");
    }
    final var violations = validator.validate(request);
    if (!violations.isEmpty()) {
      final var fields =
          violations.stream()
              .map(violation -> violation.getPropertyPath().toString())
              .sorted()
              .distinct()
              .toList();
      throw new InvalidMobileCheckInRequestException("Invalid request fields: " + fields);
    }
    return UserAgentHeader.clientId(userAgent);
  }
}
