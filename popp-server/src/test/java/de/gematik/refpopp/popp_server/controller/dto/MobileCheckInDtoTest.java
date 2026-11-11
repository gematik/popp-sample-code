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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MobileCheckInDtoTest {

  private static final ValidatorFactory VALIDATOR_FACTORY =
      Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();
  private final ObjectMapper mapper = new ObjectMapper();

  @AfterAll
  static void closeValidatorFactory() {
    VALIDATOR_FACTORY.close();
  }

  @Test
  void deserializesValidRequestWithOptionalWorkplace() {
    final var request =
        mapper.readValue(
            """
            {"telematikId":"1-SMC-B-Testkarte--883110000168757","workplaceId":"Arbeitsplatz 1"}
            """,
            MobileCheckInRequest.class);

    assertThat(request.telematikId()).isEqualTo("1-SMC-B-Testkarte--883110000168757");
    assertThat(request.workplaceId()).isEqualTo("Arbeitsplatz 1");
    assertThat(VALIDATOR.validate(request)).isEmpty();
    assertThat(VALIDATOR.validate(new MobileCheckInRequest("actor-id", null))).isEmpty();
  }

  @Test
  void rejectsMissingOrBlankTelematikId() {
    assertThat(VALIDATOR.validate(new MobileCheckInRequest(null, null)))
        .anySatisfy(
            violation -> assertThat(violation.getPropertyPath()).hasToString("telematikId"));
    assertThat(VALIDATOR.validate(new MobileCheckInRequest("  ", null)))
        .anySatisfy(
            violation -> assertThat(violation.getPropertyPath()).hasToString("telematikId"));
  }

  @Test
  void rejectsInvalidWorkplaceId() {
    for (final var workplaceId : new String[] {"", "bad/name", "bad:name", "x".repeat(65)}) {
      assertThat(VALIDATOR.validate(new MobileCheckInRequest("actor-id", workplaceId)))
          .as("workplaceId: %s", workplaceId)
          .anySatisfy(
              violation -> assertThat(violation.getPropertyPath()).hasToString("workplaceId"));
    }
  }

  @Test
  void serializesRequiredResponseFields() {
    final var id = UUID.fromString("237cc7b7-f85e-42c3-981c-ad7f9d97ddca");

    final var json = mapper.writeValueAsString(new MobileCheckInResponse("pending", id));

    assertThat(json)
        .isEqualTo(
            "{\"state\":\"pending\",\"poppStatusRecordId\":\"237cc7b7-f85e-42c3-981c-ad7f9d97ddca\"}");
  }

  @Test
  void responseRequiresStateAndRecordId() {
    final var id = UUID.randomUUID();

    assertThatNullPointerException().isThrownBy(() -> new MobileCheckInResponse(null, id));
    assertThatNullPointerException().isThrownBy(() -> new MobileCheckInResponse("pending", null));
  }
}
