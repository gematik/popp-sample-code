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
import de.gematik.refpopp.popp_client.connector.session.ConnectorSessionLifecycle;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    log.debug("| Entering handlePoPPMessage() with message type: {}", poPPMessage.getType());
    switch (poPPMessage) {
      case final TokenMessage tokenMessage -> handleTokenMessage(tokenMessage);
      case final StandardScenarioMessage standardScenarioMessage ->
          handleStandardScenarioMessage(standardScenarioMessage);
      case final ConnectorScenarioMessage connectorScenarioMessage ->
          handleConnectorScenarioMessage(connectorScenarioMessage);
      case final ErrorMessage errorMessage -> handleErrorMessage(errorMessage);
      default -> log.error("| Unknown message type: {}", poPPMessage.getType());
    }
  }

  private void handleErrorMessage(final ErrorMessage errorMessage) {
    log.error(
        "| Error message: {}, {}", errorMessage.getErrorCode(), errorMessage.getErrorDetail());
    final var clientSessionId = errorMessage.getClientSessionId();
    if (clientSessionId == null) {
      log.warn("| No clientSessionId found for server error message");
      return;
    }
    sessionRegistry.failToken(
        clientSessionId,
        new IllegalStateException(
            "Server error " + errorMessage.getErrorCode() + ": " + errorMessage.getErrorDetail()));
  }

  private void handleConnectorScenarioMessage(
      final ConnectorScenarioMessage connectorScenarioMessage) {
    final var clientSessionId = connectorScenarioMessage.getClientSessionId();
    final var context = sessionRegistry.getRequestContext(clientSessionId);
    final List<String> responses =
        connectorScenarioProcessor.process(connectorScenarioMessage, context);
    sendScenarioResponseMessage(clientSessionId, responses);
  }

  private void handleStandardScenarioMessage(
      final StandardScenarioMessage standardScenarioMessage) {
    final var clientSessionId = standardScenarioMessage.getClientSessionId();
    final var context = sessionRegistry.getRequestContext(clientSessionId);
    final List<String> responses =
        standardScenarioProcessor.process(standardScenarioMessage, context);
    sendScenarioResponseMessage(clientSessionId, responses);
  }

  private void sendScenarioResponseMessage(
      final String clientSessionId, final List<String> responses) {
    final var responseMessage = new ScenarioResponseMessage(clientSessionId, responses);
    clientServerCommunicationService.sendMessage(responseMessage);
  }

  private void handleTokenMessage(final TokenMessage tokenMessage) {
    log.info("| Received PoPP token: {}", tokenMessage.getToken());
    final var clientSessionId = tokenMessage.getClientSessionId();
    log.info("| ClientSessionId: {}", clientSessionId);

    final var context =
        clientSessionId == null ? null : sessionRegistry.getRequestContext(clientSessionId);
    if (context != null) {
      connectorSessionLifecycle.stopSessionIfRequired(context);
    }

    if (clientSessionId != null
        && sessionRegistry.completeToken(clientSessionId, tokenMessage.getToken())) {
      return;
    }

    // Fallback: some servers do not echo back the client's clientSessionId (or send none at all).
    // If exactly one token request is in flight, complete it so the waiting caller is not stuck
    // until the request times out.
    if (sessionRegistry.completeSolePendingToken(tokenMessage.getToken())) {
      log.warn(
          "| Server did not echo back the clientSessionId '{}' in the token response (or sent none"
              + " at all); falling back to complete the single pending token request to prevent the"
              + " caller from hanging until timeout",
          clientSessionId);
      return;
    }

    log.warn("| No token future found for clientSessionId {}", clientSessionId);
  }
}
