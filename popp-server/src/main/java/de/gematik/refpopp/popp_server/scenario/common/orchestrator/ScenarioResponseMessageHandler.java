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

package de.gematik.refpopp.popp_server.scenario.common.orchestrator;

import de.gematik.poppcommons.api.messages.ScenarioResponseMessage;
import de.gematik.refpopp.popp_server.handler.SessionCommunication;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioTransitionService;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractScenarioProcessingService;
import de.gematik.refpopp.popp_server.scenario.common.provider.CardScenarioProvider;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioProcessingProviderStrategyService;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioProviderStrategyService;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultManager;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Handles responses received from a client during scenario execution.
 *
 * <p>The handler records the response steps for the current scenario, selects the providers for the
 * session's communication mode, and starts processing the next scenario step.
 */
@Component
public class ScenarioResponseMessageHandler implements MessageHandler<ScenarioResponseMessage> {

  private final ScenarioResultManager scenarioResultManager;
  private final ScenarioTransitionService scenarioTransitionService;
  private final SessionAccessor sessionAccessor;
  private final ScenarioProviderStrategyService scenarioProviderStrategyService;
  private final ScenarioProcessingProviderStrategyService scenarioProcessingProviderStrategyService;

  /**
   * Creates a handler using the services required to manage scenario responses and transitions.
   *
   * @param scenarioResultManager manages the received scenario results
   * @param scenarioTransitionService provides the current scenario
   * @param sessionAccessor provides session-specific communication settings
   * @param scenarioProviderStrategyService selects the card scenario provider
   * @param scenarioProcessingProviderStrategyService selects the scenario processing provider
   */
  public ScenarioResponseMessageHandler(
      final ScenarioResultManager scenarioResultManager,
      final ScenarioTransitionService scenarioTransitionService,
      final SessionAccessor sessionAccessor,
      final ScenarioProviderStrategyService scenarioProviderStrategyService,
      final ScenarioProcessingProviderStrategyService scenarioProcessingProviderStrategyService) {
    this.scenarioResultManager = scenarioResultManager;
    this.scenarioTransitionService = scenarioTransitionService;
    this.sessionAccessor = sessionAccessor;
    this.scenarioProviderStrategyService = scenarioProviderStrategyService;
    this.scenarioProcessingProviderStrategyService = scenarioProcessingProviderStrategyService;
  }

  /**
   * Handles the response steps and starts processing the next scenario step.
   *
   * @param message the scenario response received from the client
   * @param session the session communication associated with the response
   */
  @Override
  public void handle(final ScenarioResponseMessage message, final SessionCommunication session) {
    final var responses = message.getSteps();
    final var lastScenarioSentToClient =
        scenarioTransitionService.getCurrentScenario(session.getSessionId());
    handleScenarioResult(session.getSessionId(), lastScenarioSentToClient, responses);
    final var cardScenarioProvider = getCardScenarioProvider(session.getSessionId());
    final var scenarioProcessingProvider = getScenarioProcessingProvider(session.getSessionId());
    scenarioProcessingProvider.processScenario(
        session, lastScenarioSentToClient, cardScenarioProvider);
  }

  /**
   * Returns the message type supported by this handler.
   *
   * @return {@link ScenarioResponseMessage}
   */
  @Override
  public Class<ScenarioResponseMessage> getMessageType() {
    return ScenarioResponseMessage.class;
  }

  private AbstractScenarioProcessingService getScenarioProcessingProvider(final String sessionId) {
    final var communicationMode = getCommunicationMode(sessionId);
    return scenarioProcessingProviderStrategyService.getProvider(communicationMode);
  }

  private CommunicationMode getCommunicationMode(final String sessionId) {
    return sessionAccessor.getCommunicationMode(sessionId);
  }

  private CardScenarioProvider getCardScenarioProvider(final String sessionId) {
    final var communicationMode = getCommunicationMode(sessionId);
    return scenarioProviderStrategyService.getProvider(communicationMode);
  }

  private void handleScenarioResult(
      final String sessionId, final Scenario scenario, final List<String> responses) {
    scenarioResultManager.manage(sessionId, scenario, responses);
  }
}
