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

package de.gematik.refpopp.popp_server.scenario.contactbased;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioId;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContactBasedScenariosProviderTest {

  @Test
  void getNextScenarioReturnsConfiguredNextScenario() {
    final var provider = new ContactBasedScenariosProvider();
    final var currentScenario = provider.getScenarios().getFirst();

    final var nextScenario = provider.getNextScenario(currentScenario);

    assertThat(nextScenario)
        .contains(Scenario.of(ScenarioId.TRUSTED_CHANNEL_STEP_1, StepId.SELECT_PRIVATE_KEY));
  }

  @Test
  void getNextScenarioReturnsEmptyWhenCurrentScenarioIsLast() {
    final var provider = new ContactBasedScenariosProvider();
    final var lastScenario = provider.getScenarios().getLast();

    assertThat(provider.getNextScenario(lastScenario)).isEmpty();
  }

  @Test
  void addAdditionalStepsAppendsStepsInOrder() {
    final var staticStep = new StepDefinition(StepId.READ_SUB_CA_CV_CERTIFICATE);
    final var dynamicStep = new StepDefinition(StepId.MSE_APDU, "car".getBytes());
    final var scenario = new Scenario(ScenarioId.READ_CVC, List.of(staticStep));

    final var enrichedScenario = scenario.addAdditionalSteps(List.of(dynamicStep));

    assertThat(enrichedScenario.stepDefinitions()).containsExactly(staticStep, dynamicStep);
  }

  @Test
  void scenarioEqualityIsBasedOnScenarioId() {
    final var scenarioWithStaticStep =
        new Scenario(
            ScenarioId.READ_CVC, List.of(new StepDefinition(StepId.READ_SUB_CA_CV_CERTIFICATE)));
    final var scenarioWithDifferentSteps =
        new Scenario(
            ScenarioId.READ_CVC,
            List.of(new StepDefinition(StepId.READ_END_ENTITY_CV_CERTIFICATE)));

    assertThat(scenarioWithStaticStep)
        .isEqualTo(scenarioWithDifferentSteps)
        .hasSameHashCodeAs(scenarioWithDifferentSteps);
  }

  @Test
  void stepDefinitionUsesStepDefaults() {
    final var step = new StepDefinition(StepId.SELECT_MASTER_FILE);

    assertThat(step.name()).isEqualTo(StepId.SELECT_MASTER_FILE.value());
    assertThat(step.expectedStatusWords())
        .isEqualTo(StepId.SELECT_MASTER_FILE.expectedStatusWords());
  }
}
