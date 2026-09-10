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
import de.gematik.refpopp.popp_client.client.protocol.PoPPMessageHandler;
import de.gematik.refpopp.popp_client.client.session.ClientRequestContext;
import de.gematik.refpopp.popp_client.client.session.CommunicationSessionRegistry;
import de.gematik.refpopp.popp_client.client.transport.ClientMessageDispatcher;
import de.gematik.refpopp.popp_client.client.transport.ClientServerCommunicationService;
import de.gematik.refpopp.popp_client.client.transport.events.CommunicationEvent;
import de.gematik.refpopp.popp_client.client.transport.events.TextMessageReceivedEvent;
import de.gematik.refpopp.popp_client.client.transport.events.WebSocketConnectionClosedEvent;
import de.gematik.refpopp.popp_client.client.transport.events.WebSocketConnectionOpenedEvent;
import de.gematik.refpopp.popp_client.connector.session.ConnectorSessionLifecycle;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Lazy
@Slf4j
@RequiredArgsConstructor
public class CommunicationService {

  private final ObjectMapper mapper;
  private final CardCommunicationService cardCommunicationService;
  private final ClientServerCommunicationService clientServerCommunicationService;
  private final VirtualCardService virtualCardService;
  private final VirtualCardServiceFactory virtualCardServiceFactory;
  private final CommunicationSessionRegistry sessionRegistry;
  private final ConnectorSessionLifecycle connectorSessionLifecycle;
  private final PoPPMessageHandler poPPMessageHandler;
  private final ClientMessageDispatcher messageDispatcher;
  private final ReentrantLock physicalCardFlowLock = new ReentrantLock(true);

  @Value("${popp-client.token-wait-timeout-seconds:30}")
  private int tokenWaitTimeoutSeconds;

  public String startStandardCardReader(
      final CardConnectionType cardConnectionType, final String clientSessionId) {
    physicalCardFlowLock.lock();
    try {
      final var sessionId = resolveSessionId(clientSessionId);
      return startAndAwaitToken(sessionId, () -> executeStart(cardConnectionType, sessionId));
    } finally {
      physicalCardFlowLock.unlock();
    }
  }

  public String startWithConnector(CardConnectionType connectorType, String patientId) {
    final var sessionId = connectorSessionLifecycle.startSession(patientId);
    return startAndAwaitToken(sessionId, () -> executeStart(connectorType, sessionId));
  }

  public String startConnectorMock(final String clientSessionId) {
    final var sessionId = resolveSessionId(clientSessionId);
    final var context = initializeRequestContext(sessionId, CardConnectionType.UNKNOWN);
    context.setConnectorMock(true);
    return startAndAwaitToken(sessionId, () -> sendConnectorStartMessage(sessionId));
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

    final var sessionId = resolveSessionId(clientSessionId);
    final var context = initializeRequestContext(sessionId, cardConnectionType);
    context.setVirtualCard(true);
    sessionRegistry.registerVirtualCard(sessionId, selectedVirtualCardService);
    return startAndAwaitToken(sessionId, () -> sendStartMessage(cardConnectionType, sessionId));
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

    final PoPPMessage poPPMessage;
    try {
      poPPMessage = mapper.readValue(eventPayload, PoPPMessage.class);
    } catch (final JacksonException e) {
      log.error("| Error parsing message: {}", e.getMessage());
      throw new IllegalArgumentException("Error parsing message", e);
    }

    final var orderingKey =
        poPPMessage instanceof final ClientSessionScopedMessage scoped
            ? scoped.getClientSessionId()
            : null;
    messageDispatcher.dispatch(orderingKey, () -> poPPMessageHandler.handle(poPPMessage));
  }

  private void executeStart(
      final CardConnectionType cardConnectionType, final String clientSessionId) {
    initializeRequestContext(clientSessionId, cardConnectionType);
    validateConnectionCompatibility(cardConnectionType);
    sendStartMessage(cardConnectionType, clientSessionId);
  }

  private String resolveSessionId(final String sessionUUID) {
    final var sessionUUIDExists = sessionUUID != null && !sessionUUID.isEmpty();
    if (sessionUUIDExists) {
      return sessionUUID;
    }
    try {
      final var ssl = clientServerCommunicationService.getSslSession();
      if (ssl != null && ssl.getClientSessionId() != null && !ssl.getClientSessionId().isBlank()) {
        return ssl.getClientSessionId();
      }
    } catch (final Exception ignored) {
      // fall back to random
    }
    return UUID.randomUUID().toString();
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

  private void sendConnectorStartMessage(final String sessionId) {
    sendStartMessage(CardConnectionType.CONTACT_CONNECTOR, sessionId);
  }

  private ClientRequestContext initializeRequestContext(
      final String clientSessionId, final CardConnectionType cardConnectionType) {
    clientServerCommunicationService.connect(cardConnectionType);
    return sessionRegistry.registerRequestContext(clientSessionId, cardConnectionType);
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

  private String startAndAwaitToken(final String clientSessionId, final Runnable startOperation) {
    final var tokenFuture = sessionRegistry.registerTokenWaiter(clientSessionId);
    startOperation.run();
    return waitAndGetToken(tokenFuture);
  }

  private String waitAndGetToken(CompletableFuture<String> tokenFuture) {
    // The connection is deliberately no longer closed after receiving a token, so that
    // additional sequential token requests can be made over the same kept-open WebSocket
    // connection. Explicit closing is now performed via
    // clientServerCommunicationService.disconnect() by the caller or during shutdown.
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
    }
  }
}
