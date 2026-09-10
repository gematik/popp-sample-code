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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.*;
import de.gematik.refpopp.popp_client.client.session.ClientRequestContext;
import de.gematik.refpopp.popp_client.client.session.CommunicationSessionRegistry;
import de.gematik.refpopp.popp_client.client.transport.ClientServerCommunicationService;
import de.gematik.refpopp.popp_client.connector.session.ConnectorSessionLifecycle;
import java.util.List;
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
  @Mock private ConnectorSessionLifecycle connectorSessionLifecycle;

  private PoPPMessageHandler sut;

  @BeforeEach
  void setUp() {
    sut =
        new PoPPMessageHandler(
            clientServerCommunicationService,
            sessionRegistry,
            standardScenarioProcessor,
            connectorScenarioProcessor,
            connectorSessionLifecycle);
  }

  @Test
  void handleTokenMessageCompletesTokenAndStopsConnectorSession() {
    // given
    final var clientSessionId = "session-id";
    final var context = new ClientRequestContext(clientSessionId);
    context.setCardConnectionType(CardConnectionType.CONTACT_CONNECTOR);
    when(sessionRegistry.getRequestContext(clientSessionId)).thenReturn(context);
    when(sessionRegistry.completeToken(clientSessionId, "token")).thenReturn(true);

    // when
    sut.handle(new TokenMessage(clientSessionId, "token", "pn"));

    // then
    verify(sessionRegistry).completeToken(clientSessionId, "token");
    verify(connectorSessionLifecycle).stopSessionIfRequired(context);
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
    verifyNoInteractions(connectorScenarioProcessor, connectorSessionLifecycle);
  }

  @Test
  void handleConnectorScenarioMessageDelegatesToProcessorAndSendsResponse() {
    // given
    final var clientSessionId = "session-id";
    final var message = new ConnectorScenarioMessage("1.0.0", "signed-scenario", clientSessionId);
    final var context = new ClientRequestContext(clientSessionId);
    when(sessionRegistry.getRequestContext(clientSessionId)).thenReturn(context);
    when(connectorScenarioProcessor.process(message, context)).thenReturn(List.of("9000"));

    // when
    sut.handle(message);

    // then
    verify(connectorScenarioProcessor).process(message, context);
    final var responseCaptor = ArgumentCaptor.forClass(ScenarioResponseMessage.class);
    verify(clientServerCommunicationService).sendMessage(responseCaptor.capture());
    assertThat(responseCaptor.getValue().getSteps()).containsExactly("9000");
    verifyNoInteractions(standardScenarioProcessor, connectorSessionLifecycle);
  }

  @Test
  void handleErrorMessageFailsTokenWhenClientSessionIdExists() {
    // given
    final var clientSessionId = "session-id";

    // when
    sut.handle(
        ErrorMessage.builder()
            .clientSessionId(clientSessionId)
            .errorCode("errorCode")
            .errorDetail("errorDetail")
            .build());

    // then
    final var exceptionCaptor = ArgumentCaptor.forClass(IllegalStateException.class);
    verify(sessionRegistry).failToken(eq(clientSessionId), exceptionCaptor.capture());
    assertThat(exceptionCaptor.getValue()).hasMessage("Server error errorCode: errorDetail");
    verifyNoInteractions(
        standardScenarioProcessor, connectorScenarioProcessor, connectorSessionLifecycle);
  }

  @Test
  void handleErrorMessageDoesNothingWithoutClientSessionId() {
    // given / when
    sut.handle(ErrorMessage.builder().errorCode("errorCode").errorDetail("errorDetail").build());

    // then
    verifyNoInteractions(
        sessionRegistry,
        standardScenarioProcessor,
        connectorScenarioProcessor,
        connectorSessionLifecycle);
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
        connectorScenarioProcessor,
        connectorSessionLifecycle);
  }

  @Test
  void handleTokenMessageDoesNothingWhenClientSessionIdMissing() {
    // given
    when(sessionRegistry.completeSolePendingToken("token")).thenReturn(false);

    // when
    sut.handle(new TokenMessage("token", "pn"));

    // then
    verify(sessionRegistry).completeSolePendingToken("token");
    verify(sessionRegistry, never()).completeToken(anyString(), anyString());
    verifyNoInteractions(connectorSessionLifecycle, standardScenarioProcessor);
  }

  @Test
  void handleTokenMessageUsesFallbackWhenClientSessionIdDoesNotMatchWaiter() {
    // given
    final var clientSessionId = "session-id";
    final var context = new ClientRequestContext(clientSessionId);
    when(sessionRegistry.getRequestContext(clientSessionId)).thenReturn(context);
    when(sessionRegistry.completeToken(clientSessionId, "token")).thenReturn(false);
    when(sessionRegistry.completeSolePendingToken("token")).thenReturn(true);

    // when
    sut.handle(new TokenMessage(clientSessionId, "token", "pn"));

    // then
    verify(sessionRegistry).completeToken(clientSessionId, "token");
    verify(sessionRegistry).completeSolePendingToken("token");
    verify(connectorSessionLifecycle).stopSessionIfRequired(context);
    verifyNoInteractions(standardScenarioProcessor, connectorScenarioProcessor);
  }

  @Test
  void handleTokenMessageLogsWarningWhenNoWaiterFoundAtAll() {
    // given
    final var clientSessionId = "session-id";
    final var context = new ClientRequestContext(clientSessionId);
    when(sessionRegistry.getRequestContext(clientSessionId)).thenReturn(context);
    when(sessionRegistry.completeToken(clientSessionId, "token")).thenReturn(false);
    when(sessionRegistry.completeSolePendingToken("token")).thenReturn(false);

    // when
    sut.handle(new TokenMessage(clientSessionId, "token", "pn"));

    // then
    verify(sessionRegistry).completeToken(clientSessionId, "token");
    verify(sessionRegistry).completeSolePendingToken("token");
    verify(connectorSessionLifecycle).stopSessionIfRequired(context);
    verifyNoInteractions(standardScenarioProcessor, connectorScenarioProcessor);
  }

  @Test
  void handleTokenMessageWithNullClientSessionIdUsesFallbackSuccessfully() {
    // given
    when(sessionRegistry.completeSolePendingToken("token")).thenReturn(true);

    // when
    sut.handle(new TokenMessage(null, "token", "pn"));

    // then
    verify(sessionRegistry).completeSolePendingToken("token");
    verify(sessionRegistry, never()).completeToken(anyString(), anyString());
    verify(sessionRegistry, never()).getRequestContext(any());
    verify(connectorSessionLifecycle, never()).stopSessionIfRequired(any());
    verifyNoInteractions(standardScenarioProcessor, connectorScenarioProcessor);
  }

  @Test
  void handleTokenMessageWithoutContextDoesNotStopSession() {
    // given
    final var clientSessionId = "session-id";
    when(sessionRegistry.getRequestContext(clientSessionId)).thenReturn(null);
    when(sessionRegistry.completeToken(clientSessionId, "token")).thenReturn(true);

    // when
    sut.handle(new TokenMessage(clientSessionId, "token", "pn"));

    // then
    verify(sessionRegistry).completeToken(clientSessionId, "token");
    verify(connectorSessionLifecycle, never()).stopSessionIfRequired(any());
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
    assertThat(responseCaptor.getValue().getClientSessionId()).isEqualTo("session-id");
  }
}
