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

package de.gematik.refpopp.popp_client.client.protocol;

import de.gematik.poppcommons.api.messages.ConnectorScenarioMessage;
import de.gematik.poppcommons.api.messages.ErrorMessage;
import de.gematik.poppcommons.api.messages.PoPPMessage;
import de.gematik.poppcommons.api.messages.ScenarioResponseMessage;
import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.poppcommons.api.messages.TokenMessage;
import de.gematik.refpopp.popp_client.client.session.CommunicationSessionRegistry;
import de.gematik.refpopp.popp_client.client.transport.ClientServerCommunicationService;
import de.gematik.refpopp.popp_client.client.transport.SecureWebSocketClient;
import de.gematik.refpopp.popp_client.connector.session.ConnectorSessionLifecycle;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Routes incoming PoPP protocol messages to their appropriate client-side handlers.
 *
 * <p>Scenario messages are processed through the matching card-flow processor and answered with a
 * {@link ScenarioResponseMessage}. Token and error messages complete or fail the correlated token
 * request in the session registry. Connector sessions are closed when their token flow completes.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PoPPMessageHandler {

  private final ClientServerCommunicationService clientServerCommunicationService;
  private final CommunicationSessionRegistry sessionRegistry;
  private final StandardScenarioProcessor standardScenarioProcessor;
  private final ConnectorScenarioProcessor connectorScenarioProcessor;
  private final ConnectorSessionLifecycle connectorSessionLifecycle;

  public void handle(final PoPPMessage poPPMessage) {
    handle(poPPMessage, null);
  }

  public void handle(final PoPPMessage poPPMessage, final SecureWebSocketClient client) {
    log.debug("| Entering handlePoPPMessage() with message type: {}", poPPMessage.getType());
    switch (poPPMessage) {
      case final TokenMessage tokenMessage -> handleTokenMessage(tokenMessage, client);
      case final StandardScenarioMessage standardScenarioMessage ->
          handleStandardScenarioMessage(standardScenarioMessage, client);
      case final ConnectorScenarioMessage connectorScenarioMessage ->
          handleConnectorScenarioMessage(connectorScenarioMessage, client);
      case final ErrorMessage errorMessage -> handleErrorMessage(errorMessage, client);
      default -> log.error("| Unknown message type: {}", poPPMessage.getType());
    }
  }

  private String resolveSessionId(
      final String messageSessionId, final SecureWebSocketClient client) {
    if (client == null) {
      if (messageSessionId != null && !messageSessionId.isBlank()) {
        return messageSessionId;
      }
      throw new IllegalStateException(
          "Cannot correlate server message without a WebSocket connection");
    }
    final var activeSessionId =
        sessionRegistry
            .getPendingSessionIdForConnection(client)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No pending token request on this WebSocket connection"));
    if (messageSessionId != null && !messageSessionId.equals(activeSessionId)) {
      throw new IllegalStateException(
          "Server message clientSessionId does not match the pending request on this connection");
    }
    return activeSessionId;
  }

  private void handleErrorMessage(
      final ErrorMessage errorMessage, final SecureWebSocketClient client) {
    log.error(
        "| Error message: {}, {}", errorMessage.getErrorCode(), errorMessage.getErrorDetail());
    final var clientSessionId = resolveSessionId(null, client);
    if (!sessionRegistry.failToken(
        clientSessionId,
        new IllegalStateException(
            "Server error "
                + errorMessage.getErrorCode()
                + ": "
                + errorMessage.getErrorDetail()))) {
      throw new IllegalStateException(
          "No pending token request for clientSessionId " + clientSessionId);
    }
  }

  private void handleConnectorScenarioMessage(
      final ConnectorScenarioMessage connectorScenarioMessage, final SecureWebSocketClient client) {
    final var clientSessionId = resolveSessionId(null, client);
    final var context = sessionRegistry.getRequestContext(clientSessionId);
    final List<String> responses =
        connectorScenarioProcessor.process(connectorScenarioMessage, context);
    sendScenarioResponseMessage(responses);
  }

  private void handleStandardScenarioMessage(
      final StandardScenarioMessage standardScenarioMessage, final SecureWebSocketClient client) {
    final var clientSessionId =
        resolveSessionId(standardScenarioMessage.getClientSessionId(), client);
    final var context = sessionRegistry.getRequestContext(clientSessionId);
    final List<String> responses =
        standardScenarioProcessor.process(standardScenarioMessage, context);
    sendScenarioResponseMessage(responses);
  }

  private void sendScenarioResponseMessage(final List<String> responses) {
    final var responseMessage = new ScenarioResponseMessage(responses);
    clientServerCommunicationService.sendMessage(responseMessage);
  }

  private void handleTokenMessage(
      final TokenMessage tokenMessage, final SecureWebSocketClient client) {
    final var clientSessionId = resolveSessionId(null, client);

    final var context = sessionRegistry.getRequestContext(clientSessionId);
    if (context != null) {
      try {
        connectorSessionLifecycle.stopSessionIfRequired(context);
      } catch (RuntimeException e) {
        sessionRegistry.failToken(clientSessionId, e);
        throw e;
      }
    }

    if (!sessionRegistry.completeToken(clientSessionId, tokenMessage.getToken())) {
      throw new IllegalStateException(
          "No pending token request for clientSessionId " + clientSessionId);
    }
  }
}
