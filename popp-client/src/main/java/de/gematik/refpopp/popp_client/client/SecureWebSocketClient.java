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

import de.gematik.refpopp.popp_client.client.events.TextMessageReceivedEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketCommunicationErrorEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketConnectionClosedEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketConnectionOpenedEvent;
import de.gematik.zeta.sdk.WsClientExtension;
import de.gematik.zeta.sdk.ZetaSdkClient;
import io.ktor.client.plugins.logging.LogLevel;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import kotlin.Unit;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("prototype")
@Slf4j
public class SecureWebSocketClient {
  private final CommunicationEventPublisher eventPublisher;
  private final boolean disableServerValidation;
  private final LogLevel zetaHttpLogLevel;
  private final URI serverUri;
  private final ZetaSdkClient zetaSdk;
  private final ExecutorService pool;
  private CountDownLatch sessionReady;
  private final AtomicReference<WsClientExtension.WsSession> session = new AtomicReference<>();
  private final AtomicReference<Throwable> connectError = new AtomicReference<>();
  private final Map<String, Object> sessionMetadata;
  private final WsClientWrapper wsClientWrapper;

  @Autowired
  public SecureWebSocketClient(
      @Value("${popp-server.url}") final URI serverUri,
      final CommunicationEventPublisher eventPublisher,
      @Value("${zeta.client.disableServerValidation}") final boolean disableServerValidation,
      @Value("${zeta.http-log-level:NONE}") final LogLevel zetaHttpLogLevel,
      final ZetaSdkClient zetaSdk,
      final WsClientWrapper wsClientWrapper) {
    this.serverUri = serverUri;
    this.eventPublisher = eventPublisher;
    this.pool = Executors.newFixedThreadPool(1);
    this.sessionMetadata = new ConcurrentHashMap<>();
    this.disableServerValidation = disableServerValidation;
    this.zetaSdk = zetaSdk;
    this.zetaHttpLogLevel = zetaHttpLogLevel;
    this.wsClientWrapper = wsClientWrapper;
  }

  public void close() {
    log.info("| Closing SecureWebSocketClient");
    final var currentSession = session.getAndSet(null);
    if (currentSession != null) {
      try {
        currentSession.close();
      } catch (final Exception e) {
        log.warn("| Could not close WebSocket session", e);
      }
    }

    connectError.set(null);
    sessionMetadata.clear();
    pool.shutdownNow();
    log.info("| Finished closing SecureWebSocketClient");
  }

  @PostConstruct
  public void init() {
    log.debug("| Initializing SecureWebSocketClient");
    log.debug("| Finished initializing SecureWebSocketClient");
  }

  public void onOpen(final ServerHandshake handshake) {
    log.info("| Secure WebSocket connection opened");
    eventPublisher.publishEvent(new WebSocketConnectionOpenedEvent());
    log.debug("| Exiting onOpen()");
  }

  public void onMessage(final String message) {
    log.debug("| Entering onMessage()");
    log.info("| Received message: {}", message);
    eventPublisher.publishEvent(TextMessageReceivedEvent.builder().payload(message).build());
  }

  public void onClose(final int code, final String reason, final boolean remote) {
    log.debug("| Entering onClose()");
    log.info("| Connection closed: {}", reason);
    eventPublisher.publishEvent(new WebSocketConnectionClosedEvent());
    log.debug("| Exiting onClose()");
  }

  public void onError(final Exception ex) {
    log.debug("| Entering onError()");
    log.error(ex.getMessage());
    eventPublisher.publishEvent(WebSocketCommunicationErrorEvent.builder().error(ex).build());
    log.debug("| Exiting onError()");
  }

  public boolean isClosed() {
    return session.get() == null;
  }

  public boolean isOpen() {
    return session.get() != null;
  }

  public void connectBlocking() {
    if (isOpen()) {
      log.warn("| WebSocket is already open, skipping connect");
      return;
    }
    connectError.set(null);
    sessionReady = new CountDownLatch(1);
    pool.submit(this::openWebSocketSession);
    awaitSessionReady();
  }

  public void send(final String messageAsString) {
    final var currentSession = session.get();
    if (currentSession == null) {
      throw new IllegalStateException("WebSocket session is not open");
    }

    currentSession.sendText(messageAsString);
  }

  public Map<String, Object> getSSLSession() {
    return this.sessionMetadata;
  }

  public boolean getReadyState() {
    return isOpen();
  }

  private void openWebSocketSession() {
    try {
      wsClientWrapper.ws(
          zetaSdk,
          this.serverUri.toString(),
          builder -> {
            builder.disableServerValidation(disableServerValidation).logging(zetaHttpLogLevel);
            return Unit.INSTANCE;
          },
          new HashMap<>(),
          this::handleConnectedSession);
    } catch (final Exception e) {
      // InterruptedException can be thrown by Kotlin's runBlocking at runtime without being
      // declared (Kotlin has no checked exceptions). The static analyzer cannot detect this,
      // so we use getClass() instead of instanceof to avoid a false "always false" warning.
      if (e.getClass() == InterruptedException.class) {
        Thread.currentThread().interrupt();
        log.debug("| WebSocket session thread was interrupted, likely due to shutdown");
        sessionReady.countDown();
        return;
      }
      connectError.set(e);
      final String message = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
      log.error("WebSocket connect failed: {}", message, e);
      sessionReady.countDown();
    }
  }

  private void handleConnectedSession(final WsClientExtension.WsSession session) {
    this.session.set(session);
    log.info("| Zeta SDK WS connected");
    sessionReady.countDown();

    try {
      while (handleIncomingMessage(session.receiveNext())) {
        // keep consuming the session until it closes
      }
    } finally {
      this.session.compareAndSet(session, null);
    }
  }

  private boolean handleIncomingMessage(final WsClientExtension.WsMessage incoming) {
    if (incoming == null || incoming instanceof WsClientExtension.WsMessage.Close) {
      log.debug("| WebSocket closed.");
      return false;
    }
    if (incoming instanceof WsClientExtension.WsMessage.Text text) {
      log.debug("| WebSocket received: {}", text.getText());
      onMessage(text.getText());
      return true;
    }
    if (incoming instanceof WsClientExtension.WsMessage.Binary bin) {
      log.debug("| WebSocket received binary (bytes): {}", bin.getBytes().length);
    }
    return true;
  }

  private void awaitSessionReady() {
    try {
      if (!sessionReady.await(150, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Connection timeout");
      }
      throwIfConnectionFailed();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Thread was interrupted while waiting for WebSocket connection", e);
    }
  }

  private void throwIfConnectionFailed() {
    final Throwable cause = connectError.get();
    if (cause != null) {
      throw new IllegalStateException(
          "Failed to establish WebSocket connection: " + cause.getMessage(), cause);
    }
  }
}
