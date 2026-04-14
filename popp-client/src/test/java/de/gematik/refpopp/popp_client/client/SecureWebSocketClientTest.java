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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import de.gematik.refpopp.popp_client.client.events.TextMessageReceivedEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketCommunicationErrorEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketConnectionClosedEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketConnectionOpenedEvent;
import de.gematik.refpopp.popp_client.configuration.PathResolver;
import de.gematik.zeta.sdk.WsClientExtension;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SecureWebSocketClientTest {

  private SecureWebSocketClient sut;
  private CommunicationEventPublisher eventPublisherMock;
  private WsClientWrapper wsClientWrapperMock;

  @BeforeEach
  void setUp() throws Exception {
    eventPublisherMock = mock(CommunicationEventPublisher.class);
    wsClientWrapperMock = mock(WsClientWrapper.class);
    final var smcbPrivateP12Path =
        PathResolver.resolveAgainstWorkingDirectoryAncestors(
                "docker/zeta/smcb-private/smcb_private.p12")
            .toString();
    sut =
        new SecureWebSocketClient(
            new URI("wss://example.com"),
            eventPublisherMock,
            smcbPrivateP12Path,
            "alias",
            "00",
            false,
            wsClientWrapperMock);
  }

  @Test
  void initLoadsTrustStoreSuccessfully() {
    // given

    // when
    sut.init();

    // then
    assertThat(sut.getReadyState()).isFalse();
  }

  @Test
  void connectionOpenedEventPublished() {
    // given
    final var handshake = mock(ServerHandshake.class);

    // when
    sut.onOpen(handshake);

    // then
    verify(eventPublisherMock).publishEvent(any(WebSocketConnectionOpenedEvent.class));
  }

  @Test
  void textMessageReceivedEventPublished() {
    // given
    final var message = "Hello, WebSocket!";

    // when
    sut.onMessage(message);

    // then
    final var captor = ArgumentCaptor.forClass(TextMessageReceivedEvent.class);
    verify(eventPublisherMock).publishEvent(captor.capture());
    assertThat(captor.getValue().getPayload()).isEqualTo(message);
  }

  @Test
  void connectionClosedEventPublished() {
    // given

    // when
    sut.onClose(1000, "Normal closure", true);

    // then
    verify(eventPublisherMock).publishEvent(any(WebSocketConnectionClosedEvent.class));
  }

  @Test
  void communicationErrorEventPublished() {
    // given
    final var ex = new Exception("Test error");

    // when
    sut.onError(ex);

    // then
    final var captor = ArgumentCaptor.forClass(WebSocketCommunicationErrorEvent.class);
    verify(eventPublisherMock).publishEvent(captor.capture());
    assertThat(captor.getValue().getError()).isSameAs(ex);
  }

  @Test
  void testConnectBlocking() {
    // given
    doAnswer(
            invocation -> {
              WsClientExtension.WsSession.WsHandler handler = invocation.getArgument(4);

              WsClientExtension.WsSession sessionMock = mock(WsClientExtension.WsSession.class);
              when(sessionMock.receiveNext()).thenReturn(null);

              handler.handle(sessionMock);

              return null;
            })
        .when(wsClientWrapperMock)
        .ws(any(), anyString(), any(), anyMap(), any());

    // when
    sut.connectBlocking();
    sut.send("Hello, WebSocket!");

    // then
    verify(wsClientWrapperMock).ws(any(), anyString(), any(), anyMap(), any());
    assertThat(sut.isOpen()).isTrue();
    assertThat(sut.getSSLSession()).isEmpty();
    assertThat(sut.isClosed()).isFalse();
  }

  @Test
  void testConnectBlocking_timeout() throws Exception {
    // given
    var latchSpy = spy(new CountDownLatch(1));
    setPrivateField(sut, "sessionReady", latchSpy);

    doAnswer(
            invocation -> {
              WsClientExtension.WsSession.WsHandler handler = invocation.getArgument(4);
              WsClientExtension.WsSession sessionMock = mock(WsClientExtension.WsSession.class);
              when(sessionMock.receiveNext()).thenReturn(null);
              handler.handle(sessionMock);
              return null;
            })
        .when(wsClientWrapperMock)
        .ws(any(), anyString(), any(), anyMap(), any());

    doReturn(false).when(latchSpy).await(anyLong(), any());

    // when / then
    RuntimeException ex = assertThrows(RuntimeException.class, () -> sut.connectBlocking());
    assertThat(ex).hasMessage("Connection timeout");
  }

  @Test
  void testConnectBlocking_interrupted() throws Exception {
    // given
    var latchSpy = spy(new CountDownLatch(1));
    setPrivateField(sut, "sessionReady", latchSpy);

    doAnswer(
            invocation -> {
              WsClientExtension.WsSession.WsHandler handler = invocation.getArgument(4);
              WsClientExtension.WsSession sessionMock = mock(WsClientExtension.WsSession.class);
              when(sessionMock.receiveNext()).thenReturn(null);
              handler.handle(sessionMock);
              return null;
            })
        .when(wsClientWrapperMock)
        .ws(any(), anyString(), any(), anyMap(), any());

    doThrow(new InterruptedException()).when(latchSpy).await(anyLong(), any());

    // when / then
    var ex = assertThrows(RuntimeException.class, () -> sut.connectBlocking());
    assertThat(ex.getCause()).isInstanceOf(InterruptedException.class);
  }

  @Test
  void testConnectBlocking_handlesTextMessage() throws Exception {
    // given
    var sessionMock = mock(WsClientExtension.WsSession.class);
    var textMsg = mock(WsClientExtension.WsMessage.Text.class);
    when(textMsg.getText()).thenReturn("Hello WebSocket");
    var closeMsg = mock(WsClientExtension.WsMessage.Close.class);
    when(sessionMock.receiveNext()).thenReturn(textMsg, closeMsg);

    SecureWebSocketClient sutSpy = spy(sut);

    var onMessageLatch = new CountDownLatch(1);
    doAnswer(
            inv -> {
              onMessageLatch.countDown();
              return null;
            })
        .when(sutSpy)
        .onMessage(any());

    doAnswer(
            invocation -> {
              WsClientExtension.WsSession.WsHandler handler = invocation.getArgument(4);
              handler.handle(sessionMock);
              return null;
            })
        .when(wsClientWrapperMock)
        .ws(any(), anyString(), any(), anyMap(), any());

    // when
    sutSpy.connectBlocking();

    // then
    assertThat(onMessageLatch.await(1, TimeUnit.SECONDS)).isTrue();
    verify(sutSpy).onMessage("Hello WebSocket");
    assertThat(sutSpy.isOpen()).isTrue();
  }

  @Test
  void testConnectBlocking_handlesBinaryMessage() throws Exception {
    // given
    var sessionMock = mock(WsClientExtension.WsSession.class);
    var bytes = new byte[] {1, 2, 3, 4};
    var binaryMsg = mock(WsClientExtension.WsMessage.Binary.class);
    when(binaryMsg.getBytes()).thenReturn(bytes);

    var closeMsg = mock(WsClientExtension.WsMessage.Close.class);

    when(sessionMock.receiveNext()).thenReturn(binaryMsg, closeMsg);

    var latch = new CountDownLatch(1);
    SecureWebSocketClient sutSpy = spy(sut);
    setPrivateField(sutSpy, "sessionReady", latch);

    doAnswer(
            invocation -> {
              WsClientExtension.WsSession.WsHandler handler = invocation.getArgument(4);
              handler.handle(sessionMock);
              return null;
            })
        .when(wsClientWrapperMock)
        .ws(any(), anyString(), any(), anyMap(), any());

    // when
    sutSpy.connectBlocking();

    // then
    verify(sutSpy, never()).onMessage(any());
    assertThat(sutSpy.isOpen()).isTrue();
  }

  private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
