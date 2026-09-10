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

import de.gematik.poppcommons.api.messages.StartMessage;
import de.gematik.refpopp.popp_server.handler.SessionCommunication;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioTransitionService;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractScenarioProcessingService;
import de.gematik.refpopp.popp_server.scenario.common.provider.CardScenarioProvider;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioProcessingProviderStrategyService;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioProviderStrategyService;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import org.springframework.stereotype.Component;

@Component
public class StartMessageHandler implements MessageHandler<StartMessage> {

  private final ScenarioTransitionService scenarioTransitionService;
  private final SessionAccessor sessionAccessor;
  private final ScenarioProcessingProviderStrategyService scenarioProcessingProviderStrategyService;
  private final ScenarioProviderStrategyService scenarioProviderStrategyService;
  private final CardScenarioProvider firstScenarioProvider;

  public StartMessageHandler(
      final ScenarioTransitionService scenarioTransitionService,
      final SessionAccessor sessionAccessor,
      final ScenarioProcessingProviderStrategyService scenarioProcessingProviderStrategyService,
      final ScenarioProviderStrategyService scenarioProviderStrategyService,
      final CardScenarioProvider firstScenarioProvider) {
    this.scenarioTransitionService = scenarioTransitionService;
    this.sessionAccessor = sessionAccessor;
    this.scenarioProcessingProviderStrategyService = scenarioProcessingProviderStrategyService;
    this.scenarioProviderStrategyService = scenarioProviderStrategyService;
    this.firstScenarioProvider = firstScenarioProvider;
  }

  @Override
  public void handle(final StartMessage message, final SessionCommunication session) {
    final var logicalSessionId = session.getSessionId();
    sessionAccessor.copyConnectionScopedData(session.getTransportSessionId(), logicalSessionId);
    storeRelevantDataInSession(logicalSessionId, message);
    final var processingProvider = getScenarioProcessingProvider(logicalSessionId);
    final var scenario = scenarioTransitionService.getCurrentScenario(logicalSessionId);
    final var cardScenarioProvider = getCardScenarioProvider(logicalSessionId);
    processingProvider.processScenario(session, scenario, cardScenarioProvider);
  }

  @Override
  public Class<StartMessage> getMessageType() {
    return StartMessage.class;
  }

  private CardScenarioProvider getCardScenarioProvider(final String sessionId) {
    final var communicationMode = getCommunicationMode(sessionId);
    return scenarioProviderStrategyService.getProvider(communicationMode);
  }

  private AbstractScenarioProcessingService getScenarioProcessingProvider(final String sessionId) {
    final var communicationMode = getCommunicationMode(sessionId);
    return scenarioProcessingProviderStrategyService.getProvider(communicationMode);
  }

  private CommunicationMode getCommunicationMode(final String sessionId) {
    return sessionAccessor.getCommunicationModeOrDefaultValue(sessionId);
  }

  private void storeRelevantDataInSession(final String sessionId, final StartMessage startMessage) {
    // The first scenario is initialized per token request (no longer only
    // once during connection establishment). This allows another sequential token request to be
    // started
    // over the same open WebSocket connection after the previous request state has been reset.
    storeFirstScenarioInSession(sessionId);
    sessionAccessor.storeCardConnectionType(sessionId, startMessage.getCardConnectionType());
    sessionAccessor.storeClientSessionId(sessionId, startMessage.getClientSessionId());
    storeInitialSequenceCounterInSession(sessionId);
  }

  private void storeFirstScenarioInSession(final String sessionId) {
    sessionAccessor.storeScenario(sessionId, firstScenarioProvider.getScenarios().getFirst());
  }

  private void storeInitialSequenceCounterInSession(final String sessionId) {
    sessionAccessor.storeSequenceCounter(sessionId, 0);
  }
}
