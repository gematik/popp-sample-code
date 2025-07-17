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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.poppcommons.api.messages.TokenMessage;
import de.gematik.refpopp.popp_server.handler.SessionCommunication;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ClientCommunicationServiceTest {

  private final ClientCommunicationService sut;
  private final SessionCommunication sessionMock;

  ClientCommunicationServiceTest() {
    sut = new ClientCommunicationService();
    sessionMock = mock(SessionCommunication.class);
  }

  @Test
  void sendMessageSendsStandardScenarioMessage() throws IOException {
    // given
    when(sessionMock.getSessionId()).thenReturn("session1");
    final var messagePayload =
        """
            {
              "type": "StandardScenario",
              "version": "1.0.0",
              "clientSessionId": "12345-abcde-67890",
              "sequenceCounter": 1,
              "timeSpan": 300,
              "steps": [
                {
                  "commandApdu": "00A404000E325041592E5359532E4444463031",
                  "expectedStatusWords": [
                    "9000",
                    "6A82"
                  ]
                },
                {
                  "commandApdu": "00B2010C00",
                  "expectedStatusWords": [
                    "9000"
                  ]
                }
              ]
            }
        """;
    final ObjectMapper mapper = new ObjectMapper();
    final var standardScenarioMessage =
        mapper.readValue(messagePayload, StandardScenarioMessage.class);

    // when
    sut.sendMessage(standardScenarioMessage, sessionMock);

    // then
    verify(sessionMock).sendMessage(standardScenarioMessage);
  }

  @Test
  void sendMessageSendsTokenMessage() {
    // given
    when(sessionMock.getSessionId()).thenReturn("session1");
    final var token = "AKQEDA.fSdg.ABRIAA";
    final var tokenMessage = new TokenMessage(token, "pn");

    // when
    sut.sendMessage(tokenMessage, sessionMock);

    // then
    verify(sessionMock).sendMessage(tokenMessage);
  }

  @Test
  void closeSession() {
    // given
    when(sessionMock.getSessionId()).thenReturn("session1");

    // when
    sut.closeSession(sessionMock);

    // then
    verify(sessionMock).closeSession();
  }
}
