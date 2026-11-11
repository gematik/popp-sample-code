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

import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

class CheckInStatusResponseTest {

  private static final UUID ID = UUID.fromString("237cc7b7-f85e-42c3-981c-ad7f9d97ddca");
  private final ObjectMapper mapper = new ObjectMapper();

  @ParameterizedTest
  @CsvSource({"PENDING,pending", "SUCCESS,success", "CANCELED,canceled"})
  void writesContractFieldNamesAndLowercaseState(
      PoppTokenGenerationStatus status, String expectedState) {
    final var response = new CheckInStatusResponse(status.getValue(), ID);

    assertThat(mapper.readTree(mapper.writeValueAsString(response)))
        .isEqualTo(
            mapper.readTree(
                """
                {"state":"%s","poppStatusRecordId":"237cc7b7-f85e-42c3-981c-ad7f9d97ddca"}
                """
                    .formatted(expectedState)));
  }

  @Test
  void omitsAbsentOptionalFieldsInsteadOfWritingNull() {
    assertThat(mapper.readTree(mapper.writeValueAsString(new CheckInStatusResponse(null, ID))))
        .isEqualTo(
            mapper.readTree(
                """
                {"poppStatusRecordId":"237cc7b7-f85e-42c3-981c-ad7f9d97ddca"}
                """));
    assertThat(
            mapper.readTree(mapper.writeValueAsString(new CheckInStatusResponse("pending", null))))
        .isEqualTo(mapper.readTree("{\"state\":\"pending\"}"));
    assertThat(mapper.readTree(mapper.writeValueAsString(new CheckInStatusResponse(null, null))))
        .isEqualTo(mapper.readTree("{}"));
  }
}
