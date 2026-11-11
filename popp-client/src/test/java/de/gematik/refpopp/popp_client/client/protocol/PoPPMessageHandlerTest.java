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

package de.gematik.refpopp.popp_client.client.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.*;
import de.gematik.refpopp.popp_client.client.session.ClientRequestContext;
import de.gematik.refpopp.popp_client.client.session.CommunicationSessionRegistry;
import de.gematik.refpopp.popp_client.client.transport.ClientServerCommunicationService;
import de.gematik.refpopp.popp_client.client.transport.SecureWebSocketClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PoPPMessageHandlerTest {

  @Mock private ClientServerCommunicationService clientServerCommunicationService;
  @Mock private CommunicationSessionRegistry sessionRegistry;
  @Mock private StandardScenarioProcessor standardScenarioProcessor;
  @Mock private ConnectorScenarioProcessor connectorScenarioProcessor;

  private PoPPMessageHandler sut;

  @BeforeEach
  void setUp() {
    sut =
        new PoPPMessageHandler(
            clientServerCommunicationService,
            sessionRegistry,
            standardScenarioProcessor,
            connectorScenarioProcessor);
  }

  @Test
  void handleTokenMessageCompletesTokenWithoutManagingConnectorResources() {
    // given
    final var clientSessionId = "session-id";
    final var client = mock(SecureWebSocketClient.class);
    when(sessionRegistry.getPendingSessionIdForConnection(client))
        .thenReturn(Optional.of(clientSessionId));
    when(sessionRegistry.completeToken(clientSessionId, "token")).thenReturn(true);

    // when
    sut.handle(new TokenMessage("token"), client);

    // then
    verify(sessionRegistry).completeToken(clientSessionId, "token");
    verify(sessionRegistry, never()).getRequestContext(anyString());
    verifyNoInteractions(standardScenarioProcessor, connectorScenarioProcessor);
  }

  @Test
  void handleStandardScenarioMessageDelegatesToProcessorAndSendsResponse() {
    // given
    final var message =
        StandardScenarioMessage.builder()
            .version("1.0.0")
            .clientSessionId("session-id")
            .sequenceCounter(0)
            .timeSpan(0)
            .steps(List.of(new ScenarioStep("00A4040000", List.of("9000"))))
            .build();
    final var clientSessionId = message.getClientSessionId();
    final var context = new ClientRequestContext(clientSessionId);
    when(sessionRegistry.getRequestContext(clientSessionId)).thenReturn(context);
    when(standardScenarioProcessor.process(message, context)).thenReturn(List.of("9000"));

    // when
    sut.handle(message);

    // then
    final var responseCaptor = ArgumentCaptor.forClass(ScenarioResponseMessage.class);
    verify(clientServerCommunicationService).sendMessage(responseCaptor.capture());
    assertThat(responseCaptor.getValue().getSteps()).containsExactly("9000");
    verifyNoInteractions(connectorScenarioProcessor);
  }

  @Test
  void handleConnectorScenarioMessageDelegatesToProcessorAndSendsResponse() {
    // given
    final var clientSessionId = "session-id";
    final var client = mock(SecureWebSocketClient.class);
    final var message = new ConnectorScenarioMessage("1.0.0", "signed-scenario");
    final var context = new ClientRequestContext(clientSessionId);
    when(sessionRegistry.getPendingSessionIdForConnection(client))
        .thenReturn(Optional.of(clientSessionId));
    when(sessionRegistry.getRequestContext(clientSessionId)).thenReturn(context);
    when(connectorScenarioProcessor.process(message, context)).thenReturn(List.of("9000"));

    // when
    sut.handle(message, client);

    // then
    verify(connectorScenarioProcessor).process(message, context);
    final var responseCaptor = ArgumentCaptor.forClass(ScenarioResponseMessage.class);
    verify(clientServerCommunicationService).sendMessage(responseCaptor.capture());
    assertThat(responseCaptor.getValue().getSteps()).containsExactly("9000");
    verifyNoInteractions(standardScenarioProcessor);
  }

  @Test
  void handleErrorMessageFailsTokenWhenClientSessionIdExists() {
    // given
    final var clientSessionId = "session-id";
    final var client = mock(SecureWebSocketClient.class);
    when(sessionRegistry.getPendingSessionIdForConnection(client))
        .thenReturn(Optional.of(clientSessionId));
    when(sessionRegistry.failToken(eq(clientSessionId), any(IllegalStateException.class)))
        .thenReturn(true);

    // when
    sut.handle(
        ErrorMessage.builder().errorCode("errorCode").errorDetail("errorDetail").build(), client);

    // then
    final var exceptionCaptor = ArgumentCaptor.forClass(IllegalStateException.class);
    verify(sessionRegistry).failToken(eq(clientSessionId), exceptionCaptor.capture());
    assertThat(exceptionCaptor.getValue()).hasMessage("Server error errorCode: errorDetail");
    verifyNoInteractions(standardScenarioProcessor, connectorScenarioProcessor);
  }

  @Test
  void handleErrorMessageWithoutConnectionFailsExplicitly() {
    assertThatThrownBy(
            () ->
                sut.handle(
                    ErrorMessage.builder()
                        .errorCode("errorCode")
                        .errorDetail("errorDetail")
                        .build()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot correlate");
  }

  @Test
  void handleUnknownMessageDoesNothing() {
    // given
    final PoPPMessage unknownMessage = mock(PoPPMessage.class);
    doReturn(null).when(unknownMessage).getType();

    // when
    sut.handle(unknownMessage);

    // then
    verifyNoInteractions(
        clientServerCommunicationService,
        sessionRegistry,
        standardScenarioProcessor,
        connectorScenarioProcessor);
  }

  @Test
  void handleTokenMessageWithoutConnectionFailsExplicitly() {
    assertThatThrownBy(() -> sut.handle(new TokenMessage("token")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot correlate");
  }

  @Test
  void handleStandardScenarioRejectsIdFromAnotherRequest() {
    final var registry = new CommunicationSessionRegistry();
    final var client = mock(SecureWebSocketClient.class);
    final var otherClient = mock(SecureWebSocketClient.class);
    final var activeToken = registry.registerTokenWaiter("active");
    final var otherToken = registry.registerTokenWaiter("other");
    registry.associatePendingTokenWithConnection("active", client);
    registry.associatePendingTokenWithConnection("other", otherClient);
    final var activeContext =
        registry.registerRequestContext("active", CardConnectionType.CONTACT_STANDARD);
    final var otherContext =
        registry.registerRequestContext("other", CardConnectionType.CONTACT_STANDARD);
    final var handler =
        new PoPPMessageHandler(
            clientServerCommunicationService,
            registry,
            standardScenarioProcessor,
            connectorScenarioProcessor);
    final var scenario =
        StandardScenarioMessage.builder()
            .version("1.0.0")
            .clientSessionId("other")
            .steps(List.of())
            .build();

    assertThatThrownBy(() -> handler.handle(scenario, client))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not match");

    assertThat(activeToken).isNotDone();
    assertThat(otherToken).isNotDone();
    assertThat(registry.getPendingSessionIdForConnection(client)).contains("active");
    assertThat(registry.getPendingSessionIdForConnection(otherClient)).contains("other");
    assertThat(registry.getRequestContext("active")).isSameAs(activeContext);
    assertThat(registry.getRequestContext("other")).isSameAs(otherContext);
    verifyNoInteractions(
        clientServerCommunicationService, standardScenarioProcessor, connectorScenarioProcessor);
  }

  @Test
  void handleTokenWithoutPendingRequestOnConnectionFailsExplicitly() {
    final var client = mock(SecureWebSocketClient.class);
    when(sessionRegistry.getPendingSessionIdForConnection(client)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.handle(new TokenMessage("token"), client))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No pending token request");
  }

  @Test
  void handleErrorWithoutPendingRequestOnConnectionFailsExplicitly() {
    final var client = mock(SecureWebSocketClient.class);
    when(sessionRegistry.getPendingSessionIdForConnection(client)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                sut.handle(
                    ErrorMessage.builder().errorCode("79100").errorDetail("failed").build(),
                    client))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No pending token request");

    verify(sessionRegistry, never()).failToken(any(), any());
  }

  @Test
  void idlessTokenCompletesOnlyWaiterOnOriginConnection() {
    final var registry = new CommunicationSessionRegistry();
    final var firstClient = mock(SecureWebSocketClient.class);
    final var secondClient = mock(SecureWebSocketClient.class);
    final var firstToken = registry.registerTokenWaiter("first");
    final var secondToken = registry.registerTokenWaiter("second");
    registry.associatePendingTokenWithConnection("first", firstClient);
    registry.associatePendingTokenWithConnection("second", secondClient);
    registry.registerRequestContext("first", CardConnectionType.CONTACT_CONNECTOR);
    final var secondContext =
        registry.registerRequestContext("second", CardConnectionType.CONTACT_CONNECTOR);
    final var handler =
        new PoPPMessageHandler(
            clientServerCommunicationService,
            registry,
            standardScenarioProcessor,
            connectorScenarioProcessor);

    handler.handle(new TokenMessage("first-token"), firstClient);

    assertThat(firstToken).isCompletedWithValue("first-token");
    assertThat(secondToken).isNotDone();
    assertThat(registry.getPendingSessionIdForConnection(secondClient)).contains("second");
    assertThat(registry.getRequestContext("first")).isNull();
    assertThat(registry.getRequestContext("second")).isSameAs(secondContext);
    verifyNoInteractions(standardScenarioProcessor, connectorScenarioProcessor);
  }

  @Test
  void idlessErrorFailsOnlyWaiterOnOriginConnection() {
    final var registry = new CommunicationSessionRegistry();
    final var firstClient = mock(SecureWebSocketClient.class);
    final var secondClient = mock(SecureWebSocketClient.class);
    final var firstToken = registry.registerTokenWaiter("first");
    final var secondToken = registry.registerTokenWaiter("second");
    registry.associatePendingTokenWithConnection("first", firstClient);
    registry.associatePendingTokenWithConnection("second", secondClient);
    registry.registerRequestContext("first", CardConnectionType.CONTACT_STANDARD);
    final var secondContext =
        registry.registerRequestContext("second", CardConnectionType.CONTACT_STANDARD);
    final var handler =
        new PoPPMessageHandler(
            clientServerCommunicationService,
            registry,
            standardScenarioProcessor,
            connectorScenarioProcessor);

    handler.handle(
        ErrorMessage.builder().errorCode("79100").errorDetail("failed").build(), firstClient);

    assertThat(firstToken).isCompletedExceptionally();
    assertThat(firstToken.exceptionNow())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Server error 79100: failed");
    assertThat(secondToken).isNotDone();
    assertThat(registry.getPendingSessionIdForConnection(secondClient)).contains("second");
    assertThat(registry.getRequestContext("first")).isNull();
    assertThat(registry.getRequestContext("second")).isSameAs(secondContext);
  }

  @Test
  void handleTokenMessageDoesNotRequireRequestContext() {
    // given
    final var clientSessionId = "session-id";
    final var client = mock(SecureWebSocketClient.class);
    when(sessionRegistry.getPendingSessionIdForConnection(client))
        .thenReturn(Optional.of(clientSessionId));
    when(sessionRegistry.completeToken(clientSessionId, "token")).thenReturn(true);

    // when
    sut.handle(new TokenMessage("token"), client);

    // then
    verify(sessionRegistry).completeToken(clientSessionId, "token");
    verify(sessionRegistry, never()).getRequestContext(anyString());
    verifyNoInteractions(standardScenarioProcessor, connectorScenarioProcessor);
  }

  @Test
  void handleStandardScenarioWithNullContextStillSendsResponse() {
    // given
    final var message =
        StandardScenarioMessage.builder()
            .version("1.0.0")
            .clientSessionId("session-id")
            .sequenceCounter(0)
            .timeSpan(0)
            .steps(List.of(new ScenarioStep("00A4040000", List.of("9000"))))
            .build();
    when(sessionRegistry.getRequestContext(message.getClientSessionId())).thenReturn(null);
    when(standardScenarioProcessor.process(message, null)).thenReturn(List.of("9000"));

    // when
    sut.handle(message);

    // then
    final var responseCaptor = ArgumentCaptor.forClass(ScenarioResponseMessage.class);
    verify(clientServerCommunicationService).sendMessage(responseCaptor.capture());
    assertThat(responseCaptor.getValue().getSteps()).containsExactly("9000");
  }
}
