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

package de.gematik.refpopp.popp_server.scenario.common.provider;

import de.gematik.poppcommons.api.messages.PoPPMessage;
import de.gematik.poppcommons.api.messages.TokenMessage;
import de.gematik.refpopp.popp_server.communication.ClientCommunicationService;
import de.gematik.refpopp.popp_server.handler.SessionCommunication;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioMessageFactory;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioTransitionService;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
public abstract class AbstractScenarioProcessingService {

  private final SessionAccessor sessionAccessor;
  private final ScenarioMessageFactory scenarioMessageFactory;
  private final ClientCommunicationService clientCommunicationService;
  private final ScenarioTransitionService scenarioTransitionService;

  @Value("${scenario-vars.time-span:5000}")
  private int timeSpan;

  protected AbstractScenarioProcessingService(
      final SessionAccessor sessionAccessor,
      final ScenarioMessageFactory scenarioMessageFactory,
      final ClientCommunicationService clientCommunicationService,
      final ScenarioTransitionService scenarioTransitionService) {
    this.sessionAccessor = sessionAccessor;
    this.scenarioMessageFactory = scenarioMessageFactory;
    this.clientCommunicationService = clientCommunicationService;
    this.scenarioTransitionService = scenarioTransitionService;
  }

  public abstract CommunicationMode getSupportedCommunicationMode();

  public abstract boolean isLastScenario(final Scenario currentScenario);

  public abstract void processScenario(
      final SessionCommunication session,
      final Scenario lastScenarioSentToClient,
      final CardScenarioProvider cardScenarioProvider);

  public void createAndSendMessage(final SessionCommunication session, final Scenario scenario) {
    final var clientSessionId = getClientSessionId(session.getSessionId());
    final var sequenceCounter = getSequenceCounter(session.getSessionId());
    final var currentTimeSpan = determineTimeSpan(scenario);
    final var scenarioMessage =
        createScenarioMessage(
            scenario, clientSessionId, sequenceCounter, currentTimeSpan, session.getSessionId());
    storeSequenceCounterInSession(session, sequenceCounter + 1);
    log.info("| {} Prepared scenario {} for the client", session.getSessionId(), scenario.name());
    sendMessage(scenarioMessage, session);
  }

  protected Scenario getNextScenario(
      final String sessionId,
      final Scenario currentScenario,
      final CardScenarioProvider cardScenarioProvider) {
    return scenarioTransitionService.getNextScenario(
        sessionId, currentScenario, cardScenarioProvider);
  }

  protected void processLastScenario(final SessionCommunication session) {
    log.debug("| Entering processLastScenario()");
    final var poppToken = getPoppToken(session.getSessionId());
    final var tokenMessage = new TokenMessage(poppToken, "pn");
    sendMessage(tokenMessage, session);
    closeSession(session);
    log.debug("| Exiting processLastScenario()");
  }

  protected Integer getSequenceCounter(final String sessionId) {
    return sessionAccessor.getSequenceCounter(sessionId);
  }

  protected void sendMessage(
      final PoPPMessage poPPMessage, final SessionCommunication sessionCommunication) {
    clientCommunicationService.sendMessage(poPPMessage, sessionCommunication);
  }

  protected String getClientSessionId(final String sessionId) {
    return sessionAccessor.getClientSessionId(sessionId);
  }

  protected void storeSequenceCounterInSession(
      final SessionCommunication session, final int sequenceCounter) {
    sessionAccessor.storeScenarioCounter(session.getSessionId(), sequenceCounter);
  }

  protected String getPoppToken(final String sessionId) {
    return sessionAccessor.getPoppToken(sessionId);
  }

  protected void closeSession(final SessionCommunication sessionCommunication) {
    sessionCommunication.closeSession();
  }

  protected PoPPMessage createScenarioMessage(
      final Scenario nextScenario,
      final String clientSessionId,
      final int sequenceCounter,
      final int timeSpan,
      final String sessionId) {
    return scenarioMessageFactory.create(
        nextScenario, clientSessionId, sequenceCounter, timeSpan, sessionId);
  }

  private int determineTimeSpan(final Scenario scenario) {
    int currentTimeSpan = this.timeSpan;
    if (isLastScenario(scenario)) {
      currentTimeSpan = 0;
    }
    return currentTimeSpan;
  }
}
