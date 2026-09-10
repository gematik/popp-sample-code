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

package de.gematik.refpopp.popp_client.client.transport;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.PoPPMessage;
import de.gematik.refpopp.popp_client.client.session.CommunicationSslSession;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class ClientServerCommunicationService {

  private final ObjectMapper objectMapper;
  private final AtomicReference<SecureWebSocketClient> secureWebSocketClientRef =
      new AtomicReference<>();
  private final ObjectProvider<SecureWebSocketClient> webSocketClientProvider;

  public ClientServerCommunicationService(
      final ObjectMapper mapper,
      final ObjectProvider<SecureWebSocketClient> webSocketClientProvider) {
    this.objectMapper = mapper;

    this.webSocketClientProvider = webSocketClientProvider;
  }

  /**
   * Establishes a WebSocket connection to the server if not already connected. If a connection is
   * already open, it will be reused. This method is synchronized to prevent concurrent connection
   * attempts.
   *
   * @param cardConnectionType The type of card connection to use for the WebSocket connection.
   */
  public synchronized void connect(CardConnectionType cardConnectionType) {
    log.debug("| Entering connect()");
    final var existing = secureWebSocketClientRef.get();
    if (existing != null && existing.isOpen()) {
      log.info("| Reusing existing open WebSocket connection");
      log.debug("| Exiting connect()");
      return;
    }

    final var client = createNewWebSocketClient();
    secureWebSocketClientRef.set(client);

    try {
      client.connectBlocking(cardConnectionType);
    } catch (final RuntimeException e) {
      log.error("| Error connecting to WebSocket server: {}", e.getMessage(), e);
      try {
        client.close();
      } catch (final Exception ex) {
        log.debug(
            "| Error while closing websocket client after failed connect: {}", ex.getMessage());
      }
      secureWebSocketClientRef.set(null);
      throw e;
    }

    log.debug("| Exiting connect()");
  }

  @PreDestroy
  public synchronized void disconnect() {
    log.debug("| Entering disconnect()");
    final var client = secureWebSocketClientRef.getAndSet(null);
    if (client != null) {
      try {
        client.close();
      } catch (final Exception e) {
        log.debug("| Error while closing websocket client during disconnect: {}", e.getMessage());
      }
    }
    log.debug("| Exiting disconnect()");
  }

  public void sendMessage(final PoPPMessage poPPMessage) {
    log.debug("| Entering sendMessage()");
    try {
      final var messageAsString = objectMapper.writeValueAsString(poPPMessage);
      final var client = secureWebSocketClientRef.get();
      if (client == null || client.isClosed()) {
        log.error("| Websocket client is not connected");
        throw new IllegalStateException("Websocket client is not connected");
      }
      log.info("| Send message: {}", messageAsString);
      client.send(messageAsString);
    } catch (final JacksonException ex) {
      log.error("| Error converting message object to string: {}", ex.getMessage());
      throw new IllegalStateException("Error converting message object to string");
    }
    log.debug("| Exiting sendMessage()");
  }

  public CommunicationSslSession getSslSession() {
    final var client = secureWebSocketClientRef.get();
    if (client == null) {
      throw new IllegalStateException("Websocket client is not connected");
    }
    return new CommunicationSslSession(client.getSSLSession());
  }

  private SecureWebSocketClient createNewWebSocketClient() {
    return webSocketClientProvider.getObject();
  }
}
