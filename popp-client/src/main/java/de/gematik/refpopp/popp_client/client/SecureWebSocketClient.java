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
import de.gematik.refpopp.popp_client.configuration.PathResolver;
import de.gematik.zeta.sdk.BuildConfig;
import de.gematik.zeta.sdk.StorageConfig;
import de.gematik.zeta.sdk.TpmConfig;
import de.gematik.zeta.sdk.WsClientExtension;
import de.gematik.zeta.sdk.ZetaSdk;
import de.gematik.zeta.sdk.ZetaSdkClient;
import de.gematik.zeta.sdk.attestation.model.ClientSelfAssessment;
import de.gematik.zeta.sdk.attestation.model.PlatformProductId;
import de.gematik.zeta.sdk.authentication.AuthConfig;
import de.gematik.zeta.sdk.authentication.SubjectTokenProvider;
import de.gematik.zeta.sdk.authentication.smb.SmbTokenProvider;
import de.gematik.zeta.sdk.network.http.client.ZetaHttpClientBuilder;
import de.gematik.zeta.sdk.storage.InMemoryStorage;
import io.ktor.client.plugins.logging.LogLevel;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
  private final Path keyfile;
  private final String alias;
  private final String password;
  private final boolean disableServerValidation;
  private final URI serverUri;
  private final ZetaSdkClient zetaSdk;
  private ExecutorService pool;
  private WsClientExtension.WsSession session;
  private final Map<String, Object> sessionMetadata;

  @Autowired
  public SecureWebSocketClient(
      @Value("${popp-server.url}") final URI serverUri,
      final CommunicationEventPublisher eventPublisher,
      @Value("${zeta.authentication.smb.keyfile}") final String keyfile,
      @Value("${zeta.authentication.smb.alias}") final String alias,
      @Value("${zeta.authentication.smb.password}") final String password,
      @Value("${zeta.client.disableServerValidation}") final boolean disableServerValidation) {
    this.serverUri = serverUri;
    this.eventPublisher = eventPublisher;
    this.pool = Executors.newFixedThreadPool(1);
    this.sessionMetadata = new HashMap<>();
    this.keyfile = PathResolver.resolveAgainstWorkingDirectoryAncestors(keyfile);
    this.alias = alias;
    this.password = password;
    this.disableServerValidation = disableServerValidation;
    this.zetaSdk =
        ZetaSdk.INSTANCE.build(
            serverUri.toString(),
            new BuildConfig(
                "demo-client",
                "0.2.0",
                "sdk-client",
                new StorageConfig(
                    new InMemoryStorage(), "7aae7xXr8rnzVqjpYbosS0CFMrlprkD7jbVotm0fd+w="),
                new TpmConfig() {},
                new AuthConfig(List.of("popp"), 30, true, getTokenProvider()),
                new ClientSelfAssessment(
                    "name",
                    "clientId",
                    "manufacturerId",
                    "manufacturerName",
                    "test@example.com",
                    0,
                    new PlatformProductId.AppleProductId("apple", "macos", List.of("bundleX"))),
                new ZetaHttpClientBuilder("")
                    .disableServerValidation(disableServerValidation)
                    .logging(LogLevel.ALL, message -> log.info("Ktor HttpClient: {}", message)),
                null,
                null));
  }

  private SubjectTokenProvider getTokenProvider() {
    if (!Files.isReadable(keyfile)) {
      throw new IllegalStateException("Can't read private key: " + keyfile);
    }
    return new SmbTokenProvider(
        new SmbTokenProvider.Credentials(keyfile.toString(), alias, password));
  }

  @PostConstruct
  public void init() {
    log.debug("| Initializing SecureWebSocketClient");
    log.debug("| Finished initializing SecureWebSocketClient");
  }

  public void onOpen(final ServerHandshake handshake) {
    log.debug("| Entering onOpen()");
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
    return session == null;
  }

  public boolean isOpen() {
    return session != null;
  }

  public void connectBlocking() {
    // Start new thread

    pool.submit(
        () -> {
          WsClientExtension.ws(
              zetaSdk,
              this.serverUri.toString(),
              builder -> {
                builder.disableServerValidation(true);
                return Unit.INSTANCE;
              },
              new HashMap<>(),
              session -> {
                this.session = session;
                log.info("Zeta SDK WS connected");

                while (true) {
                  WsClientExtension.WsMessage incoming = session.receiveNext();
                  if (incoming == null || incoming instanceof WsClientExtension.WsMessage.Close) {
                    log.debug("WebSocket closed.");
                    break;
                  }
                  if (incoming instanceof WsClientExtension.WsMessage.Text text) {
                    log.debug("WebSocket received: " + text.getText());
                    onMessage(text.getText());
                  } else if (incoming instanceof WsClientExtension.WsMessage.Binary bin) {
                    log.debug("WebSocket received binary (bytes): " + bin.getBytes().length);
                  }
                }
              });
        });

    // TODO: instead of busy waiting, use semaphor for inter-thread communication or smth similar
    while (true) {
      if (this.session != null) break;
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void send(String messageAsString) {
    this.session.sendText(messageAsString);
  }

  public Map<String, Object> getSSLSession() {
    return this.sessionMetadata;
  }

  public boolean getReadyState() {
    return isOpen();
  }
}
