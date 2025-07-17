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

import de.gematik.poppcommons.api.exceptions.ValidationException;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.smartcardio.ResponseAPDU;
import org.springframework.stereotype.Component;

@Component
public class ScenarioResultFactory {

  public ScenarioResult create(final Scenario scenario, final List<String> responses) {
    final var scenarioSteps = scenario.stepDefinitions();
    final int minSize = Math.min(scenarioSteps.size(), responses.size());
    checkStepsSize(scenarioSteps, responses, minSize);
    final var steps = createResultSteps(responses, minSize, scenarioSteps);

    return new ScenarioResult(scenario.name(), steps);
  }

  private List<ScenarioResultStep> createResultSteps(
      final List<String> responses, final int minSize, final List<StepDefinition> stepDefinitions) {
    final List<ScenarioResultStep> steps = new ArrayList<>();

    for (int i = 0; i < minSize; i++) {
      final var responseAPDU = new ResponseAPDU(HexFormat.of().parseHex(responses.get(i)));
      final var statusWord = Integer.toHexString(responseAPDU.getSW());
      steps.add(
          new ScenarioResultStep(
              stepDefinitions.get(i).name(), statusWord, responseAPDU.getData()));
    }
    return steps;
  }

  private void checkStepsSize(
      final List<StepDefinition> currentStepDefinitions,
      final List<String> responseSteps,
      final int minSize) {
    final var sb = new StringBuilder();
    if (currentStepDefinitions.size() > responseSteps.size()) {
      for (int i = minSize; i < currentStepDefinitions.size(); i++) {
        sb.append("Extra current step at index ")
            .append(i)
            .append(": ")
            .append(currentStepDefinitions.get(i).name());
      }
    } else if (responseSteps.size() > currentStepDefinitions.size()) {
      for (int i = minSize; i < responseSteps.size(); i++) {
        sb.append("Extra ScenarioResponseStep at index ")
            .append(i)
            .append(": response ")
            .append(responseSteps.get(i));
      }
    }
    if (!sb.isEmpty()) {
      throw new ValidationException(sb.toString(), "errorCode");
    }
  }
}
