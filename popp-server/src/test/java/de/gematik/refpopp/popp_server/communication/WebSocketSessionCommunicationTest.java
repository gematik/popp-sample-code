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

package de.gematik.refpopp.popp_server.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.poppcommons.api.messages.TokenMessage;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class WebSocketSessionCommunicationTest {

  private WebSocketSessionCommunication sut;
  private WebSocketSession webSocketSessionMock;

  @BeforeEach
  void setUp() {
    webSocketSessionMock = mock(WebSocketSession.class);
    sut = new WebSocketSessionCommunication(webSocketSessionMock);
  }

  @Test
  void sendMessage() throws IOException {
    // given
    final var message = new TokenMessage("token", "pn");
    final var argumentCaptor = ArgumentCaptor.forClass(TextMessage.class);

    // when
    sut.sendMessage(message);

    // then
    verify(webSocketSessionMock).sendMessage(argumentCaptor.capture());
    final var textMessage = argumentCaptor.getValue();
    assertThat(textMessage.getPayload())
        .isEqualTo(
            """
            {"type":"Token","token":"token","pn":"pn"}\
            """);
  }

  @Test
  void sendMessageThrowsException() throws IOException {
    // given
    final var message = new TokenMessage("token", "pn");
    doThrow(new IOException("error message")).when(webSocketSessionMock).sendMessage(any());

    // when
    final var scenarioException =
        assertThrows(ScenarioException.class, () -> sut.sendMessage(message));

    // then
    assertThat(scenarioException.getErrorCode()).isEqualTo("errorCode");
  }

  @Test
  void getSessionId() {
    // when
    sut.getSessionId();

    // then
    verify(webSocketSessionMock).getId();
  }

  @Test
  void closeSession() throws IOException {
    // when
    sut.closeSession();

    // then
    verify(webSocketSessionMock).close(CloseStatus.NORMAL);
  }

  @Test
  void closeSessionThrowsIOException() throws IOException {
    // given
    final var expectedErrorCode = "errorCode";
    doThrow(new IOException("error message")).when(webSocketSessionMock).close(CloseStatus.NORMAL);

    // when
    final var scenarioException = assertThrows(ScenarioException.class, () -> sut.closeSession());

    // then
    assertThat(scenarioException.getErrorCode()).isEqualTo(expectedErrorCode);
  }
}
