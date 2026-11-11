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

import de.gematik.refpopp.popp_server.controller.dto.TokenDeliveryRequest;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

/** Validates the token-delivery request and its required client header. */
@Component
public class TokenDeliveryRequestValidator {

  private final Validator validator;

  public TokenDeliveryRequestValidator(final Validator validator) {
    this.validator = validator;
  }

  public void validate(final TokenDeliveryRequest request, final String userAgent) {
    if (request == null) {
      throw new InvalidTokenDeliveryRequestException("Request body is required");
    }
    if (!UserAgentHeader.isValid(userAgent)) {
      throw new InvalidTokenDeliveryRequestException("Invalid x-useragent header");
    }
    final var violations = validator.validate(request);
    if (!violations.isEmpty()) {
      final var fields =
          violations.stream()
              .map(violation -> violation.getPropertyPath().toString())
              .sorted()
              .distinct()
              .toList();
      throw new InvalidTokenDeliveryRequestException("Invalid request fields: " + fields);
    }
  }
}
