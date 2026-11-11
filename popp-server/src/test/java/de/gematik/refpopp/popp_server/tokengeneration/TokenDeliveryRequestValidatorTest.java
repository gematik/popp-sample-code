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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.refpopp.popp_server.controller.dto.TokenDeliveryRequest;
import jakarta.validation.Validation;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TokenDeliveryRequestValidatorTest {

  private final TokenDeliveryRequestValidator validator;

  TokenDeliveryRequestValidatorTest() {
    final var factory = Validation.buildDefaultValidatorFactory();
    validator = new TokenDeliveryRequestValidator(factory.getValidator());
  }

  @Test
  void acceptsBoundaryLimitsAndBothUnfilteredForms() {
    final var unfilteredRequest = request(null, 1);
    final var emptyWorkplacesRequest = request(List.of(), 100);
    assertThatCode(() -> validator.validate(unfilteredRequest, "CLIENT-123/2.1.12-45"))
        .doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(emptyWorkplacesRequest, "A/1"))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsValidWorkplaceIds() {
    final var validRequest =
        request(List.of("Arbeitsplatz 1", "019f4c0e-da55-7d3b-93e6-543c7aa723bd"), 5);
    assertThatCode(() -> validator.validate(validRequest, "A/1")).doesNotThrowAnyException();
  }

  @Test
  void rejectsMissingRequestBody() {
    assertThatThrownBy(() -> validator.validate(null, "A/1"))
        .isInstanceOf(InvalidTokenDeliveryRequestException.class)
        .hasMessageContaining("Request body");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {" ", "A", "A/", "A/1/2", "A_B/1", "ABCDEFGHIJKLMNOPQRSTU/1", "A/1234567890123456"})
  void rejectsInvalidUserAgent(String userAgent) {
    final var tokenRequest = request(null, 1);
    assertThatThrownBy(() -> validator.validate(tokenRequest, userAgent))
        .isInstanceOf(InvalidTokenDeliveryRequestException.class)
        .hasMessageContaining("x-useragent");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 101})
  void rejectsOutOfRangeLimit(int limit) {
    final var tokenRequest = request(null, limit);
    assertThatThrownBy(() -> validator.validate(tokenRequest, "A/1"))
        .isInstanceOf(InvalidTokenDeliveryRequestException.class)
        .hasMessageContaining("limit");
  }

  @Test
  void rejectsMissingLimit() {
    final var tokenRequest = request(null, null);
    assertThatThrownBy(() -> validator.validate(tokenRequest, "A/1"))
        .isInstanceOf(InvalidTokenDeliveryRequestException.class)
        .hasMessageContaining("limit");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void rejectsMissingOrBlankTelematikId(String telematikId) {
    final var tokenRequest = new TokenDeliveryRequest(telematikId, null, 1);
    assertThatThrownBy(() -> validator.validate(tokenRequest, "A/1"))
        .isInstanceOf(InvalidTokenDeliveryRequestException.class)
        .hasMessageContaining("telematikId");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"/", "A:B", "A*B", "A?B", "A\"B", "A<B", "A>B", "A|B"})
  void rejectsInvalidWorkplaceId(String workplaceId) {
    final var tokenRequest = request(Arrays.asList("Arbeitsplatz 1", workplaceId), 1);
    assertThatThrownBy(() -> validator.validate(tokenRequest, "A/1"))
        .isInstanceOf(InvalidTokenDeliveryRequestException.class)
        .hasMessageContaining("workplaceIds");
  }

  @Test
  void rejectsWorkplaceIdLongerThan64Characters() {
    final var tokenRequest = request(List.of("A".repeat(65)), 1);
    assertThatThrownBy(() -> validator.validate(tokenRequest, "A/1"))
        .isInstanceOf(InvalidTokenDeliveryRequestException.class)
        .hasMessageContaining("workplaceIds");
  }

  private TokenDeliveryRequest request(List<String> workplaces, Integer limit) {
    return new TokenDeliveryRequest("test-id", workplaces, limit);
  }
}
