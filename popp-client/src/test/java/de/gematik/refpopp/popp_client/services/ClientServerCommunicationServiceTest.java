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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.messages.ScenarioResponseMessage;
import de.gematik.refpopp.popp_client.client.ClientServerCommunicationService;
import de.gematik.refpopp.popp_client.client.SecureWebSocketClient;
import de.gematik.refpopp.popp_client.configuration.WebSocketConfig;
import java.util.List;
import org.java_websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class ClientServerCommunicationServiceTest {

  private ClientServerCommunicationService sut;

  @Mock private ObjectMapper objectMapperMock;
  @Mock private SecureWebSocketClient webSocketClientMock;
  @Mock private ObjectProvider<WebSocketClient> webSocketClientProviderMock;

  @Mock private WebSocketConfig webSocketConfig;

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
  void alreadyConnected() throws InterruptedException {
    // given
    when(webSocketClientMock.isOpen()).thenReturn(true);

    // when
    sut.connect();

    // then
    verify(webSocketClientMock, never()).connectBlocking();
  }

  @Test
  void connectSuccessfully() throws InterruptedException {
    // given
    when(webSocketClientMock.isClosed()).thenReturn(true);

    // when
    sut.connect();

    // then
    verify(webSocketClientMock).connectBlocking();
    verify(webSocketClientProviderMock).getObject();
  }

  @Test
  void connectHandlesInterruptedException() throws InterruptedException {
    // given
    when(webSocketClientMock.isClosed()).thenReturn(true);
    doThrow(new InterruptedException("Test exception")).when(webSocketClientMock).connectBlocking();

    // when
    sut.connect();

    // then
    verify(webSocketClientMock).connectBlocking();
    assertTrue(Thread.currentThread().isInterrupted());
  }

  @Test
  void sendMessageSuccessfully() {
    // given
    when(webSocketClientMock.isClosed()).thenReturn(false);
    when(objectMapperMock.writeValueAsString(any())).thenReturn("message");
    sut.connect();
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
    sut.connect();
    when(webSocketClientMock.getSSLSession()).thenReturn(null);

    // when
    sut.getSSLSession();

    // then
    verify(webSocketClientMock).getSSLSession();
  }
}
