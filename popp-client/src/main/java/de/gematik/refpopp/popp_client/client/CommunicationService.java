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

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.*;
import de.gematik.refpopp.popp_client.cardreader.card.CardCommunicationService;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardService;
import de.gematik.refpopp.popp_client.cardreader.card.events.PaceInitializationCompleteEvent;
import de.gematik.refpopp.popp_client.client.events.CommunicationEvent;
import de.gematik.refpopp.popp_client.client.events.TextMessageReceivedEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketConnectionClosedEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketConnectionOpenedEvent;
import de.gematik.refpopp.popp_client.connector.ConnectorCommunicationServiceWrapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import javax.net.ssl.SSLSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Lazy
@Slf4j
@RequiredArgsConstructor
public class CommunicationService {

  private static final String VIRTUAL_CARD = "virtualCard";
  public static final String CARD_CONNECTION_TYPE = "cardConnectionType";
  public static final String CLIENT_SESSION_ID = "clientSessionId";
  private final ObjectMapper mapper;
  private final CardCommunicationService cardCommunicationService;
  private final ClientServerCommunicationService clientServerCommunicationService;
  private final ConnectorCommunicationServiceWrapper connectorCommunicationServiceWrapper;
  private final VirtualCardService virtualCardService;

  private CardConnectionType pendingCardConnectionType;
  private String pendingClientSessionId;
  private final Map<String, CompletableFuture<String>> tokenQueue = new ConcurrentHashMap<>();

  public String start(final CardConnectionType cardConnectionType, final String clientSessionId) {
    if (isContactlessConnection(cardConnectionType)) {
      log.info("| PACE not yet completed, waiting for initialization...");
      pendingCardConnectionType = cardConnectionType;
      pendingClientSessionId = clientSessionId;
      return null;
    }
    final var sessionId = resolveSessionId(clientSessionId, cardConnectionType);
    CompletableFuture<String> tokenFuture = new CompletableFuture<>();
    tokenQueue.put(sessionId, tokenFuture);
    executeStart(cardConnectionType, sessionId);

    return waitAndGetToken(tokenFuture);
  }

  private String waitAndGetToken(CompletableFuture<String> tokenFuture) {
    try {
      return tokenFuture.get(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // ✓ restore interrupt flag
      throw new RuntimeException("Thread was interrupted while waiting for token", e);
    } catch (TimeoutException e) {
      throw new RuntimeException("Token retrieval timed out", e);
    } catch (ExecutionException e) {
      throw new RuntimeException("Error while retrieving token", e);
    }
  }

  private void executeStart(
      final CardConnectionType cardConnectionType, final String clientSessionId) {
    validateConnectionCompatibility(cardConnectionType);
    clientServerCommunicationService.connect();
    final var sslSession = clientServerCommunicationService.getSSLSession();
    sslSession.putValue(CARD_CONNECTION_TYPE, cardConnectionType);
    putSessionIdIntoSSLSession(clientSessionId);
    sendStartMessage(cardConnectionType, clientSessionId);
  }

  public String startConnectorMock(final String clientSessionId) {
    clientServerCommunicationService.connect();
    final var sslSession = clientServerCommunicationService.getSSLSession();
    sslSession.putValue(ConnectorCommunicationServiceWrapper.CONNECTOR_MOCK, true);
    final var sessionId =
        (clientSessionId != null && !clientSessionId.isBlank())
            ? clientSessionId
            : UUID.randomUUID().toString();

    CompletableFuture<String> tokenFuture = new CompletableFuture<>();
    tokenQueue.put(sessionId, tokenFuture);
    sendStartMessageWithSessionId(CardConnectionType.CONTACT_CONNECTOR, sessionId);

    return waitAndGetToken(tokenFuture);
  }

  public String startVirtualCard(
      final CardConnectionType cardConnectionType, final String clientSessionId) {
    log.info("| Using virtual card");

    clientServerCommunicationService.connect();
    final var sslSession = clientServerCommunicationService.getSSLSession();
    sslSession.putValue(CARD_CONNECTION_TYPE, cardConnectionType);
    sslSession.putValue(VIRTUAL_CARD, true);

    final var sessionId = resolveSessionId(clientSessionId, cardConnectionType);
    putSessionIdIntoSSLSession(sessionId);

    CompletableFuture<String> tokenFuture = new CompletableFuture<>();
    tokenQueue.put(sessionId, tokenFuture);
    sendStartMessage(cardConnectionType, sessionId);

    return waitAndGetToken(tokenFuture);
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
    } catch (final JacksonException e) {
      log.error("Error parsing message: {}", e.getMessage());
      throw new IllegalArgumentException("Error parsing message", e);
    }
  }

  private void putSessionIdIntoSSLSession(final String clientSessionId) {
    final SSLSession sslSession = clientServerCommunicationService.getSSLSession();
    sslSession.putValue(CLIENT_SESSION_ID, clientSessionId);
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
    final var isConnectorMock =
        sslSession.getValue(ConnectorCommunicationServiceWrapper.CONNECTOR_MOCK);
    if (Boolean.TRUE.equals(isConnectorMock)) {
      useStandardTerminalAsMock(signedScenario);
    } else {
      final var responses = connectorCommunicationServiceWrapper.secureSendApdu(signedScenario);
      sendScenarioResponseMessage(responses);
    }
  }

  private void useStandardTerminalAsMock(final String signedScenario) {
    final String[] tokenParts = signedScenario.split("\\.");
    if (tokenParts.length != 3) {
      throw new IllegalArgumentException("| Invalid token format");
    }

    final var payloadJson =
        new String(Base64.getUrlDecoder().decode(tokenParts[1]), StandardCharsets.UTF_8);
    final var claims = mapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
    final var message = mapper.convertValue(claims.get("message"), StandardScenarioMessage.class);
    handleStandardScenarioMessage(message);
  }

  private void handleStandardScenarioMessage(
      final StandardScenarioMessage standardScenarioMessage) {
    final var steps = standardScenarioMessage.getSteps();

    final var sslSession = clientServerCommunicationService.getSSLSession();
    final var isVirtualCard = sslSession.getValue(VIRTUAL_CARD);
    List<String> responses;
    if (Boolean.TRUE.equals(isVirtualCard)) {
      if (virtualCardService.isConfigured()) {
        responses = virtualCardService.process(steps);
      } else {
        throw new IllegalStateException("No image file configured for virtual card.");
      }
    } else {
      responses = cardCommunicationService.process(steps);
    }
    sendScenarioResponseMessage(responses);
  }

  private void sendScenarioResponseMessage(final List<String> responses) {
    final var responseMessage = new ScenarioResponseMessage(responses);
    clientServerCommunicationService.sendMessage(responseMessage);
  }

  private void handleTokenMessage(final TokenMessage tokenMessage) {
    log.info("| Received PoPP token: {}", tokenMessage.getToken());
    final var clientSessionId =
        (String) clientServerCommunicationService.getSSLSession().getValue(CLIENT_SESSION_ID);
    log.info("| ClientSessionId: {}", clientSessionId);
    CompletableFuture<String> tokenFuture = tokenQueue.get(clientSessionId);
    if (tokenFuture != null) {
      tokenFuture.complete(tokenMessage.getToken());
      tokenQueue.remove(clientSessionId);
    } else {
      log.warn("| No token future found for clientSessionId {}", clientSessionId);
    }
    stopConnectorSessionIfRequired();
  }

  private void stopConnectorSessionIfRequired() {
    final var sslSession = clientServerCommunicationService.getSSLSession();
    final var cardConnectionType = (CardConnectionType) sslSession.getValue(CARD_CONNECTION_TYPE);
    if (CardConnectionType.CONTACT_CONNECTOR.equals(cardConnectionType)) {
      final var clientSessionId = (String) sslSession.getValue(CLIENT_SESSION_ID);
      try {
        connectorCommunicationServiceWrapper.stopCardSession(clientSessionId);
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
      if (cardCommunicationService.getSecureChannel().isPresent()) {
        throw new IllegalStateException("Contact connection requested but card is contactless.");
      }
    } else {
      if (cardCommunicationService.getSecureChannel().isEmpty()) {
        throw new IllegalStateException(
            "Contactless connection requested but card is contact-based.");
      }
    }
  }

  private void sendStartMessageWithSessionId(
      final CardConnectionType cardConnectionType, final String sessionId) {
    final var sslSession = clientServerCommunicationService.getSSLSession();
    sslSession.putValue(CLIENT_SESSION_ID, sessionId);

    sendStartMessage(cardConnectionType, sessionId);
  }

  private String resolveSessionId(
      final String sessionUUID, final CardConnectionType cardConnectionType) {
    if (cardConnectionType.equals(CardConnectionType.CONTACT_CONNECTOR)) {
      final var connectorSessionId =
          connectorCommunicationServiceWrapper.startCardSession(
              connectorCommunicationServiceWrapper.getConnectedEgkCard());
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
