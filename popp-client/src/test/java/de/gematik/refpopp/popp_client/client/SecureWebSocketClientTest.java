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

import de.gematik.refpopp.popp_client.client.events.TextMessageReceivedEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketCommunicationErrorEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketConnectionClosedEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketConnectionOpenedEvent;
import de.gematik.zeta.sdk.WsClientExtension;
import de.gematik.zeta.sdk.ZetaSdkClient;
import io.ktor.client.plugins.logging.LogLevel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SecureWebSocketClientTest {

  private SecureWebSocketClient sut;
  private CommunicationEventPublisher eventPublisherMock;
  private WsClientWrapper wsClientWrapperMock;
  private ZetaSdkClient zetaSdkClientMock;

  @BeforeEach
  void setUp() throws Exception {
    eventPublisherMock = mock(CommunicationEventPublisher.class);
    wsClientWrapperMock = mock(WsClientWrapper.class);
    zetaSdkClientMock = mock(ZetaSdkClient.class);
    sut = newClient();
  }

  @Test
  void initLeavesClientInClosedState() {
    sut.init();

    assertThat(sut.getReadyState()).isFalse();
    assertThat(sut.isClosed()).isTrue();
  }

  @Test
  void connectionOpenedEventPublished() {
    ServerHandshake handshake = mock(ServerHandshake.class);

    sut.onOpen(handshake);

    verify(eventPublisherMock).publishEvent(any(WebSocketConnectionOpenedEvent.class));
  }

  @Test
  void textMessageReceivedEventPublished() {
    String message = "Hello, WebSocket!";

    sut.onMessage(message);

    ArgumentCaptor<TextMessageReceivedEvent> captor =
        ArgumentCaptor.forClass(TextMessageReceivedEvent.class);
    verify(eventPublisherMock).publishEvent(captor.capture());
    assertThat(captor.getValue().getPayload()).isEqualTo(message);
  }

  @Test
  void connectionClosedEventPublished() {
    sut.onClose(1000, "Normal closure", true);

    verify(eventPublisherMock).publishEvent(any(WebSocketConnectionClosedEvent.class));
  }

  @Test
  void communicationErrorEventPublished() {
    Exception ex = new Exception("Test error");

    sut.onError(ex);

    ArgumentCaptor<WebSocketCommunicationErrorEvent> captor =
        ArgumentCaptor.forClass(WebSocketCommunicationErrorEvent.class);
    verify(eventPublisherMock).publishEvent(captor.capture());
    assertThat(captor.getValue().getError()).isSameAs(ex);
  }

  @Test
  void connectBlockingOpensSessionAndSendDelegatesToCurrentSession() throws Exception {
    WsClientExtension.WsSession sessionMock = mock(WsClientExtension.WsSession.class);
    CountDownLatch releaseReceive = new CountDownLatch(1);
    CountDownLatch workerFinished = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              releaseReceive.await(1, TimeUnit.SECONDS);
              return mock(WsClientExtension.WsMessage.Close.class);
            })
        .when(sessionMock)
        .receiveNext();

    doAnswer(
            invocation -> {
              WsClientExtension.WsSession.WsHandler handler = invocation.getArgument(4);
              Thread.ofPlatform()
                  .start(
                      () -> {
                        try {
                          handler.handle(sessionMock);
                        } finally {
                          workerFinished.countDown();
                        }
                      });
              return null;
            })
        .when(wsClientWrapperMock)
        .ws(any(), anyString(), any(), anyMap(), any());

    sut.connectBlocking();
    sut.send("Hello, WebSocket!");

    assertThat(sut.isOpen()).isTrue();
    assertThat(sut.isClosed()).isFalse();
    verify(sessionMock).sendText("Hello, WebSocket!");

    releaseReceive.countDown();
    assertThat(workerFinished.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(sut.isClosed()).isTrue();
  }

  @Test
  void connectBlockingSkipsWhenSessionIsAlreadyOpen() throws Exception {
    WsClientExtension.WsSession sessionMock = mock(WsClientExtension.WsSession.class);
    sessionRef(sut).set(sessionMock);

    sut.connectBlocking();

    verify(wsClientWrapperMock, never()).ws(any(), anyString(), any(), anyMap(), any());
    assertThat(sut.isOpen()).isTrue();
  }

  @Test
  void connectBlockingClosesSessionWhenReceiveNextReturnsNull() throws Exception {
    CountDownLatch workerFinished = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              WsClientExtension.WsSession.WsHandler handler = invocation.getArgument(4);
              WsClientExtension.WsSession sessionMock = mock(WsClientExtension.WsSession.class);
              doReturn(null).when(sessionMock).receiveNext();
              try {
                handler.handle(sessionMock);
              } finally {
                workerFinished.countDown();
              }
              return null;
            })
        .when(wsClientWrapperMock)
        .ws(any(), anyString(), any(), anyMap(), any());

    sut.connectBlocking();

    assertThat(workerFinished.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(sut.isClosed()).isTrue();
    assertThat(sut.getSSLSession()).isEmpty();
  }

  @Test
  void connectBlockingRethrowsConnectErrorFromWorkerThread() {
    RuntimeException connectionException = new RuntimeException("Service discovery failed");
    doThrow(connectionException)
        .when(wsClientWrapperMock)
        .ws(any(), anyString(), any(), anyMap(), any());

    assertThatThrownBy(() -> sut.connectBlocking())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to establish WebSocket connection: Service discovery failed")
        .hasCause(connectionException);
  }

  @Test
  void connectBlockingHandlesTextMessage() throws Exception {
    WsClientExtension.WsSession sessionMock = mock(WsClientExtension.WsSession.class);
    WsClientExtension.WsMessage.Text textMsg = mock(WsClientExtension.WsMessage.Text.class);
    WsClientExtension.WsMessage.Close closeMsg = mock(WsClientExtension.WsMessage.Close.class);
    CountDownLatch workerFinished = new CountDownLatch(1);
    doReturn("Hello WebSocket").when(textMsg).getText();
    doReturn(textMsg, closeMsg).when(sessionMock).receiveNext();
    doAnswer(
            invocation -> {
              WsClientExtension.WsSession.WsHandler handler = invocation.getArgument(4);
              try {
                handler.handle(sessionMock);
              } finally {
                workerFinished.countDown();
              }
              return null;
            })
        .when(wsClientWrapperMock)
        .ws(any(), anyString(), any(), anyMap(), any());

    sut.connectBlocking();

    assertThat(workerFinished.await(1, TimeUnit.SECONDS)).isTrue();
    ArgumentCaptor<TextMessageReceivedEvent> captor =
        ArgumentCaptor.forClass(TextMessageReceivedEvent.class);
    verify(eventPublisherMock).publishEvent(captor.capture());
    assertThat(captor.getValue().getPayload()).isEqualTo("Hello WebSocket");
    assertThat(sut.isClosed()).isTrue();
  }

  @Test
  void connectBlockingHandlesBinaryMessageWithoutPublishingTextEvent() throws Exception {
    WsClientExtension.WsSession sessionMock = mock(WsClientExtension.WsSession.class);
    WsClientExtension.WsMessage.Binary binaryMsg = mock(WsClientExtension.WsMessage.Binary.class);
    WsClientExtension.WsMessage.Close closeMsg = mock(WsClientExtension.WsMessage.Close.class);
    CountDownLatch workerFinished = new CountDownLatch(1);
    doReturn(new byte[] {1, 2, 3, 4}).when(binaryMsg).getBytes();
    doReturn(binaryMsg, closeMsg).when(sessionMock).receiveNext();
    doAnswer(
            invocation -> {
              WsClientExtension.WsSession.WsHandler handler = invocation.getArgument(4);
              try {
                handler.handle(sessionMock);
              } finally {
                workerFinished.countDown();
              }
              return null;
            })
        .when(wsClientWrapperMock)
        .ws(any(), anyString(), any(), anyMap(), any());

    sut.connectBlocking();

    assertThat(workerFinished.await(1, TimeUnit.SECONDS)).isTrue();
    verify(eventPublisherMock, never()).publishEvent(any(TextMessageReceivedEvent.class));
    assertThat(sut.isClosed()).isTrue();
  }

  @Test
  void awaitSessionReadyThrowsTimeoutExceptionWhenLatchDoesNotComplete() throws Exception {
    CountDownLatch latchSpy = spy(new CountDownLatch(1));
    setPrivateField(sut, "sessionReady", latchSpy);
    doReturn(false).when(latchSpy).await(anyLong(), any(TimeUnit.class));

    assertThatThrownBy(() -> invokePrivateMethod(sut, "awaitSessionReady"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Connection timeout");
  }

  @Test
  void awaitSessionReadyThrowsIllegalStateExceptionAndInterruptsThreadWhenAwaitIsInterrupted()
      throws Exception {
    CountDownLatch latchSpy = spy(new CountDownLatch(1));
    setPrivateField(sut, "sessionReady", latchSpy);
    doThrow(new InterruptedException("interrupted"))
        .when(latchSpy)
        .await(anyLong(), any(TimeUnit.class));

    Thread.interrupted();

    try {
      assertThatThrownBy(() -> invokePrivateMethod(sut, "awaitSessionReady"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Thread was interrupted while waiting for WebSocket connection")
          .hasCauseInstanceOf(InterruptedException.class);

      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void sendThrowsWhenSessionIsNotOpen() {
    assertThatThrownBy(() -> sut.send("Hello, WebSocket!"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("WebSocket session is not open");
  }

  @Test
  void closeClosesSessionAndClearsState() throws Exception {
    WsClientExtension.WsSession sessionMock = mock(WsClientExtension.WsSession.class);
    sessionRef(sut).set(sessionMock);
    connectErrorRef(sut).set(new RuntimeException("stale"));
    sut.getSSLSession().put("protocol", "TLSv1.3");

    sut.close();

    verify(sessionMock).close();
    assertThat(sut.isClosed()).isTrue();
    assertThat(sut.getSSLSession()).isEmpty();
    assertThat(connectErrorRef(sut).get()).isNull();
  }

  @Test
  void closeSwallowsSessionCloseErrorsAndStillClearsState() throws Exception {
    WsClientExtension.WsSession sessionMock = mock(WsClientExtension.WsSession.class);
    doThrow(new RuntimeException("boom")).when(sessionMock).close();
    sessionRef(sut).set(sessionMock);
    connectErrorRef(sut).set(new RuntimeException("stale"));
    sut.getSSLSession().put("cipher", "AES");

    sut.close();

    verify(sessionMock).close();
    assertThat(sut.isClosed()).isTrue();
    assertThat(sut.getSSLSession()).isEmpty();
    assertThat(connectErrorRef(sut).get()).isNull();
  }

  @Test
  void closeWithoutOpenSessionStillClearsState() throws Exception {
    connectErrorRef(sut).set(new RuntimeException("stale"));
    sut.getSSLSession().put("peer", "server");

    sut.close();

    assertThat(sut.isClosed()).isTrue();
    assertThat(sut.getSSLSession()).isEmpty();
    assertThat(connectErrorRef(sut).get()).isNull();
  }

  @Test
  void connectBlockingHandlesInterruptedExceptionInWorkerThread() {
    doAnswer(
            invocation -> {
              Thread.currentThread().interrupt();
              throw new InterruptedException("Worker thread interrupted");
            })
        .when(wsClientWrapperMock)
        .ws(any(), anyString(), any(), anyMap(), any());

    sut.connectBlocking();

    assertThat(sut.isClosed()).isTrue();
  }

  @Test
  void getReadyStateReflectsSessionState() throws Exception {
    assertThat(sut.getReadyState()).isFalse();

    WsClientExtension.WsSession sessionMock = mock(WsClientExtension.WsSession.class);
    sessionRef(sut).set(sessionMock);

    assertThat(sut.getReadyState()).isTrue();

    sessionRef(sut).set(null);

    assertThat(sut.getReadyState()).isFalse();
  }

  @Test
  void isClosedAndIsOpenAreConsistent() throws Exception {
    assertThat(sut.isClosed()).isTrue();
    assertThat(sut.isOpen()).isFalse();

    WsClientExtension.WsSession sessionMock = mock(WsClientExtension.WsSession.class);
    sessionRef(sut).set(sessionMock);

    assertThat(sut.isClosed()).isFalse();
    assertThat(sut.isOpen()).isTrue();
  }

  @Test
  void constructorInitializesAllFields() {
    assertThat(sut.isClosed()).isTrue();
    assertThat(sut.isOpen()).isFalse();
    assertThat(sut.getSSLSession()).isEmpty();
    assertThat(sut.getReadyState()).isFalse();
  }

  private SecureWebSocketClient newClient() throws Exception {
    return new SecureWebSocketClient(
        new URI("wss://example.com"),
        eventPublisherMock,
        false,
        LogLevel.NONE,
        zetaSdkClientMock,
        wsClientWrapperMock);
  }

  @SuppressWarnings("unchecked")
  private AtomicReference<WsClientExtension.WsSession> sessionRef(SecureWebSocketClient target)
      throws Exception {
    return (AtomicReference<WsClientExtension.WsSession>) getPrivateField(target, "session");
  }

  @SuppressWarnings("unchecked")
  private AtomicReference<Throwable> connectErrorRef(SecureWebSocketClient target)
      throws Exception {
    return (AtomicReference<Throwable>) getPrivateField(target, "connectError");
  }

  private Object getPrivateField(Object target, String fieldName) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
  }

  private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private Object invokePrivateMethod(Object target, String methodName) throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName);
    method.setAccessible(true);
    try {
      return method.invoke(target);
    } catch (ReflectiveOperationException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw e;
    }
  }
}
