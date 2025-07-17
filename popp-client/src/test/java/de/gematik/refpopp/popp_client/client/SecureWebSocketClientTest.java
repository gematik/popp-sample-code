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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.gematik.refpopp.popp_client.client.events.TextMessageReceivedEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketCommunicationErrorEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketConnectionClosedEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketConnectionOpenedEvent;
import java.net.URI;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SecureWebSocketClientTest {

  private SecureWebSocketClient sut;
  private CommunicationEventPublisher eventPublisherMock;

  @BeforeEach
  void setUp() throws Exception {
    eventPublisherMock = mock(CommunicationEventPublisher.class);
    sut =
        new SecureWebSocketClient(
            new URI("wss://example.com"), eventPublisherMock, "popp_keystore.p12", "popp-store");
  }

  @Test
  void initLoadsTrustStoreSuccessfully() throws Exception {
    // given

    // when
    sut.init();

    // then
    assertThat(sut.getReadyState()).isNotNull();
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
}
