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

import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.validator.StatusWordValidator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ScenarioResultManager {

  private final StatusWordValidator statusWordValidator;
  private final Map<String, ScenarioResultProcessor> scenarioResultProcessorMap;
  private final ScenarioResultFactory scenarioResultFactory;

  public ScenarioResultManager(
      final List<ScenarioResultProcessor> scenarioResultProcessors,
      final StatusWordValidator statusWordValidator,
      final ScenarioResultFactory scenarioResultFactory) {
    this.statusWordValidator = statusWordValidator;
    this.scenarioResultProcessorMap =
        scenarioResultProcessors.stream()
            .collect(Collectors.toMap(ScenarioResultProcessor::getScenarioName, p -> p));
    this.scenarioResultFactory = scenarioResultFactory;
  }

  public void manage(
      final String sessionId, final Scenario scenario, final List<String> responses) {
    final var scenarioResult = createScenarioResult(scenario, responses);
    validateStatusWords(scenario, scenarioResult);
    processScenarioResults(sessionId, scenario, scenarioResult);
  }

  private void processScenarioResults(
      final String sessionId, final Scenario scenario, final ScenarioResult scenarioResult) {
    final var resultProcessor = scenarioResultProcessorMap.get(scenario.name());
    if (resultProcessor != null) {
      resultProcessor.process(sessionId, scenarioResult);
    }
  }

  private void validateStatusWords(final Scenario scenario, final ScenarioResult scenarioResult) {
    statusWordValidator.validate(scenarioResult, scenario);
  }

  private ScenarioResult createScenarioResult(
      final Scenario scenario, final List<String> responses) {
    return scenarioResultFactory.create(scenario, responses);
  }
}
