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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.enums.BdeErrorCode;
import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.poppcommons.api.messages.ConnectorScenarioMessage;
import de.gematik.poppcommons.api.messages.ScenarioResponseMessage;
import de.gematik.poppcommons.api.messages.ScenarioStep;
import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.poppcommons.api.messages.StartMessage;
import de.gematik.poppcommons.api.messages.TokenMessage;
import de.gematik.refpopp.popp_client.cardreader.card.CardCommunicationService;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardService;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardServiceFactory;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardSessionState;
import de.gematik.refpopp.popp_client.client.protocol.ConnectorScenarioProcessor;
import de.gematik.refpopp.popp_client.client.protocol.PoPPMessageHandler;
import de.gematik.refpopp.popp_client.client.protocol.StandardScenarioProcessor;
import de.gematik.refpopp.popp_client.client.session.CommunicationSessionRegistry;
import de.gematik.refpopp.popp_client.client.transport.ClientMessageDispatcher;
import de.gematik.refpopp.popp_client.client.transport.ClientServerCommunicationService;
import de.gematik.refpopp.popp_client.client.transport.CommunicationEventPublisher;
import de.gematik.refpopp.popp_client.client.transport.SecureWebSocketClient;
import de.gematik.refpopp.popp_client.client.transport.WsClientWrapper;
import de.gematik.refpopp.popp_client.client.transport.events.TextMessageReceivedEvent;
import de.gematik.refpopp.popp_client.connector.ConnectorCommunicationServiceWrapper;
import de.gematik.refpopp.popp_client.connector.session.ConnectorSessionLifecycle;
import de.gematik.refpopp.popp_server.handler.WebSocketHandler;
import de.gematik.refpopp.popp_server.scenario.common.orchestrator.MessageOrchestrator;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.CardScenarioProvider;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioId;
import de.gematik.refpopp.popp_server.sessionmanagement.LogicalSessionId;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import io.ktor.client.plugins.logging.LogLevel;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.smartcardio.CardChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the v1 WebSocket exchange between the client and server across serialized JSON frames.
 *
 * <p>The test connects the real client message handling to the server's {@link WebSocketHandler}
 * through an in-memory transport. Card and connector I/O and the server scenario orchestrator are
 * simulated, so no network, card reader, or external service is required. The scenarios protect
 * request correlation without outer session IDs, sequential connection reuse, error recovery, and
 * concurrency across separate connections.
 */
class V1WebSocketEndToEndTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final SessionContainer serverSessions = new SessionContainer();
  private final List<WireClient> clients = new ArrayList<>();
  private final List<String> starts = new CopyOnWriteArrayList<>();
  private final List<String> responses = new CopyOnWriteArrayList<>();
  private final ConcurrentHashMap<String, CompletableFuture<Void>> completedRequests =
      new ConcurrentHashMap<>();
  private WebSocketHandler server;
  private CountDownLatch bothStarted;
  private CountDownLatch firstStarted;
  private CountDownLatch releaseScenarios;

  @BeforeEach
  void setUp() {
    final var provider = mock(CardScenarioProvider.class);
    when(provider.getScenarios()).thenReturn(List.of(new Scenario(ScenarioId.OPEN_EGK, List.of())));
    final MessageOrchestrator orchestrator =
        (message, session) -> {
          if (message instanceof StartMessage start) {
            starts.add(start.getClientSessionId());
            completedRequests.put(session.getSessionId(), new CompletableFuture<>());
            serverSessions.storeSessionData(
                session.getSessionId(),
                SessionContainer.SessionStorageKey.CLIENT_SESSION_ID,
                start.getClientSessionId());
            if (start.getClientSessionId().equals("failed")) {
              throw new ScenarioException(
                  session.getSessionId(), "expected failure", BdeErrorCode.INVALID_MESSAGE);
            }
            if (firstStarted != null && start.getClientSessionId().equals("first")) {
              firstStarted.countDown();
            }
            if (releaseScenarios != null) {
              if (bothStarted != null) {
                bothStarted.countDown();
              }
              try {
                if (!releaseScenarios.await(3, TimeUnit.SECONDS)) {
                  throw new IllegalStateException("Second connection did not start");
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for requests", e);
              }
            }
            if (start.getCardConnectionType() == CardConnectionType.CONTACT_CONNECTOR) {
              session.sendMessage(new ConnectorScenarioMessage("1.0.0", "signed-scenario"));
            } else {
              session.sendMessage(
                  StandardScenarioMessage.builder()
                      .version("1.0.0")
                      .clientSessionId(start.getClientSessionId())
                      .sequenceCounter(0)
                      .timeSpan(1000)
                      .steps(
                          start.getClientSessionId().equals("empty")
                              ? List.of()
                              : List.of(new ScenarioStep("00a4040c", List.of("9000"))))
                      .build());
            }
          } else if (message instanceof ScenarioResponseMessage) {
            responses.add(session.getSessionId());
            final var clientSessionId =
                serverSessions
                    .retrieveSessionData(
                        session.getSessionId(),
                        SessionContainer.SessionStorageKey.CLIENT_SESSION_ID,
                        String.class)
                    .orElseThrow();
            try {
              session.sendMessage(new TokenMessage("token-" + clientSessionId));
            } finally {
              serverSessions.clearRequestState(session.getSessionId());
              completedRequests.get(session.getSessionId()).complete(null);
            }
          } else {
            throw new IllegalArgumentException("Unexpected client message: " + message.getType());
          }
        };
    server = new WebSocketHandler(serverSessions, orchestrator, mapper, provider, 4, 1000, 1024);
  }

  @AfterEach
  void tearDown() {
    if (releaseScenarios != null) {
      releaseScenarios.countDown();
    }
    for (final var client : clients) {
      client.transport.disconnect();
      client.dispatcher.shutdown();
      server.afterConnectionClosed(client.serverSession, CloseStatus.NORMAL);
    }
    server.shutdown();
  }

  @Test
  void virtualCardRequestsReuseConnectionWithoutSendingResponseIds() throws IOException {
    final var client = connect("transport-1");

    assertThat(client.service.startVirtualCard(CardConnectionType.CONTACT_STANDARD, "first", null))
        .isEqualTo("token-first");
    assertThat(client.service.startVirtualCard(CardConnectionType.CONTACT_STANDARD, "second", null))
        .isEqualTo("token-second");
    awaitCompleted("transport-1", "second");

    assertThat(starts).containsExactly("first", "second");
    assertThat(responses)
        .containsExactly(
            LogicalSessionId.of("transport-1", "first"),
            LogicalSessionId.of("transport-1", "second"));
    assertThat(client.clientFrames)
        .extracting(json -> mapper.readTree(json).get("type").asString())
        .containsExactly("Start", "ScenarioResponse", "Start", "ScenarioResponse");
    assertThat(client.clientFrames.stream().filter(json -> json.contains("ScenarioResponse")))
        .allSatisfy(json -> assertThat(mapper.readTree(json).has("clientSessionId")).isFalse());
    assertThat(serverSessions.getActiveRequest("transport-1")).isEmpty();
    assertThat(client.transport.getCurrentWebSocketClient()).isSameAs(client.websocket);
  }

  @Test
  void physicalCardRespondsToApduAndEmptyScenarioWithV1Messages() throws IOException {
    final var client = connect("physical-transport");
    when(client.card.process(anyList())).thenReturn(List.of("9000"));

    assertThat(
            client.service.startStandardCardReader(CardConnectionType.CONTACT_STANDARD, "physical"))
        .isEqualTo("token-physical");
    assertThat(client.service.startVirtualCard(CardConnectionType.CONTACT_STANDARD, "empty", null))
        .isEqualTo("token-empty");
    awaitCompleted("physical-transport", "empty");

    verify(client.card).process(List.of(new ScenarioStep("00a4040c", List.of("9000"))));
    assertThat(mapper.readTree(client.clientFrames.get(3)).get("steps").size()).isZero();
    assertThat(mapper.readTree(client.clientFrames.get(3)).has("clientSessionId")).isFalse();
    assertThat(serverSessions.getActiveRequest("physical-transport")).isEmpty();
  }

  @Test
  void connectorScenarioWithoutOuterIdUsesCorrectContextAndClosesConnectorSession()
      throws IOException {
    final var client = connect("connector-transport");
    when(client.connector.getConnectedEgkCard("kvnr")).thenReturn("egk");
    when(client.connector.startCardSession("egk")).thenReturn("connector-session");
    when(client.connector.secureSendApdu("signed-scenario")).thenReturn(List.of("9000"));

    assertThat(client.service.startWithConnector(CardConnectionType.CONTACT_CONNECTOR, "kvnr"))
        .isEqualTo("token-connector-session");
    awaitCompleted("connector-transport", "connector-session");

    assertThat(client.serverFrames)
        .extracting(json -> mapper.readTree(json).get("type").asString())
        .containsExactly("ConnectorScenario", "Token");
    assertThat(client.serverFrames)
        .allSatisfy(json -> assertThat(mapper.readTree(json).has("clientSessionId")).isFalse());
    assertThat(mapper.readTree(client.clientFrames.get(1)).has("clientSessionId")).isFalse();
    verify(client.connector).secureSendApdu("signed-scenario");
    verify(client.connector).stopCardSession("connector-session");
    assertThat(serverSessions.getActiveRequest("connector-transport")).isEmpty();
  }

  @Test
  void idlessErrorReleasesConnectionForFollowingRequest() throws IOException {
    final var client = connect("transport-1");

    assertThatThrownBy(
            () ->
                client.service.startVirtualCard(
                    CardConnectionType.CONTACT_STANDARD, "failed", null))
        .isInstanceOf(TokenRetrievalException.class)
        .hasMessageContaining("expected failure");
    assertThat(client.serverFrames).hasSize(1);
    assertThat(mapper.readTree(client.serverFrames.getFirst()).has("clientSessionId")).isFalse();
    assertThat(client.service.startVirtualCard(CardConnectionType.CONTACT_STANDARD, "next", null))
        .isEqualTo("token-next");
    awaitCompleted("transport-1", "next");
    assertThat(starts).containsExactly("failed", "next");
    assertThat(serverSessions.getActiveRequest("transport-1")).isEmpty();
  }

  @Test
  void independentConnectionsCanProgressConcurrently() throws Exception {
    final var first = connect("transport-1");
    final var second = connect("transport-2");
    bothStarted = new CountDownLatch(2);
    releaseScenarios = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      final var firstToken =
          executor.submit(
              () ->
                  first.service.startVirtualCard(
                      CardConnectionType.CONTACT_STANDARD, "first", null));
      final var secondToken =
          executor.submit(
              () ->
                  second.service.startVirtualCard(
                      CardConnectionType.CONTACT_STANDARD, "second", null));

      try {
        assertThat(bothStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(serverSessions.getActiveRequest("transport-1"))
            .contains(LogicalSessionId.of("transport-1", "first"));
        assertThat(serverSessions.getActiveRequest("transport-2"))
            .contains(LogicalSessionId.of("transport-2", "second"));
      } finally {
        releaseScenarios.countDown();
      }

      assertThat(firstToken.get(3, TimeUnit.SECONDS)).isEqualTo("token-first");
      assertThat(secondToken.get(3, TimeUnit.SECONDS)).isEqualTo("token-second");
    }
    assertThat(responses)
        .containsExactlyInAnyOrder(
            LogicalSessionId.of("transport-1", "first"),
            LogicalSessionId.of("transport-2", "second"));
  }

  @Test
  void overlappingRequestsOnOneConnectionAreSentSequentially() throws Exception {
    final var client = connect("shared-transport");
    firstStarted = new CountDownLatch(1);
    releaseScenarios = new CountDownLatch(1);
    final var secondStarted = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      final var first =
          executor.submit(
              () ->
                  client.service.startVirtualCard(
                      CardConnectionType.CONTACT_STANDARD, "first", null));
      assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
      final var second =
          executor.submit(
              () -> {
                secondStarted.countDown();
                return client.service.startVirtualCard(
                    CardConnectionType.CONTACT_STANDARD, "second", null);
              });

      try {
        assertThat(secondStarted.await(2, TimeUnit.SECONDS)).isTrue();
        verify(client.websocket, after(150).times(1)).send(anyString());
        assertThat(client.clientFrames).hasSize(1);
      } finally {
        releaseScenarios.countDown();
      }

      assertThat(first.get(3, TimeUnit.SECONDS)).isEqualTo("token-first");
      assertThat(second.get(3, TimeUnit.SECONDS)).isEqualTo("token-second");
    }
    assertThat(starts).containsExactly("first", "second");
    assertThat(client.serverFrames)
        .extracting(json -> mapper.readTree(json).get("type").asString())
        .containsExactly("StandardScenario", "Token", "StandardScenario", "Token");
  }

  private void awaitCompleted(final String transportId, final String clientSessionId) {
    final var request = LogicalSessionId.of(transportId, clientSessionId);
    assertThat(completedRequests.get(request)).succeedsWithin(Duration.ofSeconds(3));
  }

  private WireClient connect(final String transportId) throws IOException {
    final var publisher = mock(CommunicationEventPublisher.class);
    final var serviceRef = new AtomicReference<CommunicationService>();
    doAnswer(
            invocation -> {
              serviceRef
                  .get()
                  .handleServerEvent(invocation.getArgument(0, TextMessageReceivedEvent.class));
              return null;
            })
        .when(publisher)
        .publishEvent(any(TextMessageReceivedEvent.class));
    final var websocket =
        spy(
            new SecureWebSocketClient(
                URI.create("wss://localhost/unused"),
                publisher,
                false,
                LogLevel.NONE,
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(WsClientWrapper.class)));
    doNothing().when(websocket).connectBlocking(any());
    when(websocket.isOpen()).thenReturn(true);
    when(websocket.isClosed()).thenReturn(false);
    final var provider = mock(ObjectProvider.class);
    when(provider.getObject()).thenReturn(websocket);
    final var transport = new ClientServerCommunicationService(mapper, provider);
    final var registry = new CommunicationSessionRegistry();
    final var dispatcher = new ClientMessageDispatcher(2);
    final var card = mock(CardCommunicationService.class);
    when(card.getCardChannel()).thenReturn(Optional.of(mock(CardChannel.class)));
    final var virtualCard = mock(VirtualCardService.class);
    when(virtualCard.isConfigured()).thenReturn(true);
    when(virtualCard.process(any(), any(VirtualCardSessionState.class)))
        .thenAnswer(
            invocation -> {
              final List<?> steps = invocation.getArgument(0);
              return steps.isEmpty() ? List.of() : List.of("9000");
            });
    final var connector = mock(ConnectorCommunicationServiceWrapper.class);
    final var connectorLifecycle = new ConnectorSessionLifecycle(connector);
    final var standard = new StandardScenarioProcessor(card, virtualCard, registry);
    final var connectorProcessor = new ConnectorScenarioProcessor(mapper, connector, standard);
    final var handler =
        new PoPPMessageHandler(
            transport, registry, standard, connectorProcessor, connectorLifecycle);
    final var service =
        new CommunicationService(
            mapper,
            card,
            transport,
            virtualCard,
            mock(VirtualCardServiceFactory.class),
            registry,
            connectorLifecycle,
            handler,
            dispatcher);
    ReflectionTestUtils.setField(service, "tokenWaitTimeoutSeconds", 5);
    serviceRef.set(service);

    final var serverSession = mock(WebSocketSession.class);
    when(serverSession.getId()).thenReturn(transportId);
    when(serverSession.getHandshakeHeaders()).thenReturn(new HttpHeaders());
    when(serverSession.isOpen()).thenReturn(true);
    final var clientFrames = new CopyOnWriteArrayList<String>();
    final var serverFrames = new CopyOnWriteArrayList<String>();
    doAnswer(
            invocation -> {
              final var json = invocation.getArgument(0, TextMessage.class).getPayload();
              serverFrames.add(json);
              websocket.onMessage(json);
              return null;
            })
        .when(serverSession)
        .sendMessage(any(TextMessage.class));
    doAnswer(
            invocation -> {
              final var json = invocation.getArgument(0, String.class);
              clientFrames.add(json);
              server.handleMessage(serverSession, new TextMessage(json));
              return null;
            })
        .when(websocket)
        .send(anyString());

    final var result =
        new WireClient(
            service,
            transport,
            websocket,
            serverSession,
            dispatcher,
            card,
            connector,
            clientFrames,
            serverFrames);
    clients.add(result);
    server.afterConnectionEstablished(serverSession);
    return result;
  }

  private record WireClient(
      CommunicationService service,
      ClientServerCommunicationService transport,
      SecureWebSocketClient websocket,
      WebSocketSession serverSession,
      ClientMessageDispatcher dispatcher,
      CardCommunicationService card,
      ConnectorCommunicationServiceWrapper connector,
      List<String> clientFrames,
      List<String> serverFrames) {}
}
