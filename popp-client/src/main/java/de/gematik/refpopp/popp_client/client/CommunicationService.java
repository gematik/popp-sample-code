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
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardServiceFactory;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardSessionState;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
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
  private final VirtualCardServiceFactory virtualCardServiceFactory;

  private final Map<String, CompletableFuture<String>> tokenQueue = new ConcurrentHashMap<>();
  private final Map<String, VirtualCardService> virtualCardServicesBySession =
      new ConcurrentHashMap<>();
  private final Map<String, VirtualCardSessionState> virtualCardSessionStatesBySession =
      new ConcurrentHashMap<>();

  @Value("${popp-client.token-wait-timeout-seconds:5}")
  private int tokenWaitTimeoutSeconds;

  public String start(final CardConnectionType cardConnectionType, final String clientSessionId) {
    final var sessionId = resolveSessionId(clientSessionId, cardConnectionType);
    CompletableFuture<String> tokenFuture = new CompletableFuture<>();
    tokenQueue.put(sessionId, tokenFuture);
    executeStart(cardConnectionType, sessionId);

    return waitAndGetToken(tokenFuture);
  }

  private String waitAndGetToken(CompletableFuture<String> tokenFuture) {
    try {
      return tokenFuture.get(tokenWaitTimeoutSeconds, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TokenRetrievalException("Thread was interrupted while waiting for token", e);
    } catch (TimeoutException e) {
      throw new TokenRetrievalException("Token retrieval timed out", e);
    } catch (ExecutionException e) {
      final var cause = e.getCause();
      throw new TokenRetrievalException(
          cause == null ? "Error while retrieving token" : cause.getMessage(), cause);
    } finally {
      clientServerCommunicationService.disconnect();
    }
  }

  private void executeStart(
      final CardConnectionType cardConnectionType, final String clientSessionId) {
    clientServerCommunicationService.connect();
    final Map<String, Object> sslSession = clientServerCommunicationService.getSSLSession();
    sslSession.put(CARD_CONNECTION_TYPE, cardConnectionType);
    putSessionIdIntoSSLSession(clientSessionId);

    validateConnectionCompatibility(cardConnectionType);
    sendStartMessage(cardConnectionType, clientSessionId);
  }

  public String startConnectorMock(final String clientSessionId) {
    clientServerCommunicationService.connect();
    final Map<String, Object> sslSession = clientServerCommunicationService.getSSLSession();
    sslSession.put(ConnectorCommunicationServiceWrapper.CONNECTOR_MOCK, true);
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
      final CardConnectionType cardConnectionType, final String clientSessionId, String imageFile) {
    log.info("| Using virtual card");
    final VirtualCardService selectedVirtualCardService =
        (imageFile != null && !imageFile.isEmpty())
            ? virtualCardServiceFactory.create(imageFile)
            : virtualCardService;
    if (!selectedVirtualCardService.isConfigured()) {
      throw new IllegalArgumentException("No virtual card image configured");
    }

    clientServerCommunicationService.connect();
    final Map<String, Object> sslSession = clientServerCommunicationService.getSSLSession();
    sslSession.put(CARD_CONNECTION_TYPE, cardConnectionType);
    sslSession.put(VIRTUAL_CARD, true);

    final var sessionId = resolveSessionId(clientSessionId, cardConnectionType);
    putSessionIdIntoSSLSession(sessionId);
    virtualCardServicesBySession.put(sessionId, selectedVirtualCardService);
    virtualCardSessionStatesBySession.put(sessionId, new VirtualCardSessionState());

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
  public void handleServerEvent(final TextMessageReceivedEvent event) {
    log.debug("| Entering handleServerEvent() with event-payload {}", event.getPayload());
    final var eventPayload = event.getPayload();

    try {
      final var poPPMessage = mapper.readValue(eventPayload, PoPPMessage.class);
      handlePoPPMessage(poPPMessage);
    } catch (final JacksonException e) {
      log.error("| Error parsing message: {}", e.getMessage());
      throw new IllegalArgumentException("Error parsing message", e);
    }
  }

  private void putSessionIdIntoSSLSession(final String clientSessionId) {
    final Map<String, Object> sslSession = clientServerCommunicationService.getSSLSession();
    sslSession.put(CLIENT_SESSION_ID, clientSessionId);
  }

  private void sendStartMessage(
      final CardConnectionType cardConnectionType, final String clientSessionId) {
    final var startMessage =
        StartMessage.builder()
            .version("1.0.0")
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
      case final ErrorMessage errorMessage -> handleErrorMessage(errorMessage);
      default -> log.error("| Unknown message type: {}", poPPMessage.getType());
    }
  }

  private void handleErrorMessage(final ErrorMessage errorMessage) {
    log.error(
        "| Error message: {}, {}", errorMessage.getErrorCode(), errorMessage.getErrorDetail());
    final var clientSessionId =
        (String) clientServerCommunicationService.getSSLSession().get(CLIENT_SESSION_ID);
    if (clientSessionId == null) {
      log.warn("| No clientSessionId found for server error message");
      return;
    }
    final var tokenFuture = tokenQueue.remove(clientSessionId);
    virtualCardServicesBySession.remove(clientSessionId);
    virtualCardSessionStatesBySession.remove(clientSessionId);
    if (tokenFuture != null) {
      tokenFuture.completeExceptionally(
          new IllegalStateException(
              "Server error "
                  + errorMessage.getErrorCode()
                  + ": "
                  + errorMessage.getErrorDetail()));
    }
  }

  private void handleConnectorScenarioMessage(
      final ConnectorScenarioMessage connectorScenarioMessage) {
    final var signedScenario = connectorScenarioMessage.getSignedScenario();

    final Map<String, Object> sslSession = clientServerCommunicationService.getSSLSession();
    final Object isConnectorMock =
        sslSession.get(ConnectorCommunicationServiceWrapper.CONNECTOR_MOCK);
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
      throw new IllegalArgumentException("Invalid token format");
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

    final Map<String, Object> sslSession = clientServerCommunicationService.getSSLSession();
    final Object isVirtualCard = sslSession.get(VIRTUAL_CARD);
    List<String> responses;
    if (Boolean.TRUE.equals(isVirtualCard)) {
      final var clientSessionId = (String) sslSession.get(CLIENT_SESSION_ID);
      final var sessionVirtualCardService =
          clientSessionId == null
              ? virtualCardService
              : virtualCardServicesBySession.getOrDefault(clientSessionId, virtualCardService);
      if (sessionVirtualCardService.isConfigured()) {
        if (clientSessionId == null) {
          responses = sessionVirtualCardService.process(steps);
        } else {
          final var sessionState =
              virtualCardSessionStatesBySession.computeIfAbsent(
                  clientSessionId, ignored -> new VirtualCardSessionState());
          responses = sessionVirtualCardService.process(steps, sessionState);
        }
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
        (String) clientServerCommunicationService.getSSLSession().get(CLIENT_SESSION_ID);
    log.info("| ClientSessionId: {}", clientSessionId);
    CompletableFuture<String> tokenFuture = tokenQueue.get(clientSessionId);
    if (tokenFuture != null) {
      tokenFuture.complete(tokenMessage.getToken());
      tokenQueue.remove(clientSessionId);
      virtualCardServicesBySession.remove(clientSessionId);
      virtualCardSessionStatesBySession.remove(clientSessionId);
    } else {
      log.warn("| No token future found for clientSessionId {}", clientSessionId);
    }
    stopConnectorSessionIfRequired();
  }

  private void stopConnectorSessionIfRequired() {
    final Map<String, Object> sslSession = clientServerCommunicationService.getSSLSession();
    final var cardConnectionType = (CardConnectionType) sslSession.get(CARD_CONNECTION_TYPE);
    if (usesConnectorSession(cardConnectionType)) {
      final var clientSessionId = (String) sslSession.get(CLIENT_SESSION_ID);
      try {
        connectorCommunicationServiceWrapper.stopCardSession(clientSessionId);
      } catch (org.springframework.ws.soap.client.SoapFaultClientException exception) {
        String faultString = exception.getFaultStringOrReason();
        boolean unknownSession =
            faultString != null && faultString.contains("Unbekannte Session ID");
        if (!unknownSession) {
          throw exception;
        }
        log.info("| Session {} is already closed.", clientSessionId);
      }
    }
  }

  private void validateConnectionCompatibility(final CardConnectionType cardConnectionType) {
    if (cardCommunicationService.getCardChannel().isEmpty()
        && cardConnectionType != CardConnectionType.CONTACT_CONNECTOR) {
      throw new IllegalStateException("No card inserted.");
    } else if (cardConnectionType.equals(CardConnectionType.CONTACT_STANDARD)
        || cardConnectionType.equals(CardConnectionType.CONTACT_CONNECTOR)) {
      if (cardCommunicationService.getSecureChannel().isPresent()) {
        throw new IllegalStateException("Contact connection requested but card is contactless.");
      }
    } else if ((cardConnectionType.equals(CardConnectionType.CONTACTLESS_STANDARD)
            || cardConnectionType.equals(CardConnectionType.CONTACTLESS_CONNECTOR))
        && cardCommunicationService.getSecureChannel().isEmpty()) {
      throw new IllegalStateException(
          "Contactless connection requested but card is contact-based.");
    }
  }

  private void sendStartMessageWithSessionId(
      final CardConnectionType cardConnectionType, final String sessionId) {
    final Map<String, Object> sslSession = clientServerCommunicationService.getSSLSession();
    sslSession.put(CLIENT_SESSION_ID, sessionId);

    sendStartMessage(cardConnectionType, sessionId);
  }

  private String resolveSessionId(
      final String sessionUUID, final CardConnectionType cardConnectionType) {
    if (usesConnectorSession(cardConnectionType)) {
      return connectorCommunicationServiceWrapper.startCardSession(
          connectorCommunicationServiceWrapper.getConnectedEgkCard());
    }
    final var sessionUUIDExists = sessionUUID != null && !sessionUUID.isEmpty();
    return sessionUUIDExists ? sessionUUID : UUID.randomUUID().toString();
  }

  private boolean usesConnectorSession(final CardConnectionType cardConnectionType) {
    return CardConnectionType.CONTACT_CONNECTOR.equals(cardConnectionType)
        || CardConnectionType.CONTACTLESS_CONNECTOR.equals(cardConnectionType);
  }
}
