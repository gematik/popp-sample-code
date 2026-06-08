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
import de.gematik.zeta.sdk.TpmConfig;
import de.gematik.zeta.sdk.WsClientExtension;
import de.gematik.zeta.sdk.ZetaSdk;
import de.gematik.zeta.sdk.ZetaSdkClient;
import de.gematik.zeta.sdk.attestation.model.AttestationConfig;
import de.gematik.zeta.sdk.attestation.model.PlatformProductId;
import de.gematik.zeta.sdk.authentication.AuthConfig;
import de.gematik.zeta.sdk.authentication.SubjectTokenProvider;
import de.gematik.zeta.sdk.authentication.smb.SmbTokenProvider;
import de.gematik.zeta.sdk.network.http.client.ZetaHttpClientBuilder;
import de.gematik.zeta.sdk.storage.InMemoryStorage;
import de.gematik.zeta.sdk.storage.StorageConfig;
import io.ktor.client.plugins.logging.LogLevel;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
  static final String APPLE_PLATFORM_TYPE_MACOS = "macos";
  static final String LINUX_PACKAGING_TYPE_JAR = "jar";
  static final String PLATFORM_PRODUCT_APPLICATION_ID = "testhub";
  static final String PLATFORM_PRODUCT_VERSION = "latest";

  private final CommunicationEventPublisher eventPublisher;
  private final Path keyfile;
  private final String alias;
  private final String password;
  private final boolean disableServerValidation;
  private final URI serverUri;
  private final ZetaSdkClient zetaSdk;
  private final ExecutorService pool;
  private final CountDownLatch sessionReady = new CountDownLatch(1);
  private final AtomicReference<WsClientExtension.WsSession> session = new AtomicReference<>();
  private final AtomicReference<Throwable> connectError = new AtomicReference<>();
  private final Map<String, Object> sessionMetadata;
  private final WsClientWrapper wsClientWrapper;

  @Autowired
  public SecureWebSocketClient(
      @Value("${popp-server.url}") final URI serverUri,
      final CommunicationEventPublisher eventPublisher,
      @Value("${zeta.authentication.smb.keyfile}") final String keyfile,
      @Value("${zeta.authentication.smb.alias}") final String alias,
      @Value("${zeta.authentication.smb.password}") final String password,
      @Value("${zeta.client.disableServerValidation}") final boolean disableServerValidation,
      final WsClientWrapper wsClientWrapper) {
    this.serverUri = serverUri;
    this.eventPublisher = eventPublisher;
    this.pool = Executors.newFixedThreadPool(1);
    this.sessionMetadata = new HashMap<>();
    this.keyfile = PathResolver.resolveAgainstWorkingDirectoryAncestors(keyfile);
    this.alias = alias;
    this.password = password;
    this.disableServerValidation = disableServerValidation;
    this.wsClientWrapper = wsClientWrapper;
    this.zetaSdk =
        ZetaSdk.INSTANCE.build(
            serverUri.toString(),
            new BuildConfig(
                "demo-client",
                "0.2.0",
                "sdk-client",
                new StorageConfig.Custom(new InMemoryStorage()),
                new TpmConfig() {},
                new AuthConfig(
                    List.of("popp"),
                    30L,
                    true,
                    getTokenProvider(),
                    AttestationConfig.software(),
                    ""),
                createPlatformProductId(),
                new ZetaHttpClientBuilder("")
                    .disableServerValidation(disableServerValidation)
                    .logging(LogLevel.ALL),
                null,
                null,
                null));
  }

  static PlatformProductId createPlatformProductId() {
    return createPlatformProductId(System.getProperty("os.name", ""));
  }

  static PlatformProductId createPlatformProductId(final String osName) {
    final var normalizedOsName = osName.toLowerCase(Locale.ROOT);

    if (normalizedOsName.contains("mac")) {
      return new PlatformProductId.AppleProductId(
          PlatformProductId.PLATFORM_APPLE, APPLE_PLATFORM_TYPE_MACOS, List.of());
    }

    if (normalizedOsName.contains("win")) {
      return new PlatformProductId.WindowsProductId(PlatformProductId.PLATFORM_WINDOWS, "", "");
    }

    if (normalizedOsName.contains("linux")
        || normalizedOsName.contains("nux")
        || normalizedOsName.contains("nix")) {
      return new PlatformProductId.LinuxProductId(
          PlatformProductId.PLATFORM_LINUX,
          LINUX_PACKAGING_TYPE_JAR,
          PLATFORM_PRODUCT_APPLICATION_ID,
          PLATFORM_PRODUCT_VERSION);
    }

    throw new IllegalStateException(
        "Unsupported operating system for ZETA platform product id: " + osName);
  }

  private SubjectTokenProvider getTokenProvider() {
    if (!Files.isReadable(keyfile)) {
      throw new IllegalStateException("Can't read private key: " + keyfile);
    }

    return new SmbTokenProvider(
        new SmbTokenProvider.Credentials(keyfile.toString(), alias, password, ""));
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
    return session.get() == null;
  }

  public boolean isOpen() {
    return session.get() != null;
  }

  public void connectBlocking() {
    pool.submit(
        () -> {
          try {
            wsClientWrapper.ws(
                zetaSdk,
                this.serverUri.toString(),
                builder -> {
                  builder.disableServerValidation(disableServerValidation);
                  return Unit.INSTANCE;
                },
                new HashMap<>(),
                session -> {
                  this.session.set(session);
                  log.info("Zeta SDK WS connected");
                  sessionReady.countDown();

                  while (true) {
                    WsClientExtension.WsMessage incoming = session.receiveNext();
                    if (incoming == null || incoming instanceof WsClientExtension.WsMessage.Close) {
                      log.debug("WebSocket closed.");
                      break;
                    }
                    if (incoming instanceof WsClientExtension.WsMessage.Text text) {
                      log.debug("WebSocket received: {}", text.getText());
                      onMessage(text.getText());
                    } else if (incoming instanceof WsClientExtension.WsMessage.Binary bin) {
                      log.debug("WebSocket received binary (bytes): {}", bin.getBytes().length);
                    }
                  }
                });
          } catch (final Throwable t) {
            connectError.set(t);
            log.error("WebSocket connect failed: {}", t.getMessage(), t);
            sessionReady.countDown();
          }
        });

    try {
      if (!sessionReady.await(150, TimeUnit.SECONDS)) {
        throw new RuntimeException("Connection timeout");
      }
      final Throwable cause = connectError.get();
      if (cause != null) {
        throw new RuntimeException(
            "Failed to establish WebSocket connection: " + cause.getMessage(), cause);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Thread was interrupted while waiting for WebSocket connection", e);
    }
  }

  public void send(String messageAsString) {
    this.session.get().sendText(messageAsString);
  }

  public Map<String, Object> getSSLSession() {
    return this.sessionMetadata;
  }

  public boolean getReadyState() {
    return isOpen();
  }
}
