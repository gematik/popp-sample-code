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

import de.gematik.poppcommons.api.enums.BdeErrorCode;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import java.util.List;
import org.springframework.stereotype.Component;

/** Finds individual steps in a scenario result. */
@Component
public class ScenarioResultFinder {

  /**
   * Finds the scenario result step with the supplied name.
   *
   * <p>Step names are compared case-insensitively.
   *
   * @param sessionId the identifier of the current session
   * @param scenarioResultSteps the scenario result steps to search
   * @param name the name of the requested step
   * @return the matching scenario result step
   * @throws ScenarioException if no step with the supplied name exists
   */
  public ScenarioResultStep find(
      final String sessionId,
      final List<ScenarioResultStep> scenarioResultSteps,
      final String name) {
    return scenarioResultSteps.stream()
        .filter(step -> step.name().equalsIgnoreCase(name))
        .findFirst()
        .orElseThrow(
            () ->
                new ScenarioException(
                    sessionId,
                    "APDU Result of " + name + " not found",
                    BdeErrorCode.UNSUPPORTED_WORKFLOW));
  }

  /**
   * Finds the scenario result step associated with the supplied step identifier.
   *
   * @param sessionId the identifier of the current session
   * @param scenarioResultSteps the scenario result steps to search
   * @param stepId the identifier of the requested step
   * @return the matching scenario result step
   * @throws ScenarioException if no step with the supplied identifier exists
   */
  public ScenarioResultStep find(
      final String sessionId,
      final List<ScenarioResultStep> scenarioResultSteps,
      final StepId stepId) {
    return find(sessionId, scenarioResultSteps, stepId.value());
  }
}
