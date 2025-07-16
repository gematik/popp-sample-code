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

package de.gematik.refpopp.popp_server.scenario.common.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.gematik.poppcommons.api.exceptions.ValidationException;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatusWordValidatorTest {

  StatusWordValidator sut;

  @BeforeEach
  void setUp() {
    sut = new StatusWordValidator();
  }

  @Test
  void validateSucceeds() {
    // given
    final List<StepDefinition> stepDefinitions =
        List.of(
            new StepDefinition("name1", "Description1", "Command1", List.of("9000", "6A82")),
            new StepDefinition("name2", "Description2", "Command2", List.of("6283", "6985")));
    final var scenario = new Scenario("Scenario", stepDefinitions);

    final var resultStep = new ScenarioResultStep("description", "9000", "abcdef".getBytes());
    final var resultStep2 = new ScenarioResultStep("description2", "6985", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));

    // when / then
    assertDoesNotThrow(() -> sut.validate(scenarioResult, scenario));
  }

  @Test
  void validateThrowsExceptionWhenStatusWordIsNotExpected() {
    // given
    final List<StepDefinition> stepDefinitions =
        List.of(
            new StepDefinition("name1", "Description1", "Command1", List.of("9000", "6A82")),
            new StepDefinition("name2", "Description2", "Command2", List.of("6283", "6985")));
    final var scenario = new Scenario("Scenario", stepDefinitions);

    final var resultStep = new ScenarioResultStep("description", "9000", "abcdef".getBytes());
    final var resultStep2 = new ScenarioResultStep("description2", "1234", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));

    // when
    final var exception =
        assertThrows(ValidationException.class, () -> sut.validate(scenarioResult, scenario));

    // then
    assertThat(exception.getErrorCode()).isEqualTo("errorCode");
    assertThat(exception.getMessage()).isEqualTo("Expected StatusWord [6283, 6985] but got 1234");
  }

  @Test
  void validateThrowsExceptionWhenNumberOfExpectedResultsDoesNotMatchNumberOfReceivedResults() {
    // given
    final List<StepDefinition> stepDefinitions =
        List.of(
            new StepDefinition("name1", "Description1", "Command1", List.of("9000", "6A82")),
            new StepDefinition("name2", "Description2", "Command2", List.of("6283", "6985")));
    final var scenario = new Scenario("Scenario", stepDefinitions);

    final var resultStep = new ScenarioResultStep("description", "9000", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep));

    // when
    final var exception =
        assertThrows(ValidationException.class, () -> sut.validate(scenarioResult, scenario));

    // then
    assertThat(exception.getErrorCode()).isEqualTo("errorCode");
    assertThat(exception.getMessage())
        .isEqualTo("Number of expected results does not match number of received results");
  }
}
