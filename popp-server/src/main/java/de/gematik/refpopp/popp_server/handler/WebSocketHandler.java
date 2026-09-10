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

import de.gematik.poppcommons.api.enums.BdeErrorCode;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.poppcommons.api.messages.ClientSessionScopedMessage;
import de.gematik.poppcommons.api.messages.ErrorMessage;
import de.gematik.poppcommons.api.messages.PoPPMessage;
import de.gematik.refpopp.popp_server.communication.WebSocketSessionCommunication;
import de.gematik.refpopp.popp_server.scenario.common.orchestrator.MessageOrchestrator;
import de.gematik.refpopp.popp_server.scenario.common.provider.CardScenarioProvider;
import de.gematik.refpopp.popp_server.scenario.common.token.UserInfo;
import de.gematik.refpopp.popp_server.sessionmanagement.LogicalSessionId;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class WebSocketHandler extends AbstractWebSocketHandler {

  private static final String HEADER_ZETA_USER_INFO = "zeta-user-info";
  private final SessionContainer sessionContainer;
  private final MessageOrchestrator egkMessageOrchestrator;
  private final ObjectMapper mapper;
  private final CardScenarioProvider scenarioProvider;

  /**
   * Decorated, thread-safe view per connection. Incoming messages are processed on a worker pool so
   * that independent (parallel) requests multiplexed over one connection do not block each other;
   * {@link ConcurrentWebSocketSessionDecorator} serializes the concurrent outbound sends.
   */
  private final Map<String, WebSocketSession> connections = new ConcurrentHashMap<>();

  private final ExecutorService messageExecutor;
  private final int sendTimeLimitMs;
  private final int sendBufferSizeLimit;

  public WebSocketHandler(
      final SessionContainer sessionContainer,
      final MessageOrchestrator egkMessageOrchestrator,
      final ObjectMapper mapper,
      final CardScenarioProvider scenarioProvider,
      final int messageProcessingPoolSize,
      final int sendTimeLimitMs,
      final int sendBufferSizeLimit) {
    this.sessionContainer = sessionContainer;
    this.egkMessageOrchestrator = egkMessageOrchestrator;
    this.mapper = mapper;
    this.scenarioProvider = scenarioProvider;
    this.sendTimeLimitMs = sendTimeLimitMs;
    this.sendBufferSizeLimit = sendBufferSizeLimit;
    this.messageExecutor = Executors.newFixedThreadPool(messageProcessingPoolSize);
  }

  /** Stops the message worker pool. Wired as the bean destroy method. */
  public void shutdown() {
    messageExecutor.shutdown();
    try {
      if (!messageExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        messageExecutor.shutdownNow();
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      messageExecutor.shutdownNow();
    }
  }

  @Override
  public void afterConnectionEstablished(final WebSocketSession session) {
    log.info("| {} Connection to server established", session.getId());
    connections.put(session.getId(), decorate(session));
    extractUserInfo(session.getHandshakeHeaders())
        .flatMap(this::parseUserInfo)
        .ifPresent(
            userInfo ->
                sessionContainer.storeSessionData(
                    session.getId(), SessionContainer.SessionStorageKey.ZETA_USER_INFO, userInfo));
    storeFirstScenarioInSession(session);
  }

  @Override
  protected void handleTextMessage(final WebSocketSession session, final TextMessage message) {
    log.debug("| Entering handleTextMessage()");
    final var payload = message.getPayload();
    log.info("| {} Received message from client {}", session.getId(), payload);

    final var connection = connections.computeIfAbsent(session.getId(), id -> decorate(session));
    messageExecutor.execute(() -> processMessage(connection, payload));
    log.debug("| Exiting handleTextMessage()");
  }

  private void processMessage(final WebSocketSession connection, final String payload) {
    log.debug("| Entering processMessage()");
    final SessionCommunication transportCommunication =
        new WebSocketSessionCommunication(connection, mapper);
    try {
      final var poppMessage = mapper.readValue(payload, PoPPMessage.class);
      final var requestCommunication =
          resolveRequestCommunication(transportCommunication, poppMessage);
      egkMessageOrchestrator.orchestrate(poppMessage, requestCommunication);
    } catch (final ScenarioException e) {
      handleScenarioException(transportCommunication, e);
    } catch (final JacksonException e) {
      handleScenarioException(
          transportCommunication,
          new ScenarioException(
              connection.getId(), "Error while processing JSON", BdeErrorCode.INVALID_MESSAGE));
    } catch (final RuntimeException e) {
      log.error("| Unexpected error while processing message", e);
      handleScenarioException(
          transportCommunication,
          new ScenarioException(
              connection.getId(), e.getMessage(), BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR));
    }
    log.debug("| Exiting processMessage()");
  }

  private WebSocketSession decorate(final WebSocketSession session) {
    return new ConcurrentWebSocketSessionDecorator(session, sendTimeLimitMs, sendBufferSizeLimit);
  }

  /**
   * Derives a request scoped communication (with a logical session id) when the message carries a
   * clientSessionId, so that the state of parallel token requests over the same connection is
   * isolated. Messages without a clientSessionId keep operating on the transport session.
   */
  private SessionCommunication resolveRequestCommunication(
      final SessionCommunication transportCommunication, final PoPPMessage message) {
    if (message instanceof final ClientSessionScopedMessage scoped
        && scoped.getClientSessionId() != null) {
      final var logicalSessionId =
          LogicalSessionId.of(
              transportCommunication.getTransportSessionId(), scoped.getClientSessionId());
      return new RequestScopedSessionCommunication(transportCommunication, logicalSessionId);
    }
    return transportCommunication;
  }

  @Override
  public void afterConnectionClosed(
      final WebSocketSession session, final @NonNull CloseStatus status) {
    log.info("| {} Connection closed: {}", session.getId(), status);
    connections.remove(session.getId());
    sessionContainer.clearConnection(session.getId());
  }

  private void storeFirstScenarioInSession(final WebSocketSession session) {
    final var firstScenario = scenarioProvider.getScenarios().getFirst();
    sessionContainer.storeScenario(session.getId(), firstScenario);
  }

  private void handleScenarioException(
      final SessionCommunication transportCommunication, final ScenarioException e) {
    log.error("Scenario exception", e);
    final var clientSessionId = LogicalSessionId.clientSessionIdOf(e.getSessionId());
    final var errorMessage =
        ErrorMessage.builder()
            .clientSessionId(clientSessionId)
            .errorDetail("SessionId: " + e.getSessionId() + " " + e.getMessage())
            .errorCode(String.valueOf(e.getErrorCode().getBdeCode()))
            .build();
    try {
      transportCommunication.sendMessage(errorMessage);
      if (LogicalSessionId.isLogical(e.getSessionId())) {
        sessionContainer.clearRequestState(e.getSessionId());
      }
    } catch (final RuntimeException sendError) {
      log.error("| Error while sending error message", sendError);
    }
  }

  private Optional<String> extractUserInfo(final HttpHeaders headers) {
    final var encoded = headers.getFirst(HEADER_ZETA_USER_INFO);

    if (encoded == null || encoded.isBlank()) {
      return Optional.empty();
    }

    try {
      final byte[] decoded = Base64.getDecoder().decode(encoded);
      return Optional.of(new String(decoded, StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      log.warn("Invalid zeta-user-info header", e);
      return Optional.empty();
    }
  }

  private Optional<UserInfo> parseUserInfo(final String userInfoJson) {
    try {
      return Optional.of(mapper.readValue(userInfoJson, UserInfo.class));
    } catch (JacksonException e) {
      log.error("Invalid zeta-user-info JSON", e);
      return Optional.empty();
    }
  }
}
