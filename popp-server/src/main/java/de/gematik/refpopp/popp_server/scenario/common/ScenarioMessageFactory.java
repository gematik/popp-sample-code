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

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.ConnectorScenarioMessage;
import de.gematik.poppcommons.api.messages.PoPPMessage;
import de.gematik.poppcommons.api.messages.ScenarioStep;
import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.token.JwtTokenCreator;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import org.springframework.stereotype.Component;

@Component
public class ScenarioMessageFactory {

  public static final String VERSION = "1.0.0";

  private final SessionAccessor sessionAccessor;
  private final JwtTokenCreator tokenCreator;

  public ScenarioMessageFactory(
      final SessionAccessor sessionAccessor, final JwtTokenCreator tokenCreator) {
    this.sessionAccessor = sessionAccessor;
    this.tokenCreator = tokenCreator;
  }

  public PoPPMessage create(
      final Scenario scenario,
      final String clientSessionId,
      final int sequenceCounter,
      final int timeSpan,
      final String sessionId) {
    final var standardScenarioMessage =
        createStandardScenarioMessage(scenario, clientSessionId, sequenceCounter, timeSpan);
    if (requiresConnectorMessage(sessionId)) {
      return createConnectorScenarioMessage(standardScenarioMessage, sessionId);
    }
    return standardScenarioMessage;
  }

  private ConnectorScenarioMessage createConnectorScenarioMessage(
      final StandardScenarioMessage standardScenarioMessage, final String sessionId) {
    final var connectorToken =
        tokenCreator.createConnectorToken(standardScenarioMessage, sessionId);
    return new ConnectorScenarioMessage(VERSION, connectorToken);
  }

  private StandardScenarioMessage createStandardScenarioMessage(
      final Scenario scenario,
      final String clientSessionId,
      final int sequenceCounter,
      final int timeSpan) {
    final var scenarioSteps =
        scenario.stepDefinitions().stream()
            .map(step -> new ScenarioStep(step.commandApdu(), step.expectedStatusWord()))
            .toList();

    return StandardScenarioMessage.builder()
        .version(VERSION)
        .clientSessionId(clientSessionId)
        .sequenceCounter(sequenceCounter)
        .timeSpan(timeSpan)
        .steps(scenarioSteps)
        .build();
  }

  private boolean requiresConnectorMessage(final String sessionId) {
    final var connectionType = sessionAccessor.getCardConnectionType(sessionId);
    return connectionType == CardConnectionType.CONTACTLESS_CONNECTOR
        || connectionType == CardConnectionType.CONTACT_CONNECTOR;
  }
}
