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

package de.gematik.refpopp.popp_server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.poppcommons.api.messages.ErrorMessage;
import de.gematik.poppcommons.api.messages.PoPPMessage;
import de.gematik.refpopp.popp_server.communication.WebSocketSessionCommunication;
import de.gematik.refpopp.popp_server.scenario.common.orchestrator.MessageOrchestrator;
import de.gematik.refpopp.popp_server.scenario.common.provider.CardScenarioProvider;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Slf4j
public class WebSocketHandler extends AbstractWebSocketHandler {

  private final SessionContainer sessionContainer;
  private final MessageOrchestrator egkMessageOrchestrator;
  private final ObjectMapper mapper;
  private final CardScenarioProvider scenarioProvider;

  public WebSocketHandler(
      final SessionContainer sessionContainer,
      final MessageOrchestrator egkMessageOrchestrator,
      final ObjectMapper mapper,
      final CardScenarioProvider scenarioProvider) {
    this.sessionContainer = sessionContainer;
    this.egkMessageOrchestrator = egkMessageOrchestrator;
    this.mapper = mapper;
    this.scenarioProvider = scenarioProvider;
  }

  @Override
  public void afterConnectionEstablished(@NonNull final WebSocketSession session) {
    log.info("| {} Connection to server established", session.getId());
    storeFirstScenarioInSession(session);
  }

  @Override
  protected void handleTextMessage(
      @NonNull final WebSocketSession session, final TextMessage message) {
    log.debug("| Entering handleTextMessage()");
    final var payload = message.getPayload();

    log.info("| {} Received message from client {}", session.getId(), payload);
    final SessionCommunication sessionCommunication = new WebSocketSessionCommunication(session);

    try {
      final var poppMessage = mapper.readValue(payload, PoPPMessage.class);
      egkMessageOrchestrator.orchestrate(poppMessage, sessionCommunication);
    } catch (final ScenarioException e) {
      handleScenarioException(session, e);
    } catch (final JsonProcessingException e) {
      handleScenarioException(
          session,
          new ScenarioException(session.getId(), "Error while processing JSON", "errorCode"));
    }
    log.debug("| Exiting handleTextMessage()");
  }

  @Override
  public void afterConnectionClosed(
      @NonNull final WebSocketSession session, @NonNull final CloseStatus status) {
    log.info("| {} Connection closed: {}", session.getId(), status);
    sessionContainer.clearSession(session.getId());
  }

  private void storeFirstScenarioInSession(final WebSocketSession session) {
    final var firstScenario = scenarioProvider.getScenarios().getFirst();
    sessionContainer.storeScenario(session.getId(), firstScenario);
  }

  private void handleScenarioException(final WebSocketSession session, final ScenarioException e) {
    log.error("Scenario exception", e);
    final var errorMessage =
        ErrorMessage.builder()
            .errorDetail("SessionId: " + e.getSessionId() + " " + e.getMessage())
            .errorCode(e.getErrorCode())
            .build();
    try {
      session.sendMessage(new TextMessage(mapper.writeValueAsString(errorMessage)));
      session.close();
    } catch (final IOException ioException) {
      log.error("| Error while sending error message", ioException);
    }
  }
}
