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

package de.gematik.refpopp.popp_server.scenario.common.orchestrator;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.poppcommons.api.messages.StartMessage;
import de.gematik.refpopp.popp_server.handler.SessionCommunication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class MessageHandlerOrchestratorTest {

  private MessageHandlerProvider messageHandlerProviderMock;
  private SessionCommunication sessionCommunicationMock;
  private MessageHandlerOrchestrator sut;

  @BeforeEach
  void setUp() {
    messageHandlerProviderMock = mock(MessageHandlerProvider.class);
    sessionCommunicationMock = mock(SessionCommunication.class);
    sut = new MessageHandlerOrchestrator(messageHandlerProviderMock);
  }

  @Test
  void orchestrateHandlesStartMessage() {
    // given
    final var startMessage =
        StartMessage.builder()
            .cardConnectionType(CardConnectionType.CONTACT_STANDARD)
            .clientSessionId("clientSessionId")
            .version("version")
            .build();
    final var startMessageHandler = mock(StartMessageHandler.class);
    when(messageHandlerProviderMock.getHandlerFor(startMessage)).thenReturn(startMessageHandler);

    // when
    sut.orchestrate(startMessage, sessionCommunicationMock);

    // then
    verify(messageHandlerProviderMock).getHandlerFor(startMessage);
    verify(startMessageHandler).handle(startMessage, sessionCommunicationMock);
  }

  @Test
  void orchestrateThrowsScenarioExceptionIfMessageHandlerIsNull() {
    // given
    final var startMessage =
        StartMessage.builder()
            .cardConnectionType(CardConnectionType.CONTACT_STANDARD)
            .clientSessionId("clientSessionId")
            .version("version")
            .build();
    when(messageHandlerProviderMock.getHandlerFor(startMessage)).thenReturn(null);

    // when, then
    assertThrows(
        ScenarioException.class, () -> sut.orchestrate(startMessage, sessionCommunicationMock));
  }
}
