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

package de.gematik.refpopp.popp_client.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.ScenarioResponseMessage;
import de.gematik.refpopp.popp_client.client.transport.ClientServerCommunicationService;
import de.gematik.refpopp.popp_client.client.transport.SecureWebSocketClient;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class ClientServerCommunicationServiceTest {

  private ClientServerCommunicationService sut;

  @Mock private ObjectMapper objectMapperMock;
  @Mock private SecureWebSocketClient webSocketClientMock;
  @Mock private ObjectProvider<SecureWebSocketClient> webSocketClientProviderMock;

  private AutoCloseable autoCloseable;

  @BeforeEach
  void setUp() {
    autoCloseable = MockitoAnnotations.openMocks(this);
    when(webSocketClientProviderMock.getObject()).thenReturn(webSocketClientMock);
    sut = new ClientServerCommunicationService(objectMapperMock, webSocketClientProviderMock);
  }

  @AfterEach
  void tearDown() throws Exception {
    autoCloseable.close();
  }

  @Test
  void alreadyConnected() {
    // given
    when(webSocketClientMock.isClosed()).thenReturn(false);

    // when
    sut.connect(CardConnectionType.CONTACT_VIRTUAL);
    when(webSocketClientMock.isOpen()).thenReturn(true);
    sut.connect(CardConnectionType.CONTACT_VIRTUAL);

    // then
    verify(webSocketClientMock).connectBlocking(CardConnectionType.CONTACT_VIRTUAL);
    verify(webSocketClientMock, times(1)).connectBlocking(CardConnectionType.CONTACT_VIRTUAL);
  }

  @Test
  void reconnectsOnlyWhenZetaAuthenticationChanges() {
    final var connectorClient = mock(SecureWebSocketClient.class);
    final var nextStandardClient = mock(SecureWebSocketClient.class);
    when(webSocketClientProviderMock.getObject())
        .thenReturn(webSocketClientMock, connectorClient, nextStandardClient);
    when(webSocketClientMock.isOpen()).thenReturn(true);
    when(connectorClient.isOpen()).thenReturn(true);

    sut.connect(CardConnectionType.CONTACT_VIRTUAL);
    sut.connect(CardConnectionType.CONTACT_CONNECTOR);
    sut.connect(CardConnectionType.CONTACTLESS_CONNECTOR);
    sut.connect(CardConnectionType.CONTACT_STANDARD);

    verify(webSocketClientMock).close();
    verify(connectorClient).connectBlocking(CardConnectionType.CONTACT_CONNECTOR);
    verify(connectorClient, never()).connectBlocking(CardConnectionType.CONTACTLESS_CONNECTOR);
    verify(connectorClient).close();
    verify(nextStandardClient).connectBlocking(CardConnectionType.CONTACT_STANDARD);
    verify(webSocketClientProviderMock, times(3)).getObject();
    assertThat(sut.getCurrentWebSocketClient()).isSameAs(nextStandardClient);
  }

  @Test
  void connectSuccessfully() {
    // given
    when(webSocketClientMock.isClosed()).thenReturn(true);

    // when
    sut.connect(CardConnectionType.CONTACT_VIRTUAL);

    // then
    verify(webSocketClientMock).connectBlocking(CardConnectionType.CONTACT_VIRTUAL);
    verify(webSocketClientProviderMock).getObject();
  }

  @Test
  void connectRethrowsRuntimeExceptionFromWebSocketClient() {
    // given
    when(webSocketClientMock.isClosed()).thenReturn(true);
    final var connectionException = new RuntimeException("Connection failed");
    doThrow(connectionException)
        .when(webSocketClientMock)
        .connectBlocking(CardConnectionType.CONTACT_VIRTUAL);

    // when / then
    final var thrown =
        assertThrows(RuntimeException.class, () -> sut.connect(CardConnectionType.CONTACT_VIRTUAL));
    org.assertj.core.api.Assertions.assertThat(thrown).isSameAs(connectionException);
    verify(webSocketClientMock).close();
  }

  @Test
  void disconnectClosesCurrentWebSocketClient() {
    // given
    when(webSocketClientMock.isOpen()).thenReturn(true);
    sut.connect(CardConnectionType.CONTACT_VIRTUAL);

    // when
    sut.disconnect();

    // then
    verify(webSocketClientMock).close();
  }

  @Test
  void disconnectWithoutOpenClientIsNoOp() {
    // when
    sut.disconnect();

    // then
    verifyNoInteractions(webSocketClientMock);
  }

  @Test
  void sendMessageSuccessfully() {
    // given
    when(webSocketClientMock.isClosed()).thenReturn(false);
    when(objectMapperMock.writeValueAsString(any())).thenReturn("message");
    sut.connect(CardConnectionType.CONTACT_VIRTUAL);
    final var responseMessage = new ScenarioResponseMessage(List.of("9000", "abcdef"));

    // when
    sut.sendMessage(responseMessage);

    // then
    verify(webSocketClientMock).send("message");
  }

  @Test
  void sendMessageThrowsExceptionBecauseNoConnectionEstablished() {
    // given
    when(objectMapperMock.writeValueAsString(any())).thenReturn("message");
    final var responseMessage = new ScenarioResponseMessage(List.of("9000", "abcdef"));

    // when
    assertThrows(IllegalStateException.class, () -> sut.sendMessage(responseMessage));

    // then
    verify(webSocketClientMock, never()).send("message");
  }

  @Test
  void sendMessageThrowsExceptionBecauseMessageCouldNotBeConverted() {
    // given
    when(webSocketClientMock.isClosed()).thenReturn(false);
    when(objectMapperMock.writeValueAsString(any()))
        .thenThrow(new JacksonException("Test exception") {});
    final var responseMessage = new ScenarioResponseMessage(List.of("9000", "abcdef"));

    // when
    assertThrows(IllegalStateException.class, () -> sut.sendMessage(responseMessage));

    // then
    verify(webSocketClientMock, never()).send("message");
  }

  @Test
  void getSSLSession() {
    // given
    sut.connect(CardConnectionType.CONTACT_VIRTUAL);
    when(webSocketClientMock.getSSLSession()).thenReturn(null);

    // when
    sut.getSslSession();

    // then
    verify(webSocketClientMock).getSSLSession();
  }

  @Test
  void connectDoesNotCallConnectBlockingWhenSocketIsNotClosed() {
    // given
    when(webSocketClientMock.isClosed()).thenReturn(false);
    when(webSocketClientMock.isOpen()).thenReturn(true);

    // when
    sut.connect(CardConnectionType.CONTACT_VIRTUAL);

    // then
    verify(webSocketClientMock).connectBlocking(CardConnectionType.CONTACT_VIRTUAL);
  }

  @Test
  void sendMessageThrowsExceptionWhenSocketBecomesClosed() {
    // given
    when(webSocketClientMock.isClosed()).thenReturn(false);
    when(objectMapperMock.writeValueAsString(any())).thenReturn("message");
    sut.connect(CardConnectionType.CONTACT_VIRTUAL);
    when(webSocketClientMock.isClosed()).thenReturn(true);
    final var responseMessage = new ScenarioResponseMessage(List.of("9000", "abcdef"));

    // when & then
    assertThrows(IllegalStateException.class, () -> sut.sendMessage(responseMessage));
  }

  @Test
  void connectBothIsClosedAndIsOpenFalseBehavior() {
    // given
    when(webSocketClientMock.isClosed()).thenReturn(false);
    when(webSocketClientMock.isOpen()).thenReturn(false);

    // when
    sut.connect(CardConnectionType.CONTACT_VIRTUAL);

    // then
    verify(webSocketClientMock).connectBlocking(CardConnectionType.CONTACT_VIRTUAL);
  }

  @Test
  void invalidatedClientIsRemovedFromConnectionCache() {
    when(webSocketClientMock.isOpen()).thenReturn(false);
    ArgumentCaptor<Consumer<SecureWebSocketClient>> invalidationListenerCaptor =
        ArgumentCaptor.forClass(Consumer.class);

    sut.connect(CardConnectionType.CONTACT_VIRTUAL);
    verify(webSocketClientMock).setInvalidationListener(invalidationListenerCaptor.capture());

    invalidationListenerCaptor.getValue().accept(webSocketClientMock);
    sut.connect(CardConnectionType.CONTACT_VIRTUAL);

    verify(webSocketClientProviderMock, times(2)).getObject();
    verify(webSocketClientMock, times(2)).connectBlocking(CardConnectionType.CONTACT_VIRTUAL);
  }

  @Test
  void invalidatedStaleClientDoesNotRemoveNewConnectionFromCache() {
    SecureWebSocketClient replacementClientMock = mock(SecureWebSocketClient.class);
    when(webSocketClientProviderMock.getObject())
        .thenReturn(webSocketClientMock, replacementClientMock);
    when(webSocketClientMock.isOpen()).thenReturn(false);
    when(replacementClientMock.isOpen()).thenReturn(true);
    ArgumentCaptor<Consumer<SecureWebSocketClient>> invalidationListenerCaptor =
        ArgumentCaptor.forClass(Consumer.class);

    sut.connect(CardConnectionType.CONTACT_VIRTUAL);
    verify(webSocketClientMock).setInvalidationListener(invalidationListenerCaptor.capture());
    sut.connect(CardConnectionType.CONTACT_VIRTUAL);

    invalidationListenerCaptor.getValue().accept(webSocketClientMock);
    sut.connect(CardConnectionType.CONTACT_VIRTUAL);

    verify(webSocketClientProviderMock, times(2)).getObject();
    verify(replacementClientMock, times(1)).connectBlocking(CardConnectionType.CONTACT_VIRTUAL);
  }

  @Test
  void sendMessageRequiresEstablishedConnection() {
    // given
    when(webSocketClientMock.isClosed()).thenReturn(true);
    when(objectMapperMock.writeValueAsString(any())).thenReturn("message");
    sut.connect(CardConnectionType.CONTACT_VIRTUAL);
    final var responseMessage = new ScenarioResponseMessage(List.of("9000", "abcdef"));

    // when & then
    assertThrows(IllegalStateException.class, () -> sut.sendMessage(responseMessage));
    verify(webSocketClientMock, never()).send(any());
  }
}
