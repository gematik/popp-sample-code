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

import de.gematik.poppcommons.api.exceptions.ValidationException;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StatusWordValidator {

  public void validate(final ScenarioResult scenarioResult, final Scenario scenario)
      throws ValidationException {
    final var currentScenarioSteps = scenario.stepDefinitions();
    checkStatusWord(currentScenarioSteps, scenarioResult);
  }

  private static void checkStatusWord(
      final List<StepDefinition> currentStepDefinitions, final ScenarioResult scenarioResult) {
    if (currentStepDefinitions.size() != scenarioResult.scenarioResultSteps().size()) {
      throw new ValidationException(
          "Number of expected results does not match number of received results", "errorCode");
    }
    for (int i = 0; i < currentStepDefinitions.size(); i++) {
      final StepDefinition stepDefinition = currentStepDefinitions.get(i);
      final var response = scenarioResult.scenarioResultSteps().get(i);
      final var statusWord = response.statusWord();
      if (!stepDefinition.expectedStatusWord().contains(statusWord)) {
        throw new ValidationException(
            "Expected StatusWord " + stepDefinition.expectedStatusWord() + " but got " + statusWord,
            "errorCode");
      }
    }
  }
}
