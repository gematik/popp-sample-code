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

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.ScenarioResponseMessage;
import de.gematik.poppcommons.api.messages.ScenarioStep;
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
import de.gematik.refpopp.popp_client.client.transport.SecureWebSocketClient;
import de.gematik.refpopp.popp_client.client.transport.events.TextMessageReceivedEvent;
import de.gematik.refpopp.popp_client.client.transport.events.WebSocketConnectionClosedEvent;
import de.gematik.refpopp.popp_client.connector.ConnectorCommunicationServiceWrapper;
import de.gematik.refpopp.popp_client.connector.session.ConnectorSessionLifecycle;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises the real service, dispatcher, registry, handler, processors, and connector lifecycle.
 * Only transport and card/connector I/O are mocked. Requests use explicit contexts and origin
 * connections rather than SSL-session attributes.
 */
class CommunicationServiceIntegrationTest {

  private static final String STANDARD_SCENARIO =
      """
      {"type":"StandardScenario","version":"1.0.0","clientSessionId":"active",
       "sequenceCounter":0,"timeSpan":1000,
       "steps":[{"commandApdu":"00A4040000","expectedStatusWords":["9000"]}]}
      """;
  private static final List<ScenarioStep> STEPS =
      List.of(new ScenarioStep("00A4040000", List.of("9000")));

  private final ObjectMapper mapper = new ObjectMapper();
  private CommunicationService service;
  private ClientServerCommunicationService transport;
  private CardCommunicationService card;
  private VirtualCardService virtualCard;
  private ConnectorCommunicationServiceWrapper connector;
  private CommunicationSessionRegistry registry;
  private ClientMessageDispatcher dispatcher;
  private SecureWebSocketClient client;

  @BeforeEach
  void setUp() {
    transport = mock(ClientServerCommunicationService.class);
    card = mock(CardCommunicationService.class);
    virtualCard = mock(VirtualCardService.class);
    connector = mock(ConnectorCommunicationServiceWrapper.class);
    registry = new CommunicationSessionRegistry();
    dispatcher = new ClientMessageDispatcher(2);
    client = mock(SecureWebSocketClient.class);
    final var lifecycle = new ConnectorSessionLifecycle(connector);
    final var standard = new StandardScenarioProcessor(card, virtualCard, registry);
    final var connectorProcessor = new ConnectorScenarioProcessor(mapper, connector, standard);
    final var handler = new PoPPMessageHandler(transport, registry, standard, connectorProcessor);
    service =
        new CommunicationService(
            mapper,
            card,
            transport,
            virtualCard,
            mock(VirtualCardServiceFactory.class),
            registry,
            lifecycle,
            handler,
            dispatcher);
  }

  @AfterEach
  void tearDown() {
    dispatcher.shutdown();
  }

  @Test
  void standardScenarioAndTokenUseOnlyTheOriginRequest() throws Exception {
    final var token = registry.registerTokenWaiter("active");
    registry.associatePendingTokenWithConnection("active", client);
    registry.registerRequestContext("active", CardConnectionType.CONTACT_STANDARD);
    final var otherClient = mock(SecureWebSocketClient.class);
    final var otherToken = registry.registerTokenWaiter("other");
    registry.associatePendingTokenWithConnection("other", otherClient);
    registry.registerRequestContext("other", CardConnectionType.CONTACT_CONNECTOR);
    when(card.process(STEPS)).thenReturn(List.of("9000"));

    receive(STANDARD_SCENARIO);
    receive("{\"type\":\"Token\",\"token\":\"physical-token\"}");

    assertThat(token.get(5, TimeUnit.SECONDS)).isEqualTo("physical-token");
    verify(card).process(STEPS);
    assertScenarioResponses(List.of("9000"));
    assertThat(otherToken).isNotDone();
    assertThat(registry.getPendingSessionIdForConnection(otherClient)).contains("other");
    assertThat(registry.getRequestContext("other")).isNotNull();
    assertThat(registry.getPendingSessionIdForConnection(client)).isEmpty();
    assertThat(registry.getRequestContext("active")).isNull();
    verifyNoInteractions(connector, virtualCard);
    verify(transport, never()).getSslSession();
  }

  @Test
  void virtualCardScenariosReuseTheRegisteredRequestState() throws Exception {
    final var token = registry.registerTokenWaiter("active");
    registry.associatePendingTokenWithConnection("active", client);
    registry
        .registerRequestContext("active", CardConnectionType.CONTACT_STANDARD)
        .setVirtualCard(true);
    final var selectedCard = mock(VirtualCardService.class);
    registry.registerVirtualCard("active", selectedCard);
    when(selectedCard.isConfigured()).thenReturn(true);
    when(selectedCard.process(eq(STEPS), any(VirtualCardSessionState.class)))
        .thenReturn(List.of("9000"));

    receive(STANDARD_SCENARIO);
    receive(STANDARD_SCENARIO.replace("\"sequenceCounter\":0", "\"sequenceCounter\":1"));
    receive("{\"type\":\"Token\",\"token\":\"virtual-token\"}");

    assertThat(token.get(5, TimeUnit.SECONDS)).isEqualTo("virtual-token");
    final var states = ArgumentCaptor.forClass(VirtualCardSessionState.class);
    verify(selectedCard, times(2)).process(eq(STEPS), states.capture());
    assertThat(states.getAllValues().getFirst()).isSameAs(states.getAllValues().getLast());
    assertScenarioResponses(List.of("9000"), List.of("9000"));
    assertThat(registry.getVirtualCardServiceOrDefault("active", null)).isNull();
    verifyNoInteractions(card, virtualCard, connector);
    verify(transport, never()).getSslSession();
  }

  @Test
  void connectorScenarioUsesOriginContextWithoutClosingRequestOwnedSession() throws Exception {
    final var token = registry.registerTokenWaiter("active");
    registry.associatePendingTokenWithConnection("active", client);
    registry.registerRequestContext("active", CardConnectionType.CONTACT_CONNECTOR);
    when(connector.secureSendApdu("signed-scenario")).thenReturn(List.of("9000"));

    receive(
        """
        {"type":"ConnectorScenario","version":"1.0.0","signedScenario":"signed-scenario"}
        """);
    receive("{\"type\":\"Token\",\"token\":\"connector-token\"}");

    assertThat(token.get(5, TimeUnit.SECONDS)).isEqualTo("connector-token");
    verify(connector).secureSendApdu("signed-scenario");
    verify(connector, never()).stopCardSession(any());
    assertScenarioResponses(List.of("9000"));
    verifyNoInteractions(card, virtualCard);
    verify(transport, never()).getSslSession();
  }

  @Test
  void connectorMockUsesEmbeddedScenarioAndFlagFromOriginRequestContext() throws Exception {
    final var token = registry.registerTokenWaiter("active");
    registry.associatePendingTokenWithConnection("active", client);
    registry.registerRequestContext("active", CardConnectionType.UNKNOWN).setConnectorMock(true);
    final var otherClient = mock(SecureWebSocketClient.class);
    final var otherToken = registry.registerTokenWaiter("other");
    registry.associatePendingTokenWithConnection("other", otherClient);
    registry.registerRequestContext("other", CardConnectionType.CONTACT_CONNECTOR);
    when(card.process(STEPS)).thenReturn(List.of("9000"));
    final var payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"message\":" + STANDARD_SCENARIO + "}").getBytes(StandardCharsets.UTF_8));

    receive(
        """
        {"type":"ConnectorScenario","version":"1.0.0","signedScenario":"header.%s.signature"}
        """
            .formatted(payload));
    receive("{\"type\":\"Token\",\"token\":\"mock-token\"}");

    assertThat(token.get(5, TimeUnit.SECONDS)).isEqualTo("mock-token");
    verify(card).process(STEPS);
    assertScenarioResponses(List.of("9000"));
    assertThat(otherToken).isNotDone();
    assertThat(registry.getPendingSessionIdForConnection(otherClient)).contains("other");
    verifyNoInteractions(connector, virtualCard);
    verify(transport, never()).getSslSession();
  }

  @Test
  void invalidConnectorMockScenarioFailsRequestWithoutSendingResponse() {
    final var token = registry.registerTokenWaiter("active");
    registry.associatePendingTokenWithConnection("active", client);
    registry.registerRequestContext("active", CardConnectionType.UNKNOWN).setConnectorMock(true);

    receive(
        """
        {"type":"ConnectorScenario","version":"1.0.0","signedScenario":"invalid-token"}
        """);

    assertThatThrownBy(() -> token.get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage("Invalid token format");
    assertThat(registry.getRequestContext("active")).isNull();
    assertThat(registry.getPendingSessionIdForConnection(client)).isEmpty();
    verifyNoInteractions(card, virtualCard, connector, transport);
  }

  @Test
  void mismatchedScenarioFailsOriginRequestWithoutProcessingAnotherRequestsSteps() {
    final var token = registry.registerTokenWaiter("active");
    registry.associatePendingTokenWithConnection("active", client);
    registry.registerRequestContext("active", CardConnectionType.CONTACT_STANDARD);
    final var otherClient = mock(SecureWebSocketClient.class);
    final var otherToken = registry.registerTokenWaiter("other");
    registry.associatePendingTokenWithConnection("other", otherClient);
    final var otherContext =
        registry.registerRequestContext("other", CardConnectionType.CONTACT_STANDARD);

    receive(STANDARD_SCENARIO.replace("\"active\"", "\"other\""));

    assertThatThrownBy(() -> token.get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Server message clientSessionId does not match the pending request on this connection");
    assertThat(registry.getPendingSessionIdForConnection(client)).isEmpty();
    assertThat(otherToken).isNotDone();
    assertThat(registry.getPendingSessionIdForConnection(otherClient)).contains("other");
    assertThat(registry.getRequestContext("other")).isSameAs(otherContext);
    verifyNoInteractions(card, virtualCard, connector, transport);
  }

  @Test
  void errorFailsOnlyTheOriginRequestWithServerDetails() {
    final var token = registry.registerTokenWaiter("active");
    registry.associatePendingTokenWithConnection("active", client);
    registry.registerRequestContext("active", CardConnectionType.CONTACT_STANDARD);
    final var otherClient = mock(SecureWebSocketClient.class);
    final var otherToken = registry.registerTokenWaiter("other");
    registry.associatePendingTokenWithConnection("other", otherClient);
    registry.registerRequestContext("other", CardConnectionType.CONTACT_STANDARD);

    receive(
        """
        {"type":"Error","errorCode":"79100","errorDetail":"UnknownCertificates"}
        """);

    assertThatThrownBy(() -> token.get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("Server error 79100: UnknownCertificates");
    assertThat(registry.getPendingSessionIdForConnection(client)).isEmpty();
    assertThat(registry.getRequestContext("active")).isNull();
    assertThat(otherToken).isNotDone();
    assertThat(registry.getPendingSessionIdForConnection(otherClient)).contains("other");
    verifyNoInteractions(card, virtualCard, connector, transport);
  }

  @Test
  void tokenReceivedBeforeCloseCompletesWithoutConnectorCleanupInMessageHandler() throws Exception {
    final var token = registry.registerTokenWaiter("active");
    registry.associatePendingTokenWithConnection("active", client);
    registry.registerRequestContext("active", CardConnectionType.CONTACT_CONNECTOR);
    receive("{\"type\":\"Token\",\"token\":\"connector-token\"}");
    service.handleConnectionEvents(WebSocketConnectionClosedEvent.forClient(client));

    assertThat(token.get(5, TimeUnit.SECONDS)).isEqualTo("connector-token");
    assertThat(registry.getPendingSessionIdForConnection(client)).isEmpty();
    verifyNoInteractions(connector);
  }

  private void receive(final String json) {
    service.handleServerEvent(
        TextMessageReceivedEvent.builder().payload(json).client(client).build());
  }

  @SafeVarargs
  private void assertScenarioResponses(final List<String>... expectedSteps) {
    final var responses = ArgumentCaptor.forClass(ScenarioResponseMessage.class);
    verify(transport, times(expectedSteps.length)).sendMessage(responses.capture());
    assertThat(responses.getAllValues())
        .extracting(ScenarioResponseMessage::getSteps)
        .containsExactly(expectedSteps);
    assertThat(responses.getAllValues())
        .allSatisfy(
            response ->
                assertThat(
                        mapper.readTree(mapper.writeValueAsString(response)).has("clientSessionId"))
                    .isFalse());
  }
}
