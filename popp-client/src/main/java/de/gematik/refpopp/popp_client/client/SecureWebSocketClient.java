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
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

@Slf4j
public class SecureWebSocketClient extends WebSocketClient {

  private final CommunicationEventPublisher eventPublisher;

  private final String keystorePassword;

  private final String keyStore;

  public SecureWebSocketClient(
      final URI serverUri,
      final CommunicationEventPublisher eventPublisher,
      final String keyStore,
      final String keystorePassword) {
    super(serverUri);
    this.eventPublisher = eventPublisher;
    this.keyStore = keyStore;
    this.keystorePassword = keystorePassword;
  }

  @PostConstruct
  public void init() throws Exception {
    log.debug("| Initializing SecureWebSocketClient");
    // Use SunJSSE as a provider to have a different Instance of SSLContext than for the Konnektor
    final var sslContext = SSLContext.getInstance("TLS", "SunJSSE");
    final var trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

    final var trustStore = KeyStore.getInstance("PKCS12");
    try (final InputStream keyStoreStream =
        getClass().getClassLoader().getResourceAsStream(keyStore)) {
      trustStore.load(keyStoreStream, keystorePassword.toCharArray());
    }
    trustManagerFactory.init(trustStore);
    sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

    this.setSocketFactory(sslContext.getSocketFactory());

    log.debug("| Finished initializing SecureWebSocketClient");
  }

  @Override
  public void onOpen(final ServerHandshake handshake) {
    log.debug("| Entering onOpen()");
    log.info("| Secure WebSocket connection opened");
    eventPublisher.publishEvent(new WebSocketConnectionOpenedEvent());
    log.debug("| Exiting onOpen()");
  }

  @Override
  public void onMessage(final String message) {
    log.debug("| Entering onMessage()");
    log.info("| Received message: {}", message);
    eventPublisher.publishEvent(TextMessageReceivedEvent.builder().payload(message).build());
  }

  @Override
  public void onClose(final int code, final String reason, final boolean remote) {
    log.debug("| Entering onClose()");
    log.info("| Connection closed: {}", reason);
    eventPublisher.publishEvent(new WebSocketConnectionClosedEvent());
    log.debug("| Exiting onClose()");
  }

  @Override
  public void onError(final Exception ex) {
    log.debug("| Entering onError()");
    log.error(ex.getMessage());
    eventPublisher.publishEvent(WebSocketCommunicationErrorEvent.builder().error(ex).build());
    log.debug("| Exiting onError()");
  }
}
