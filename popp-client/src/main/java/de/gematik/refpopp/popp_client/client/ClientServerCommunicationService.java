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

import de.gematik.poppcommons.api.messages.PoPPMessage;
import javax.net.ssl.SSLSession;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class ClientServerCommunicationService {

  private final ObjectMapper objectMapper;
  private WebSocketClient webSocketClient;
  private final ObjectProvider<WebSocketClient> webSocketClientProvider;

  public ClientServerCommunicationService(
      final ObjectMapper mapper, final ObjectProvider<WebSocketClient> webSocketClientProvider) {
    this.objectMapper = mapper;

    this.webSocketClientProvider = webSocketClientProvider;
  }

  public void connect() {
    log.debug("| Entering connect()");

    this.webSocketClient = createNewWebSocketClient();

    if (webSocketClient.isClosed() || !webSocketClient.isOpen()) {
      log.info("| Websocket client is closed");
      try {
        webSocketClient.connectBlocking();
      } catch (final InterruptedException e) {
        log.error("Error connecting to WebSocket server: {}", e.getMessage());
        Thread.currentThread().interrupt();
      }
    }

    log.debug("| Exiting connect()");
  }

  public void sendMessage(final PoPPMessage poPPMessage) {
    log.debug("| Entering sendMessage()");
    try {
      final var messageAsString = objectMapper.writeValueAsString(poPPMessage);
      if (webSocketClient == null || webSocketClient.isClosed()) {
        log.error("Websocket client is not connected");
        throw new IllegalStateException("Websocket client is not connected");
      }
      webSocketClient.send(messageAsString);
    } catch (final JacksonException ex) {
      log.error("Error converting message object to string: {}", ex.getMessage());
      throw new IllegalStateException("Error converting message object to string");
    }
    log.debug("| Exiting sendMessage()");
  }

  public SSLSession getSSLSession() {
    return webSocketClient.getSSLSession();
  }

  private WebSocketClient createNewWebSocketClient() {
    return webSocketClientProvider.getObject();
  }
}
