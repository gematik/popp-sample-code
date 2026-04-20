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

package de.gematik.refpopp.popp_server.scenario.common.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.gematik.poppcommons.api.exceptions.ValidationException;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioId;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioResultFactoryTest {

  private ScenarioResultFactory sut;

  @BeforeEach
  void setUp() {
    sut = new ScenarioResultFactory();
  }

  @Test
  void createScenarioResult() {
    // given
    final var stepDefinition1 = new StepDefinition(StepId.SELECT_MASTER_FILE);
    final var stepDefinition2 = new StepDefinition(StepId.READ_VERSION);
    final var scenario =
        new Scenario(ScenarioId.OPEN_EGK, List.of(stepDefinition1, stepDefinition2));
    final var responses = List.of("9000", "abcdef");

    // when
    final var scenarioResult = sut.create(scenario, responses);

    // then
    assertThat(scenarioResult.scenarioResultSteps()).hasSize(2);
    assertThat(scenarioResult.name()).isEqualTo(ScenarioId.OPEN_EGK.value());
    assertThat(scenarioResult.scenarioResultSteps().get(0).is(StepId.SELECT_MASTER_FILE)).isTrue();
    assertThat(scenarioResult.scenarioResultSteps().get(1).is(StepId.READ_VERSION)).isTrue();
    assertThat(scenarioResult.scenarioResultSteps().get(0).statusWord()).isEqualTo("9000");
    assertThat(scenarioResult.scenarioResultSteps().get(0).data()).isEmpty();
    assertThat(scenarioResult.scenarioResultSteps().get(1).statusWord()).isEqualTo("cdef");
    assertThat(scenarioResult.scenarioResultSteps().get(1).data()).isNotEmpty();
  }

  @Test
  void createScenarioResultWithExtraStepsThrowsException() {
    // given
    final var scenario =
        new Scenario(
            ScenarioId.OPEN_EGK,
            List.of(
                new StepDefinition(StepId.SELECT_MASTER_FILE),
                new StepDefinition(StepId.READ_VERSION)));
    final var responses = List.of("9000");

    // when / then
    assertThrows(ValidationException.class, () -> sut.create(scenario, responses));
  }

  @Test
  void createScenarioResultWithExtraResponseStepsThrowsException() {
    // given
    final var scenario =
        new Scenario(ScenarioId.OPEN_EGK, List.of(new StepDefinition(StepId.SELECT_MASTER_FILE)));
    final var responses = List.of("9000", "abcdef", "6A88");

    // when / then
    assertThrows(ValidationException.class, () -> sut.create(scenario, responses));
  }
}
