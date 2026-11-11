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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.gematik.openhealth.healthcard.SecureChannel;
import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.*;
import de.gematik.refpopp.popp_client.cardreader.card.CardCommunicationService;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardService;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardServiceFactory;
import de.gematik.refpopp.popp_client.client.protocol.PoPPMessageHandler;
import de.gematik.refpopp.popp_client.client.session.CommunicationSessionRegistry;
import de.gematik.refpopp.popp_client.client.session.CommunicationSslSession;
import de.gematik.refpopp.popp_client.client.transport.ClientMessageDispatcher;
import de.gematik.refpopp.popp_client.client.transport.ClientServerCommunicationService;
import de.gematik.refpopp.popp_client.client.transport.SecureWebSocketClient;
import de.gematik.refpopp.popp_client.client.transport.events.TextMessageReceivedEvent;
import de.gematik.refpopp.popp_client.client.transport.events.WebSocketCommunicationErrorEvent;
import de.gematik.refpopp.popp_client.client.transport.events.WebSocketConnectionClosedEvent;
import de.gematik.refpopp.popp_client.connector.session.ConnectorSessionLifecycle;
import de.gematik.refpopp.popp_client.connector.session.ConnectorSessionLifecycle.ConnectorSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.smartcardio.CardChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Tests request orchestration, dispatch, and failure propagation with a mocked protocol handler.
 * Protocol processing is covered by {@link CommunicationServiceIntegrationTest} and handler tests;
 * serialized client/server exchanges are covered by {@link V1WebSocketEndToEndTest}.
 */
class CommunicationServiceTest {

  private CommunicationService sut;
  private ClientServerCommunicationService transport;
  private CardCommunicationService card;
  private VirtualCardService virtualCard;
  private VirtualCardServiceFactory virtualCardFactory;
  private CommunicationSessionRegistry registry;
  private ConnectorSessionLifecycle connectorLifecycle;
  private PoPPMessageHandler handler;
  private ClientMessageDispatcher dispatcher;
  private SecureWebSocketClient client;

  @BeforeEach
  void setUp() {
    transport = mock(ClientServerCommunicationService.class);
    card = mock(CardCommunicationService.class);
    virtualCard = mock(VirtualCardService.class);
    virtualCardFactory = mock(VirtualCardServiceFactory.class);
    registry = new CommunicationSessionRegistry();
    connectorLifecycle = mock(ConnectorSessionLifecycle.class);
    handler = mock(PoPPMessageHandler.class);
    dispatcher = mock(ClientMessageDispatcher.class);
    client = mock(SecureWebSocketClient.class);
    when(transport.getCurrentWebSocketClient()).thenReturn(client);
    when(card.getCardChannel()).thenReturn(Optional.of(mock(CardChannel.class)));
    when(card.getSecureChannel()).thenReturn(Optional.empty());
    when(virtualCard.isConfigured()).thenReturn(true);
    sut =
        new CommunicationService(
            new ObjectMapper(),
            card,
            transport,
            virtualCard,
            virtualCardFactory,
            registry,
            connectorLifecycle,
            handler,
            dispatcher);
    ReflectionTestUtils.setField(sut, "tokenWaitTimeoutSeconds", 5);
  }

  @ParameterizedTest
  @MethodSource("serverMessages")
  void handleServerEventDeserializesAndDelegatesThroughDispatcher(
      final String json, final PoPPMessage expectedMessage) {
    final var token = registry.registerTokenWaiter("active");
    registry.associatePendingTokenWithConnection("active", client);

    sut.handleServerEvent(TextMessageReceivedEvent.builder().payload(json).client(client).build());

    final var task = ArgumentCaptor.forClass(Runnable.class);
    verify(dispatcher).dispatch(eq("active"), task.capture());
    verifyNoInteractions(handler);
    task.getValue().run();

    final var message = ArgumentCaptor.forClass(PoPPMessage.class);
    verify(handler).handle(message.capture(), same(client));
    assertThat(message.getValue()).usingRecursiveComparison().isEqualTo(expectedMessage);
    assertThat(token).isNotDone();
    verifyNoInteractions(card, connectorLifecycle);
    verify(transport, never()).getSslSession();
    verify(transport, never()).sendMessage(any());
  }

  private static Stream<Arguments> serverMessages() {
    return Stream.of(
        Arguments.of(
            """
            {"type":"Token","token":"token"}
            """,
            new TokenMessage("token")),
        Arguments.of(
            """
            {"type":"StandardScenario","version":"1.0.0","clientSessionId":"active",
             "sequenceCounter":1,"timeSpan":300,
             "steps":[{"commandApdu":"00A4040000","expectedStatusWords":["9000"]}]}
            """,
            StandardScenarioMessage.builder()
                .version("1.0.0")
                .clientSessionId("active")
                .sequenceCounter(1)
                .timeSpan(300)
                .steps(List.of(new ScenarioStep("00A4040000", List.of("9000"))))
                .build()),
        Arguments.of(
            """
            {"type":"ConnectorScenario","version":"1.0.0","signedScenario":"signed-scenario"}
            """,
            new ConnectorScenarioMessage("1.0.0", "signed-scenario")),
        Arguments.of(
            """
            {"type":"Error","errorCode":"79100","errorDetail":"failed"}
            """,
            ErrorMessage.builder().errorCode("79100").errorDetail("failed").build()));
  }

  @Test
  void handlerFailureIsRethrownAndFailsOnlyTheDispatchedRequest() {
    final var failure = new IllegalStateException("handler failed");
    final var token = registry.registerTokenWaiter("active");
    final var otherToken = registry.registerTokenWaiter("other");
    registry.associatePendingTokenWithConnection("active", client);
    registry.associatePendingTokenWithConnection("other", mock(SecureWebSocketClient.class));
    doThrow(failure).when(handler).handle(any(PoPPMessage.class), same(client));

    sut.handleServerEvent(
        TextMessageReceivedEvent.builder()
            .payload("{\"type\":\"Token\",\"token\":\"token\"}")
            .client(client)
            .build());
    final var task = ArgumentCaptor.forClass(Runnable.class);
    verify(dispatcher).dispatch(eq("active"), task.capture());

    assertThatThrownBy(() -> task.getValue().run()).isSameAs(failure);
    assertThat(token).isCompletedExceptionally();
    assertThat(token.exceptionNow()).isSameAs(failure);
    assertThat(otherToken).isNotDone();
    assertThat(registry.getPendingSessionIdForConnection(client)).isEmpty();
  }

  @Test
  void messageWithoutConnectionIsDelegatedWithItsSessionIdAsOrderingKey() {
    sut.handleServerEvent(
        new TextMessageReceivedEvent(
            """
            {"type":"StandardScenario","version":"1.0.0","clientSessionId":"scoped",
             "sequenceCounter":0,"timeSpan":0,"steps":[]}
            """));
    final var task = ArgumentCaptor.forClass(Runnable.class);
    verify(dispatcher).dispatch(eq("scoped"), task.capture());

    task.getValue().run();

    verify(handler).handle(any(StandardScenarioMessage.class), isNull());
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid json", "{\"type\":\"UNKNOWN_MESSAGE\",\"payload\":\"payload\"}"})
  void invalidServerMessageFailsBeforeDispatch(final String json) {
    assertThatThrownBy(() -> sut.handleServerEvent(new TextMessageReceivedEvent(json)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Error parsing message");

    verifyNoInteractions(handler, dispatcher, transport, card);
  }

  @Test
  void startStandardCardReaderRegistersRequestBeforeSendingStart() {
    doAnswer(
            invocation -> {
              final var start = invocation.getArgument(0, StartMessage.class);
              assertThat(registry.hasPendingToken("physical")).isTrue();
              assertThat(registry.getPendingSessionIdForConnection(client)).contains("physical");
              final var context = registry.getRequestContext("physical");
              assertThat(context.getClientSessionId()).isEqualTo("physical");
              assertThat(context.getCardConnectionType())
                  .isEqualTo(CardConnectionType.CONTACT_STANDARD);
              assertThat(context.isVirtualCard()).isFalse();
              assertThat(context.isConnectorMock()).isFalse();
              registry.completeToken(start.getClientSessionId(), "token");
              return null;
            })
        .when(transport)
        .sendMessage(any(StartMessage.class));

    assertThat(sut.startStandardCardReader(CardConnectionType.CONTACT_STANDARD, "physical"))
        .isEqualTo("token");

    final var start = ArgumentCaptor.forClass(StartMessage.class);
    final var order = inOrder(transport);
    order.verify(transport).connect(CardConnectionType.CONTACT_STANDARD);
    order.verify(transport).getCurrentWebSocketClient();
    order.verify(transport).sendMessage(start.capture());
    assertThat(start.getValue().getClientSessionId()).isEqualTo("physical");
    assertThat(start.getValue().getCardConnectionType())
        .isEqualTo(CardConnectionType.CONTACT_STANDARD);
    assertThat(start.getValue().getVersion()).isEqualTo("1.0.0");
    verifyNoInteractions(handler);
    verify(transport, never()).getSslSession();
    verify(transport, never()).disconnect();
  }

  @ParameterizedTest
  @EnumSource(
      value = CardConnectionType.class,
      names = {"CONTACT_CONNECTOR", "CONTACTLESS_CONNECTOR"})
  void startWithConnectorUsesSessionFromLifecycle(final CardConnectionType type) {
    final var session = mock(ConnectorSession.class);
    when(session.getSessionId()).thenReturn("connector-session");
    when(connectorLifecycle.startSession("patient")).thenReturn(session);
    if (type == CardConnectionType.CONTACTLESS_CONNECTOR) {
      when(card.getSecureChannel()).thenReturn(Optional.of(mock(SecureChannel.class)));
    }
    mockImmediateTokenResponse();

    assertThat(sut.startWithConnector(type, "patient")).isEqualTo("dummy-token");

    final var start = ArgumentCaptor.forClass(StartMessage.class);
    final var order = inOrder(connectorLifecycle, transport, session);
    order.verify(connectorLifecycle).startSession("patient");
    order.verify(transport).connect(type);
    order.verify(transport).sendMessage(start.capture());
    order.verify(session).close();
    assertThat(start.getValue().getClientSessionId()).isEqualTo("connector-session");
    assertThat(start.getValue().getCardConnectionType()).isEqualTo(type);
    verifyNoInteractions(handler);
  }

  @Test
  void startWithConnectorForwardsNullPatientIdToLifecycle() {
    final var session = mock(ConnectorSession.class);
    when(session.getSessionId()).thenReturn("connector-session");
    when(connectorLifecycle.startSession(null)).thenReturn(session);
    mockImmediateTokenResponse();

    assertThat(sut.startWithConnector(CardConnectionType.CONTACT_CONNECTOR, null))
        .isEqualTo("dummy-token");

    verify(connectorLifecycle).startSession(null);
    verify(session).close();
  }

  @Test
  void connectorStartFailureIsPropagatedBeforeConnecting() {
    final var failure = new IllegalStateException("connector unavailable");
    when(connectorLifecycle.startSession("patient")).thenThrow(failure);

    assertThatThrownBy(
            () -> sut.startWithConnector(CardConnectionType.CONTACT_CONNECTOR, "patient"))
        .isSameAs(failure);

    verifyNoInteractions(transport, handler);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void missingSessionIdUsesExistingTransportSessionId(final String sessionId) {
    when(transport.getSslSession())
        .thenReturn(
            new CommunicationSslSession(
                new HashMap<>(Map.of("clientSessionId", "initial-session"))));
    mockImmediateTokenResponse();

    assertThat(sut.startStandardCardReader(CardConnectionType.CONTACT_STANDARD, sessionId))
        .isEqualTo("dummy-token");

    final var start = ArgumentCaptor.forClass(StartMessage.class);
    verify(transport).sendMessage(start.capture());
    assertThat(start.getValue().getClientSessionId()).isEqualTo("initial-session");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void missingSessionIdGeneratesUuidWhenNoTransportSessionExists(final String sessionId) {
    mockImmediateTokenResponse();

    sut.startStandardCardReader(CardConnectionType.CONTACT_STANDARD, sessionId);

    final var start = ArgumentCaptor.forClass(StartMessage.class);
    verify(transport).sendMessage(start.capture());
    assertThat(UUID.fromString(start.getValue().getClientSessionId())).isNotNull();
  }

  @Test
  void explicitNonUuidSessionIdIsPreserved() {
    mockImmediateTokenResponse();

    sut.startStandardCardReader(CardConnectionType.CONTACT_CONNECTOR, "not-a-uuid");

    final var start = ArgumentCaptor.forClass(StartMessage.class);
    verify(transport).sendMessage(start.capture());
    assertThat(start.getValue().getClientSessionId()).isEqualTo("not-a-uuid");
    verifyNoInteractions(connectorLifecycle);
    verify(transport, never()).getSslSession();
  }

  @Test
  void contactRequestRejectsContactlessCardAndCleansUpRequest() {
    when(card.getSecureChannel()).thenReturn(Optional.of(mock(SecureChannel.class)));

    assertThatThrownBy(
            () -> sut.startStandardCardReader(CardConnectionType.CONTACT_STANDARD, "physical"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Contact connection requested but card is contactless.");

    assertThat(registry.hasPendingToken("physical")).isFalse();
    assertThat(registry.getRequestContext("physical")).isNull();
    verify(transport, never()).sendMessage(any());
  }

  @Test
  void contactlessRequestRejectsContactCard() {
    assertThatThrownBy(
            () -> sut.startStandardCardReader(CardConnectionType.CONTACTLESS_STANDARD, "physical"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Contactless connection requested but card is contact-based.");

    verify(transport, never()).sendMessage(any());
  }

  @Test
  void physicalRequestRejectsMissingCard() {
    when(card.getCardChannel()).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> sut.startStandardCardReader(CardConnectionType.CONTACT_STANDARD, "physical"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No card inserted.");

    verify(transport, never()).sendMessage(any());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"selected-card.xml"})
  void startVirtualCardRegistersSelectedCardAndRequestContext(final String imageFile) {
    final var selectedCard = mock(VirtualCardService.class);
    when(selectedCard.isConfigured()).thenReturn(true);
    when(virtualCardFactory.create("selected-card.xml")).thenReturn(selectedCard);
    doAnswer(
            invocation -> {
              final var context = registry.getRequestContext("virtual");
              assertThat(context.isVirtualCard()).isTrue();
              assertThat(context.isConnectorMock()).isFalse();
              assertThat(context.getCardConnectionType())
                  .isEqualTo(CardConnectionType.CONTACT_STANDARD);
              assertThat(registry.getVirtualCardServiceOrDefault("virtual", null))
                  .isSameAs(imageFile == null || imageFile.isEmpty() ? virtualCard : selectedCard);
              registry.completeToken("virtual", "virtual-token");
              return null;
            })
        .when(transport)
        .sendMessage(any(StartMessage.class));

    assertThat(sut.startVirtualCard(CardConnectionType.CONTACT_STANDARD, "virtual", imageFile))
        .isEqualTo("virtual-token");

    verify(transport).connect(CardConnectionType.CONTACT_STANDARD);
    final var start = ArgumentCaptor.forClass(StartMessage.class);
    verify(transport).sendMessage(start.capture());
    assertThat(start.getValue().getClientSessionId()).isEqualTo("virtual");
    assertThat(start.getValue().getCardConnectionType())
        .isEqualTo(CardConnectionType.CONTACT_STANDARD);
    assertThat(registry.getRequestContext("virtual")).isNull();
    assertThat(registry.getVirtualCardServiceOrDefault("virtual", null)).isNull();
    verifyNoInteractions(handler, card);
  }

  @Test
  void startVirtualCardRejectsUnconfiguredCard() {
    when(virtualCard.isConfigured()).thenReturn(false);

    assertThatThrownBy(
            () -> sut.startVirtualCard(CardConnectionType.CONTACT_STANDARD, "virtual", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No virtual card image configured");

    assertThat(registry.hasPendingToken("virtual")).isFalse();
    verifyNoInteractions(transport, handler);
  }

  @Test
  void startConnectorMockRegistersMockContextAndSendsConnectorStart() {
    doAnswer(
            invocation -> {
              final var context = registry.getRequestContext("mock-session");
              assertThat(context.isConnectorMock()).isTrue();
              assertThat(context.isVirtualCard()).isFalse();
              registry.completeToken("mock-session", "mock-token");
              return null;
            })
        .when(transport)
        .sendMessage(any(StartMessage.class));

    assertThat(sut.startConnectorMock("mock-session")).isEqualTo("mock-token");

    verify(transport).connect(CardConnectionType.UNKNOWN);
    final var start = ArgumentCaptor.forClass(StartMessage.class);
    verify(transport).sendMessage(start.capture());
    assertThat(start.getValue().getClientSessionId()).isEqualTo("mock-session");
    assertThat(start.getValue().getCardConnectionType())
        .isEqualTo(CardConnectionType.CONTACT_CONNECTOR);
    verifyNoInteractions(handler, connectorLifecycle);
  }

  @Test
  void tokenRequestsOnSharedConnectionWaitUntilPreviousTokenCompletes() throws Exception {
    final var firstSent = new CountDownLatch(1);
    final var secondStarted = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              if (invocation
                  .getArgument(0, StartMessage.class)
                  .getClientSessionId()
                  .equals("first")) {
                firstSent.countDown();
              }
              return null;
            })
        .when(transport)
        .sendMessage(any(StartMessage.class));

    try (var executor = Executors.newFixedThreadPool(2)) {
      final var first =
          executor.submit(
              () -> sut.startVirtualCard(CardConnectionType.CONTACT_STANDARD, "first", null));
      assertThat(firstSent.await(1, TimeUnit.SECONDS)).isTrue();
      final var second =
          executor.submit(
              () -> {
                secondStarted.countDown();
                return sut.startConnectorMock("second");
              });
      assertThat(secondStarted.await(1, TimeUnit.SECONDS)).isTrue();
      try {
        verify(transport, after(150).times(1)).sendMessage(any(StartMessage.class));
        assertThat(registry.hasPendingToken("second")).isFalse();
        assertThat(registry.getRequestContext("second")).isNull();

        registry.completeToken("first", "first-token");
        assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("first-token");
        verify(transport, timeout(1_000).times(2)).sendMessage(any(StartMessage.class));
        registry.completeToken("second", "second-token");
        assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo("second-token");
      } finally {
        registry.failToken("first", new IllegalStateException("test finished"));
        registry.failToken("second", new IllegalStateException("test finished"));
      }
    }
  }

  @Test
  void failedStartClearsPendingStateAndAllowsNextRequest() {
    final var failure = new IllegalStateException("send failed");
    doThrow(failure)
        .doAnswer(
            invocation -> {
              registry.completeToken("second", "second-token");
              return null;
            })
        .when(transport)
        .sendMessage(any(StartMessage.class));

    assertThatThrownBy(() -> sut.startConnectorMock("first")).isSameAs(failure);
    assertThat(registry.hasPendingToken("first")).isFalse();
    assertThat(registry.getRequestContext("first")).isNull();
    assertThat(registry.getPendingSessionIdForConnection(client)).isEmpty();
    assertThat(sut.startConnectorMock("second")).isEqualTo("second-token");
  }

  @Test
  void failedTokenWaitPreservesCauseAndAllowsNextRequest() {
    final var failure = new IllegalStateException("request failed");
    doAnswer(
            invocation -> {
              registry.failToken("first", failure);
              return null;
            })
        .doAnswer(
            invocation -> {
              registry.completeToken("second", "second-token");
              return null;
            })
        .when(transport)
        .sendMessage(any(StartMessage.class));

    assertThatThrownBy(() -> sut.startConnectorMock("first"))
        .isInstanceOf(TokenRetrievalException.class)
        .hasMessageContaining("request failed")
        .hasCauseReference(failure);
    assertThat(registry.hasPendingToken("first")).isFalse();
    assertThat(sut.startConnectorMock("second")).isEqualTo("second-token");
  }

  @Test
  void timedOutRequestClearsPendingStateAndAllowsNextRequest() {
    ReflectionTestUtils.setField(sut, "tokenWaitTimeoutSeconds", 0);

    assertThatThrownBy(() -> sut.startConnectorMock("first"))
        .isInstanceOf(TokenRetrievalException.class)
        .hasMessage("Token retrieval timed out");
    assertThat(registry.hasPendingToken("first")).isFalse();
    assertThat(registry.getRequestContext("first")).isNull();
    assertThat(registry.getPendingSessionIdForConnection(client)).isEmpty();

    mockImmediateTokenResponse();
    assertThat(sut.startConnectorMock("second")).isEqualTo("dummy-token");
  }

  @Test
  void interruptedRequestPreservesInterruptAndClearsPendingState() {
    try {
      Thread.currentThread().interrupt();
      assertThatThrownBy(() -> sut.startConnectorMock("interrupted"))
          .isInstanceOf(TokenRetrievalException.class)
          .hasMessage("Thread was interrupted while waiting for token");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }

    assertThat(registry.hasPendingToken("interrupted")).isFalse();
    assertThat(registry.getRequestContext("interrupted")).isNull();
    assertThat(registry.getPendingSessionIdForConnection(client)).isEmpty();
  }

  @Test
  void connectionErrorIsDispatchedAndFailsOnlyItsPendingRequest() {
    final var token = registry.registerTokenWaiter("active");
    final var otherToken = registry.registerTokenWaiter("other");
    registry.associatePendingTokenWithConnection("active", client);
    registry.associatePendingTokenWithConnection("other", mock(SecureWebSocketClient.class));
    final var failure = new IllegalStateException("connection failed");

    sut.handleConnectionEvents(
        WebSocketCommunicationErrorEvent.builder().error(failure).client(client).build());

    final var task = ArgumentCaptor.forClass(Runnable.class);
    verify(dispatcher).dispatch(eq("active"), task.capture());
    assertThat(token).isNotDone();
    task.getValue().run();
    assertThat(token).isCompletedExceptionally();
    assertThat(token.exceptionNow())
        .hasMessage("WebSocket communication failed while waiting for a token")
        .hasCauseReference(failure);
    assertThat(otherToken).isNotDone();
    verifyNoInteractions(handler);
  }

  @Test
  void connectionCloseIsDispatchedAndFailsOnlyItsPendingRequest() {
    final var token = registry.registerTokenWaiter("active");
    final var otherToken = registry.registerTokenWaiter("other");
    registry.associatePendingTokenWithConnection("active", client);
    registry.associatePendingTokenWithConnection("other", mock(SecureWebSocketClient.class));

    sut.handleConnectionEvents(WebSocketConnectionClosedEvent.forClient(client));

    final var task = ArgumentCaptor.forClass(Runnable.class);
    verify(dispatcher).dispatch(eq("active"), task.capture());
    assertThat(token).isNotDone();
    task.getValue().run();
    assertThat(token).isCompletedExceptionally();
    assertThat(token.exceptionNow())
        .hasMessage("WebSocket connection closed while waiting for a token");
    assertThat(otherToken).isNotDone();
    verifyNoInteractions(handler);
  }

  @Test
  void staleConnectionCloseDoesNotAffectActiveRequest() {
    final var token = registry.registerTokenWaiter("active");
    registry.associatePendingTokenWithConnection("active", client);

    sut.handleConnectionEvents(
        WebSocketConnectionClosedEvent.forClient(mock(SecureWebSocketClient.class)));

    assertThat(token).isNotDone();
    verifyNoInteractions(dispatcher, handler);
  }

  @Test
  void duplicateActiveSessionDoesNotStartOrFailTheExistingRequest() {
    final var future = registry.registerTokenWaiter("active");
    final var context = registry.getRequestContext("active");

    assertThatThrownBy(() -> sut.startConnectorMock("active"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already registered");

    assertThat(future).isNotDone();
    assertThat(registry.getRequestContext("active")).isSameAs(context);
    verifyNoInteractions(transport);
  }

  @Test
  void failureDuringInitializationCannotRecreateRequestState() {
    final var failure = new IllegalStateException("connection failed");
    doAnswer(
            invocation -> {
              registry.failToken("session-id", failure);
              return null;
            })
        .when(transport)
        .connect(CardConnectionType.UNKNOWN);

    assertThatThrownBy(() -> sut.startConnectorMock("session-id"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No pending token request");

    assertThat(registry.hasPendingToken("session-id")).isFalse();
    assertThat(registry.getRequestContext("session-id")).isNull();
    verify(transport, never()).sendMessage(any());
  }

  private void mockImmediateTokenResponse() {
    doAnswer(
            invocation -> {
              final var start = invocation.getArgument(0, StartMessage.class);
              assertThat(registry.hasPendingToken(start.getClientSessionId())).isTrue();
              assertThat(registry.getPendingSessionIdForConnection(client))
                  .contains(start.getClientSessionId());
              registry.completeToken(start.getClientSessionId(), "dummy-token");
              return null;
            })
        .when(transport)
        .sendMessage(any(StartMessage.class));
  }
}
