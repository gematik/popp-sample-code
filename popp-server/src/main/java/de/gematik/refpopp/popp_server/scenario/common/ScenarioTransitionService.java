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

package de.gematik.refpopp.popp_server.scenario.common;

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.CardScenarioProvider;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import org.springframework.stereotype.Service;

@Service
public class ScenarioTransitionService {

  private final SessionContainer sessionContainer;

  public ScenarioTransitionService(final SessionContainer sessionContainer) {
    this.sessionContainer = sessionContainer;
  }

  public Scenario getCurrentScenario(final String sessionId) {
    return sessionContainer
        .retrieveScenario(sessionId)
        .orElseThrow(() -> new ScenarioException(sessionId, "No scenario found", "errorCode"));
  }

  public Scenario getNextScenario(
      final String sessionId,
      final Scenario currentScenario,
      final CardScenarioProvider scenarioProvider) {
    return scenarioProvider
        .getNextScenario(currentScenario)
        .orElseThrow(() -> new ScenarioException(sessionId, "No next scenario found", "errorCode"));
  }
}
