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

package de.gematik.refpopp.popp_client.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.ConnectorScenarioMessage;
import de.gematik.poppcommons.api.messages.ErrorMessage;
import de.gematik.poppcommons.api.messages.PoPPMessage;
import de.gematik.poppcommons.api.messages.ScenarioResponseMessage;
import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.poppcommons.api.messages.StartMessage;
import de.gematik.poppcommons.api.messages.TokenMessage;
import de.gematik.refpopp.popp_client.cardreader.card.CardCommunicationService;
import de.gematik.refpopp.popp_client.cardreader.card.events.PaceInitializationCompleteEvent;
import de.gematik.refpopp.popp_client.client.events.CommunicationEvent;
import de.gematik.refpopp.popp_client.client.events.TextMessageReceivedEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketConnectionClosedEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketConnectionOpenedEvent;
import de.gematik.refpopp.popp_client.connector.ConnectorCommunicationService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@RequiredArgsConstructor
public class CommunicationService {

  private static final String CONNECTOR_MOCK = "connectorMock";
  public static final String CARD_CONNECTION_TYPE = "cardConnectionType";
  public static final String CLIENT_SESSION_ID = "clientSessionId";
  private final ObjectMapper mapper;
  private final CardCommunicationService cardCommunicationService;
  private final ClientServerCommunicationService clientServerCommunicationService;
  private final ConnectorCommunicationService connectorCommunicationService;

  private CardConnectionType pendingCardConnectionType;
  private String pendingClientSessionId;

  public void start(final CardConnectionType cardConnectionType, final String clientSessionId) {
    if (isContactlessConnection(cardConnectionType)) {
      log.info("| PACE not yet completed, waiting for initialization...");
      pendingCardConnectionType = cardConnectionType;
      pendingClientSessionId = clientSessionId;
      return;
    }

    executeStart(cardConnectionType, clientSessionId);
  }

  private void executeStart(
      final CardConnectionType cardConnectionType, final String clientSessionId) {
    validateConnectionCompatibility(cardConnectionType);
    clientServerCommunicationService.connect();
    final var sslSession = clientServerCommunicationService.getSSLSession();
    sslSession.putValue(CARD_CONNECTION_TYPE, cardConnectionType);
    resolveSessionIdAndSendMessage(cardConnectionType, clientSessionId);
  }

  public void startConnectorMock(
      final CardConnectionType cardConnectionType, final String clientSessionId) {
    clientServerCommunicationService.connect();
    final var sslSession = clientServerCommunicationService.getSSLSession();
    sslSession.putValue(CONNECTOR_MOCK, true);
    resolveSessionIdAndSendMessage(cardConnectionType, clientSessionId);
  }

  @EventListener
  public void handleConnectionEvents(final CommunicationEvent event) {
    if (event instanceof WebSocketConnectionOpenedEvent) {
      log.info("| Connected to server");
    } else if (event instanceof WebSocketConnectionClosedEvent) {
      log.info("| Disconnected from server");
    }
  }

  @EventListener
  public void handlePaceInitializationComplete(final PaceInitializationCompleteEvent event) {
    log.info("| PACE initialization completed, starting pending Communication Service");
    executeStart(pendingCardConnectionType, pendingClientSessionId);
  }

  private boolean isContactlessConnection(CardConnectionType cardConnectionType) {
    return cardConnectionType == CardConnectionType.CONTACTLESS_STANDARD
        || cardConnectionType == CardConnectionType.CONTACTLESS_CONNECTOR;
  }

  @EventListener
  public void handleServerEvent(final TextMessageReceivedEvent event) {
    log.debug("| Entering handleServerEvent() with event-payload {}", event.getPayload());
    final var eventPayload = event.getPayload();

    try {
      final var poPPMessage = mapper.readValue(eventPayload, PoPPMessage.class);
      handlePoPPMessage(poPPMessage);
    } catch (final JsonProcessingException e) {
      log.error("Error parsing message: {}", e.getMessage());
      throw new IllegalArgumentException("Error parsing message", e);
    }
  }

  private void resolveSessionIdAndSendMessage(
      final CardConnectionType cardConnectionType, final String clientSessionId) {
    final var sessionId = resolveSessionId(clientSessionId, cardConnectionType);

    final var sslSession = clientServerCommunicationService.getSSLSession();
    sslSession.putValue(CLIENT_SESSION_ID, sessionId);

    sendStartMessage(cardConnectionType, sessionId);
  }

  private void sendStartMessage(
      final CardConnectionType cardConnectionType, final String clientSessionId) {
    final var startMessage =
        StartMessage.builder()
            .version("1.0")
            .clientSessionId(clientSessionId)
            .cardConnectionType(cardConnectionType)
            .build();
    clientServerCommunicationService.sendMessage(startMessage);
  }

  private void handlePoPPMessage(final PoPPMessage poPPMessage) {
    log.debug("| Entering handlePoPPMessage() with message type: {}", poPPMessage.getType());
    switch (poPPMessage) {
      case final TokenMessage tokenMessage -> handleTokenMessage(tokenMessage);
      case final StandardScenarioMessage standardScenarioMessage ->
          handleStandardScenarioMessage(standardScenarioMessage);
      case final ConnectorScenarioMessage connectorScenarioMessage ->
          handleConnectorScenarioMessage(connectorScenarioMessage);
      case final ErrorMessage errorMessage ->
          log.error(
              "Error message: {}, {}", errorMessage.getErrorCode(), errorMessage.getErrorDetail());
      default -> log.error("Unknown message type: {}", poPPMessage.getType());
    }
  }

  private void handleConnectorScenarioMessage(
      final ConnectorScenarioMessage connectorScenarioMessage) {
    final var signedScenario = connectorScenarioMessage.getSignedScenario();

    final var sslSession = clientServerCommunicationService.getSSLSession();
    final var isConnectorMock = sslSession.getValue(CONNECTOR_MOCK);
    if (Boolean.TRUE.equals(isConnectorMock)) {
      useStandardTerminalAsMock(signedScenario);
    } else {
      final var responses = connectorCommunicationService.secureSendApdu(signedScenario);
      sendScenarioResponseMessage(responses);
    }
  }

  private void useStandardTerminalAsMock(final String signedScenario) {
    // temporary implementation until connector interface is available
    final String[] tokenParts = signedScenario.split("\\.");
    if (tokenParts.length != 3) {
      throw new IllegalArgumentException("| Invalid token format");
    }

    final var payloadJson =
        new String(Base64.getUrlDecoder().decode(tokenParts[1]), StandardCharsets.UTF_8);
    try {
      final var claims = mapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
      final var message = mapper.convertValue(claims.get("message"), StandardScenarioMessage.class);
      handleStandardScenarioMessage(message);
    } catch (final IOException e) {
      log.error("| Error parsing token: {}", e.getMessage());
    }
  }

  private void handleStandardScenarioMessage(
      final StandardScenarioMessage standardScenarioMessage) {
    final var steps = standardScenarioMessage.getSteps();
    final var responses = cardCommunicationService.process(steps);
    sendScenarioResponseMessage(responses);
  }

  private void sendScenarioResponseMessage(final List<String> responses) {
    final var responseMessage = new ScenarioResponseMessage(responses);
    clientServerCommunicationService.sendMessage(responseMessage);
  }

  private void handleTokenMessage(final TokenMessage tokenMessage) {
    log.info("| Received PoPP token: {}", tokenMessage.getToken());
    stopConnectorSessionIfRequired();
  }

  private void stopConnectorSessionIfRequired() {
    final var sslSession = clientServerCommunicationService.getSSLSession();
    final var cardConnectionType = (CardConnectionType) sslSession.getValue(CARD_CONNECTION_TYPE);
    if (CardConnectionType.CONTACT_CONNECTOR.equals(cardConnectionType)) {
      final var clientSessionId = (String) sslSession.getValue(CLIENT_SESSION_ID);
      try {
        connectorCommunicationService.stopCardSession(clientSessionId);
      } catch (org.springframework.ws.soap.client.SoapFaultClientException exception) {
        String faultString = exception.getFaultStringOrReason();
        boolean unknownSession =
            faultString != null && faultString.contains("Unbekannte Session ID");
        if (!unknownSession) {
          throw exception;
        }
        log.info("Session {} is already closed.", clientSessionId);
      }
    }
  }

  private void validateConnectionCompatibility(final CardConnectionType cardConnectionType) {
    if (cardConnectionType.equals(CardConnectionType.CONTACT_STANDARD)
        || cardConnectionType.equals(CardConnectionType.CONTACT_CONNECTOR)) {
      if (cardCommunicationService.getTrustedChannel().isPresent()) {
        throw new IllegalStateException("Contact connection requested but card is contactless.");
      }
    } else {
      if (cardCommunicationService.getTrustedChannel().isEmpty()) {
        throw new IllegalStateException(
            "Contactless connection requested but card is contact-based.");
      }
    }
  }

  private String resolveSessionId(
      final String sessionUUID, final CardConnectionType cardConnectionType) {
    if (cardConnectionType.equals(CardConnectionType.CONTACT_CONNECTOR)) {
      final var connectorSessionId =
          connectorCommunicationService.startCardSession(
              connectorCommunicationService.getConnectedEgkCard());
      return isValidSessionId(sessionUUID) ? sessionUUID : connectorSessionId;
    }
    return sessionUUID != null ? sessionUUID : UUID.randomUUID().toString();
  }

  private static boolean isValidSessionId(final String sessionUUID) {
    if (!StringUtils.hasLength(sessionUUID)) {
      return false;
    }
    try {
      UUID.fromString(sessionUUID);
      return true;
    } catch (final IllegalArgumentException e) {
      log.warn("| Not valid clientSessionId {}.", e.getLocalizedMessage());
      return false;
    }
  }
}
