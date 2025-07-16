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

package de.gematik.refpopp.popp_server.scenario.common.provider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.refpopp.popp_server.communication.ClientCommunicationService;
import de.gematik.refpopp.popp_server.handler.SessionCommunication;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioMessageFactory;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioTransitionService;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AbstractScenarioProcessingServiceTest {

  private SessionAccessor sessionAccessorMock;
  private ScenarioMessageFactory scenarioMessageFactoryMock;
  private ClientCommunicationService clientCommunicationServiceMock;
  private ScenarioTransitionService scenarioTransitionServiceMock;
  private AbstractScenarioProcessingService sut;

  @BeforeEach
  void setUp() {
    sessionAccessorMock = mock(SessionAccessor.class);
    scenarioMessageFactoryMock = mock(ScenarioMessageFactory.class);
    clientCommunicationServiceMock = mock(ClientCommunicationService.class);
    scenarioTransitionServiceMock = mock(ScenarioTransitionService.class);
    sut = createScenarioProcessingService(false);
    ReflectionTestUtils.setField(sut, "timeSpan", 100);
  }

  private AbstractScenarioProcessingService createScenarioProcessingService(
      final boolean isLastScenario) {
    return new AbstractScenarioProcessingService(
        sessionAccessorMock,
        scenarioMessageFactoryMock,
        clientCommunicationServiceMock,
        scenarioTransitionServiceMock) {

      @Override
      public CommunicationMode getSupportedCommunicationMode() {
        return null;
      }

      @Override
      public boolean isLastScenario(final Scenario currentScenario) {
        return isLastScenario;
      }

      @Override
      public void processScenario(
          final SessionCommunication session,
          final Scenario lastScenarioSentToClient,
          final CardScenarioProvider cardScenarioProvider) {
        // for testing
      }
    };
  }

  @Test
  void sendMessage() {
    // given
    final var standardScenarioMessage =
        StandardScenarioMessage.builder()
            .clientSessionId("clientSessionId")
            .version("version")
            .steps(List.of())
            .sequenceCounter(0)
            .timeSpan(100)
            .build();
    final var sessionCommunicationMock = mock(SessionCommunication.class);

    // when
    sut.sendMessage(standardScenarioMessage, sessionCommunicationMock);

    // then
    verify(clientCommunicationServiceMock)
        .sendMessage(standardScenarioMessage, sessionCommunicationMock);
  }

  @Test
  void getClientSessionId() {
    // given
    final var sessionId = "sessionId";

    // when
    sut.getClientSessionId(sessionId);

    // then
    verify(sessionAccessorMock).getClientSessionId(sessionId);
  }

  @Test
  void storeSequenceCounterInSession() {
    // given
    final var sessionCommunicationMock = mock(SessionCommunication.class);
    final var sequenceCounter = 0;

    // when
    sut.storeSequenceCounterInSession(sessionCommunicationMock, sequenceCounter);

    // then
    verify(sessionAccessorMock)
        .storeScenarioCounter(sessionCommunicationMock.getSessionId(), sequenceCounter);
  }

  @Test
  void getPoppToken() {
    // given
    final var sessionId = "sessionId";

    // when
    sut.getPoppToken(sessionId);

    // then
    verify(sessionAccessorMock).getPoppToken(sessionId);
  }

  @Test
  void closeSession() {
    // given
    final var sessionCommunicationMock = mock(SessionCommunication.class);

    // when
    sut.closeSession(sessionCommunicationMock);

    // then
    verify(sessionCommunicationMock).closeSession();
  }

  @Test
  void createScenarioMessage() {
    // given
    final var nextScenario = new Scenario("scenario1", List.of());
    final var clientSessionId = "clientSessionId";
    final var sequenceCounter = 0;
    final var timeSpan = 100;

    // when
    sut.createScenarioMessage(
        nextScenario, clientSessionId, sequenceCounter, timeSpan, "sessionId");

    // then
    verify(scenarioMessageFactoryMock)
        .create(nextScenario, clientSessionId, sequenceCounter, timeSpan, "sessionId");
  }

  @Test
  void createAndSendMessage() {
    // given
    final var sessionCommunicationMock = mock(SessionCommunication.class);
    final var scenario = new Scenario("scenario1", List.of());
    final var clientSessionId = "clientSessionId";
    final var standardScenarioMessageMock = mock(StandardScenarioMessage.class);
    when(sessionCommunicationMock.getSessionId()).thenReturn("sessionId");
    when(sessionAccessorMock.getClientSessionId(anyString())).thenReturn(clientSessionId);
    when(sessionAccessorMock.getSequenceCounter(anyString())).thenReturn(0);
    when(scenarioMessageFactoryMock.create(any(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(standardScenarioMessageMock);

    // when
    sut.createAndSendMessage(sessionCommunicationMock, scenario);

    // then
    verify(sessionAccessorMock).getSequenceCounter(sessionCommunicationMock.getSessionId());
    verify(sessionAccessorMock).getClientSessionId(sessionCommunicationMock.getSessionId());
    verify(scenarioMessageFactoryMock).create(scenario, clientSessionId, 0, 100, "sessionId");
    verify(sessionAccessorMock).storeScenarioCounter(sessionCommunicationMock.getSessionId(), 1);
    verify(clientCommunicationServiceMock)
        .sendMessage(standardScenarioMessageMock, sessionCommunicationMock);
  }

  @Test
  void createAndSendMessageIsLast() {
    // given
    final var sessionCommunicationMock = mock(SessionCommunication.class);
    final var scenario = new Scenario("scenario1", List.of());
    final var clientSessionId = "clientSessionId";
    final var standardScenarioMessageMock = mock(StandardScenarioMessage.class);
    when(sessionCommunicationMock.getSessionId()).thenReturn("sessionId");
    when(sessionAccessorMock.getClientSessionId(anyString())).thenReturn(clientSessionId);
    when(sessionAccessorMock.getSequenceCounter(anyString())).thenReturn(0);
    when(scenarioMessageFactoryMock.create(any(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(standardScenarioMessageMock);
    sut = createScenarioProcessingService(true);

    // when
    sut.createAndSendMessage(sessionCommunicationMock, scenario);

    // then
    verify(sessionAccessorMock).getSequenceCounter(sessionCommunicationMock.getSessionId());
    verify(sessionAccessorMock).getClientSessionId(sessionCommunicationMock.getSessionId());
    verify(scenarioMessageFactoryMock).create(scenario, clientSessionId, 0, 0, "sessionId");
    verify(sessionAccessorMock).storeScenarioCounter(sessionCommunicationMock.getSessionId(), 1);
    verify(clientCommunicationServiceMock)
        .sendMessage(standardScenarioMessageMock, sessionCommunicationMock);
  }
}
