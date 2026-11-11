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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.gematik.openhealth.healthcard.SecureChannel;
import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.PoPPMessage;
import de.gematik.poppcommons.api.messages.StartMessage;
import de.gematik.refpopp.popp_client.cardreader.card.CardCommunicationService;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardService;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardServiceFactory;
import de.gematik.refpopp.popp_client.client.protocol.ConnectorScenarioProcessor;
import de.gematik.refpopp.popp_client.client.protocol.PoPPMessageHandler;
import de.gematik.refpopp.popp_client.client.protocol.StandardScenarioProcessor;
import de.gematik.refpopp.popp_client.client.session.CommunicationSessionRegistry;
import de.gematik.refpopp.popp_client.client.transport.ClientMessageDispatcher;
import de.gematik.refpopp.popp_client.client.transport.ClientServerCommunicationService;
import de.gematik.refpopp.popp_client.client.transport.SecureWebSocketClient;
import de.gematik.refpopp.popp_client.client.transport.events.TextMessageReceivedEvent;
import de.gematik.refpopp.popp_client.client.transport.events.WebSocketCommunicationErrorEvent;
import de.gematik.refpopp.popp_client.client.transport.events.WebSocketConnectionClosedEvent;
import de.gematik.refpopp.popp_client.connector.ConnectorCommunicationServiceWrapper;
import de.gematik.refpopp.popp_client.connector.session.ConnectorSessionLifecycle;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.smartcardio.CardChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies request-state cleanup and failure propagation with request-owned connector sessions. The
 * requesting thread closes its session after token retrieval, including failed requests.
 */
class ConnectorRequestLifecycleTest {

  private static final String SESSION_ID = "connector-session";

  private CommunicationService service;
  private ClientServerCommunicationService transport;
  private CardCommunicationService card;
  private ConnectorCommunicationServiceWrapper connector;
  private CommunicationSessionRegistry registry;
  private ClientMessageDispatcher dispatcher;
  private SecureWebSocketClient client;
  private ConnectorScenarioProcessor connectorProcessor;
  private VirtualCardService virtualCard;

  @BeforeEach
  void setUp() {
    transport = mock(ClientServerCommunicationService.class);
    card = mock(CardCommunicationService.class);
    connector = mock(ConnectorCommunicationServiceWrapper.class);
    registry = spy(new CommunicationSessionRegistry());
    dispatcher = new ClientMessageDispatcher(2);
    client = mock(SecureWebSocketClient.class);
    connectorProcessor = mock(ConnectorScenarioProcessor.class);
    virtualCard = mock(VirtualCardService.class);
    when(transport.getCurrentWebSocketClient()).thenReturn(client);
    when(card.getCardChannel()).thenReturn(Optional.of(mock(CardChannel.class)));
    when(card.getSecureChannel()).thenReturn(Optional.empty());
    when(connector.getConnectedEgkCard("patient")).thenReturn("egk");
    when(connector.startCardSession("egk")).thenReturn(SESSION_ID);
    final var standard = new StandardScenarioProcessor(card, virtualCard, registry);
    final var lifecycle = new ConnectorSessionLifecycle(connector);
    final var handler = new PoPPMessageHandler(transport, registry, standard, connectorProcessor);
    service =
        new CommunicationService(
            new ObjectMapper(),
            card,
            transport,
            virtualCard,
            mock(VirtualCardServiceFactory.class),
            registry,
            lifecycle,
            handler,
            dispatcher);
    ReflectionTestUtils.setField(service, "tokenWaitTimeoutSeconds", 5);
  }

  @AfterEach
  void tearDown() {
    dispatcher.shutdown();
  }

  @ParameterizedTest
  @EnumSource(
      value = CardConnectionType.class,
      names = {"CONTACT_CONNECTOR", "CONTACTLESS_CONNECTOR"})
  void successfulRequestClosesOwnedConnectorSession(CardConnectionType type) {
    if (type == CardConnectionType.CONTACTLESS_CONNECTOR) {
      when(card.getSecureChannel()).thenReturn(Optional.of(mock(SecureChannel.class)));
    }
    respondWithToken();

    assertThat(service.startWithConnector(type, "patient")).isEqualTo("token");

    verify(connector).stopCardSession(SESSION_ID);
    assertRequestRemoved();
  }

  @ParameterizedTest
  @EnumSource(FailurePoint.class)
  void failedRequestClearsRequestStateAndClosesOwnedConnectorSession(FailurePoint point) {
    configureFailure(point);

    final var failure = invokeFailingRequest(point);

    assertThat(failure).hasMessageContaining(point.message);
    verify(connector).stopCardSession(SESSION_ID);
    assertRequestRemoved();
  }

  @ParameterizedTest
  @EnumSource(FailurePoint.class)
  void requestFailurePreservesItsCauseAndSuppressesConnectorCleanupFailure(FailurePoint point) {
    final var injectedFailure = configureFailure(point);
    final var cleanupFailure = new IllegalStateException("cleanup failed");
    doThrow(cleanupFailure).when(connector).stopCardSession(SESSION_ID);

    final var failure = invokeFailingRequest(point);

    assertThat(failure).hasMessageContaining(point.message);
    assertThat(failure.getSuppressed()).containsExactly(cleanupFailure);
    switch (point) {
      case CONNECT, SEND -> assertThat(failure).isSameAs(injectedFailure);
      case VALIDATION -> assertThat(failure).isExactlyInstanceOf(IllegalStateException.class);
      case SCENARIO ->
          assertThat(failure)
              .isInstanceOf(TokenRetrievalException.class)
              .hasCauseReference(injectedFailure);
      case SOCKET_ERROR ->
          assertThat(failure)
              .isInstanceOf(TokenRetrievalException.class)
              .hasRootCause(injectedFailure);
      case SERVER_ERROR, SOCKET_CLOSE ->
          assertThat(failure)
              .isInstanceOf(TokenRetrievalException.class)
              .hasCauseInstanceOf(IllegalStateException.class);
      case TIMEOUT ->
          assertThat(failure)
              .isInstanceOf(TokenRetrievalException.class)
              .hasCauseInstanceOf(TimeoutException.class);
      case INTERRUPTION ->
          assertThat(failure)
              .isInstanceOf(TokenRetrievalException.class)
              .hasCauseInstanceOf(InterruptedException.class);
    }
    verify(connector).stopCardSession(SESSION_ID);
    assertRequestRemoved();
  }

  @Test
  void cleanupFailureAfterTokenIsPropagatedDirectly() {
    respondWithToken();
    final var cleanupFailure = new IllegalStateException("cleanup failed");
    doThrow(cleanupFailure).when(connector).stopCardSession(SESSION_ID);

    assertThatThrownBy(
            () -> service.startWithConnector(CardConnectionType.CONTACT_CONNECTOR, "patient"))
        .isSameAs(cleanupFailure);

    verify(connector).stopCardSession(SESSION_ID);
    assertRequestRemoved();
  }

  @Test
  void waiterRegistrationFailureIsPropagatedAndClosesOwnedConnectorSession() {
    final var failure = new IllegalStateException("registration failed");
    doThrow(failure).when(registry).registerTokenWaiter(SESSION_ID);

    assertThatThrownBy(
            () -> service.startWithConnector(CardConnectionType.CONTACT_CONNECTOR, "patient"))
        .isSameAs(failure);

    verify(connector).stopCardSession(SESSION_ID);
    verifyNoInteractions(transport);
    assertRequestRemoved();
  }

  @Test
  void contextRegistrationFailureClearsRequestStateAndClosesOwnedConnectorSession() {
    final var failure = new IllegalStateException("context registration failed");
    doThrow(failure).when(registry).registerRequestContext(eq(SESSION_ID), any());

    assertThatThrownBy(
            () -> service.startWithConnector(CardConnectionType.CONTACT_CONNECTOR, "patient"))
        .isSameAs(failure);

    verify(connector).stopCardSession(SESSION_ID);
    verify(registry).failToken(SESSION_ID, failure);
    assertRequestRemoved();
  }

  @Test
  void unsuccessfulConnectorSessionStartDoesNotAttemptCleanup() {
    final var failure = new IllegalStateException("start failed");
    when(connector.startCardSession("egk")).thenThrow(failure);

    assertThatThrownBy(
            () -> service.startWithConnector(CardConnectionType.CONTACT_CONNECTOR, "patient"))
        .isSameAs(failure);

    verify(connector, never()).stopCardSession(any());
    verifyNoInteractions(transport);
    assertRequestRemoved();
  }

  @ParameterizedTest
  @EnumSource(
      value = CardConnectionType.class,
      names = {"CONTACT_STANDARD", "CONTACT_VIRTUAL", "CONTACT_CONNECTOR_VIA_STANDARD_TERMINAL"})
  void requestsWithoutOwnedConnectorSessionDoNotStopConnectorSession(CardConnectionType type) {
    respondWithToken();
    when(virtualCard.isConfigured()).thenReturn(true);

    final var token =
        switch (type) {
          case CONTACT_STANDARD -> service.startStandardCardReader(type, "other");
          case CONTACT_VIRTUAL ->
              service.startVirtualCard(CardConnectionType.CONTACT_STANDARD, "other", null);
          case CONTACT_CONNECTOR_VIA_STANDARD_TERMINAL -> service.startConnectorMock("other");
          default -> throw new AssertionError(type);
        };

    assertThat(token).isEqualTo("token");
    verifyNoInteractions(connector);
    assertThat(registry.getRequestContext("other")).isNull();
  }

  @Test
  void interruptionWhileWaitingClosesSessionAndPreservesInterruptFlag() throws Exception {
    final var sent = new CountDownLatch(1);
    final var requestThread = new AtomicReference<Thread>();
    final var interruptPreserved = new AtomicBoolean();
    doAnswer(
            invocation -> {
              requestThread.set(Thread.currentThread());
              sent.countDown();
              return null;
            })
        .when(transport)
        .sendMessage(any());

    try (final var executor = Executors.newSingleThreadExecutor()) {
      final var request =
          executor.submit(
              () -> {
                try {
                  return service.startWithConnector(
                      CardConnectionType.CONTACT_CONNECTOR, "patient");
                } finally {
                  interruptPreserved.set(Thread.currentThread().isInterrupted());
                }
              });
      assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
      requestThread.get().interrupt();

      assertThatThrownBy(() -> request.get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(TokenRetrievalException.class)
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(interruptPreserved).isTrue();
    }

    verify(connector).stopCardSession(SESSION_ID);
    assertRequestRemoved();
  }

  @Test
  void tokenFollowedByCloseAndErrorDuringSlowCleanupKeepsSuccessfulResult() throws Exception {
    respondWithToken();
    final var cleanupStarted = new CountDownLatch(1);
    final var finishCleanup = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              cleanupStarted.countDown();
              assertThat(finishCleanup.await(5, TimeUnit.SECONDS)).isTrue();
              return null;
            })
        .when(connector)
        .stopCardSession(SESSION_ID);

    try (final var executor = Executors.newSingleThreadExecutor()) {
      final var request =
          executor.submit(
              () -> service.startWithConnector(CardConnectionType.CONTACT_CONNECTOR, "patient"));
      try {
        assertThat(cleanupStarted.await(5, TimeUnit.SECONDS)).isTrue();
        service.handleConnectionEvents(WebSocketConnectionClosedEvent.forClient(client));
        service.handleConnectionEvents(
            WebSocketCommunicationErrorEvent.builder()
                .client(client)
                .error(new IllegalStateException("late error"))
                .build());
        assertThat(request.isDone()).isFalse();
      } finally {
        finishCleanup.countDown();
      }

      assertThat(request.get(5, TimeUnit.SECONDS)).isEqualTo("token");
    }

    verify(connector).stopCardSession(SESSION_ID);
    assertRequestRemoved();
  }

  @Test
  void tokenRacingWithConnectionFailureAlwaysClosesOwnedConnectorSession() throws Exception {
    final var sent = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              sent.countDown();
              return null;
            })
        .when(transport)
        .sendMessage(any());
    final var race = new CountDownLatch(1);
    final Throwable failure;
    try (final var executor = Executors.newFixedThreadPool(3)) {
      final var request =
          executor.submit(
              () ->
                  catchThrowable(
                      () ->
                          assertThat(
                                  service.startWithConnector(
                                      CardConnectionType.CONTACT_CONNECTOR, "patient"))
                              .isEqualTo("token")));
      assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
      final var token =
          executor.submit(
              () -> {
                assertThat(race.await(5, TimeUnit.SECONDS)).isTrue();
                receive("{\"type\":\"Token\",\"token\":\"token\"}");
                return null;
              });
      final var close =
          executor.submit(
              () -> {
                assertThat(race.await(5, TimeUnit.SECONDS)).isTrue();
                service.handleConnectionEvents(WebSocketConnectionClosedEvent.forClient(client));
                return null;
              });
      race.countDown();
      token.get(5, TimeUnit.SECONDS);
      close.get(5, TimeUnit.SECONDS);
      failure = request.get(5, TimeUnit.SECONDS);
      if (failure != null) {
        assertThat(failure)
            .isInstanceOf(TokenRetrievalException.class)
            .hasMessageContaining("connection closed");
      }
    }

    dispatcher.shutdown();
    verify(connector).stopCardSession(SESSION_ID);
    assertRequestRemoved();
  }

  @Test
  void lateTokenAndConnectionEventsAfterTimeoutDoNotStopConnectorSessionAgain() {
    ReflectionTestUtils.setField(service, "tokenWaitTimeoutSeconds", 0);

    assertThatThrownBy(
            () -> service.startWithConnector(CardConnectionType.CONTACT_CONNECTOR, "patient"))
        .isInstanceOf(TokenRetrievalException.class)
        .hasCauseInstanceOf(TimeoutException.class);
    receive("{\"type\":\"Token\",\"token\":\"late-token\"}");
    service.handleConnectionEvents(WebSocketConnectionClosedEvent.forClient(client));
    service.handleConnectionEvents(
        WebSocketCommunicationErrorEvent.builder()
            .client(client)
            .error(new IllegalStateException("late error"))
            .build());

    dispatcher.shutdown();
    verify(connector).stopCardSession(SESSION_ID);
    assertRequestRemoved();
  }

  @Test
  void timeoutDuringTokenHandlingWithQueuedConnectionCloseClosesSessionOnlyOnce() {
    final var tokenHandlingStarted = new CountDownLatch(1);
    final var finishTokenHandling = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              tokenHandlingStarted.countDown();
              assertThat(finishTokenHandling.await(5, TimeUnit.SECONDS)).isTrue();
              return invocation.callRealMethod();
            })
        .when(registry)
        .completeToken(SESSION_ID, "token");
    doAnswer(
            invocation -> {
              receive("{\"type\":\"Token\",\"token\":\"token\"}");
              assertThat(tokenHandlingStarted.await(5, TimeUnit.SECONDS)).isTrue();
              service.handleConnectionEvents(WebSocketConnectionClosedEvent.forClient(client));
              return null;
            })
        .when(transport)
        .sendMessage(any());
    ReflectionTestUtils.setField(service, "tokenWaitTimeoutSeconds", 0);

    try {
      assertThatThrownBy(
              () -> service.startWithConnector(CardConnectionType.CONTACT_CONNECTOR, "patient"))
          .isInstanceOf(TokenRetrievalException.class)
          .hasCauseInstanceOf(TimeoutException.class);
    } finally {
      finishTokenHandling.countDown();
      dispatcher.shutdown();
    }

    verify(connector).stopCardSession(SESSION_ID);
    assertRequestRemoved();
  }

  @Test
  void nextRequestWaitsUntilConnectorCleanupFinishes() throws Exception {
    respondWithToken();
    final var cleanupStarted = new CountDownLatch(1);
    final var finishCleanup = new CountDownLatch(1);
    final var nextStarted = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              cleanupStarted.countDown();
              assertThat(finishCleanup.await(5, TimeUnit.SECONDS)).isTrue();
              return null;
            })
        .when(connector)
        .stopCardSession(SESSION_ID);

    try (final var executor = Executors.newFixedThreadPool(2)) {
      final var first =
          executor.submit(
              () -> service.startWithConnector(CardConnectionType.CONTACT_CONNECTOR, "patient"));
      assertThat(cleanupStarted.await(5, TimeUnit.SECONDS)).isTrue();
      final var next =
          executor.submit(
              () -> {
                nextStarted.countDown();
                return service.startStandardCardReader(CardConnectionType.CONTACT_STANDARD, "next");
              });
      try {
        assertThat(nextStarted.await(5, TimeUnit.SECONDS)).isTrue();
        verify(transport, after(100).never()).connect(CardConnectionType.CONTACT_STANDARD);
        assertThat(first.isDone()).isFalse();
      } finally {
        finishCleanup.countDown();
      }
      assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("token");
      assertThat(next.get(5, TimeUnit.SECONDS)).isEqualTo("token");
    }

    verify(connector).stopCardSession(SESSION_ID);
  }

  private IllegalStateException configureFailure(final FailurePoint point) {
    final var failure = new IllegalStateException(point.message);
    switch (point) {
      case CONNECT -> doThrow(failure).when(transport).connect(any());
      case VALIDATION ->
          when(card.getSecureChannel()).thenReturn(Optional.of(mock(SecureChannel.class)));
      case SEND -> doThrow(failure).when(transport).sendMessage(any());
      case SERVER_ERROR ->
          respondWith(
              "{\"type\":\"Error\",\"errorCode\":\"79100\",\"errorDetail\":\"server failed\"}");
      case SOCKET_CLOSE ->
          doAnswer(
                  invocation -> {
                    service.handleConnectionEvents(
                        WebSocketConnectionClosedEvent.forClient(client));
                    return null;
                  })
              .when(transport)
              .sendMessage(any());
      case SOCKET_ERROR ->
          doAnswer(
                  invocation -> {
                    service.handleConnectionEvents(
                        WebSocketCommunicationErrorEvent.builder()
                            .client(client)
                            .error(failure)
                            .build());
                    return null;
                  })
              .when(transport)
              .sendMessage(any());
      case SCENARIO -> {
        when(connectorProcessor.process(any(), any())).thenThrow(failure);
        respondWith(
            "{\"type\":\"ConnectorScenario\",\"version\":\"1.0.0\",\"signedScenario\":\"scenario\"}");
      }
      case TIMEOUT -> ReflectionTestUtils.setField(service, "tokenWaitTimeoutSeconds", 0);
      case INTERRUPTION -> Thread.currentThread().interrupt();
    }
    return failure;
  }

  private Throwable invokeFailingRequest(final FailurePoint point) {
    try {
      final var failure =
          catchThrowable(
              () -> service.startWithConnector(CardConnectionType.CONTACT_CONNECTOR, "patient"));
      assertThat(failure).isInstanceOf(RuntimeException.class);
      if (point == FailurePoint.INTERRUPTION) {
        assertThat(failure).hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } else if (point == FailurePoint.TIMEOUT) {
        assertThat(failure).hasCauseInstanceOf(TimeoutException.class);
      }
      return failure;
    } finally {
      if (point == FailurePoint.INTERRUPTION) {
        Thread.interrupted();
      }
    }
  }

  private void respondWithToken() {
    respondWith("{\"type\":\"Token\",\"token\":\"token\"}");
  }

  private void respondWith(final String payload) {
    doAnswer(
            invocation -> {
              if (invocation.getArgument(0) instanceof StartMessage) {
                receive(payload);
              }
              return null;
            })
        .when(transport)
        .sendMessage(any(PoPPMessage.class));
  }

  private void receive(final String payload) {
    service.handleServerEvent(
        TextMessageReceivedEvent.builder().client(client).payload(payload).build());
  }

  private void assertRequestRemoved() {
    assertThat(registry.hasPendingToken(SESSION_ID)).isFalse();
    assertThat(registry.getRequestContext(SESSION_ID)).isNull();
    assertThat(registry.getPendingSessionIdForConnection(client)).isEmpty();
  }

  private enum FailurePoint {
    CONNECT("connect failed"),
    VALIDATION("Contact connection requested but card is contactless."),
    SEND("send failed"),
    SERVER_ERROR("Server error 79100: server failed"),
    SOCKET_CLOSE("WebSocket connection closed"),
    SOCKET_ERROR("WebSocket communication failed"),
    SCENARIO("scenario failed"),
    TIMEOUT("Token retrieval timed out"),
    INTERRUPTION("Thread was interrupted");

    private final String message;

    FailurePoint(final String message) {
      this.message = message;
    }
  }
}
