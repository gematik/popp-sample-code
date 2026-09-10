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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.StartMessage;
import de.gematik.refpopp.popp_server.handler.SessionCommunication;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioTransitionService;
import de.gematik.refpopp.popp_server.scenario.common.provider.*;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.openegk.OpenEgkScenarioProcessingService;
import de.gematik.refpopp.popp_server.scenario.openegk.OpenEgkScenariosProvider;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StartMessageHandlerTest {

  private StartMessageHandler sut;
  private ScenarioTransitionService scenarioTransitionServiceMock;
  private SessionAccessor sessionAccessorMock;
  private ScenarioProcessingProviderStrategyService scenarioProcessingProviderStrategyServiceMock;
  private ScenarioProviderStrategyService scenarioProviderStrategyServiceMock;
  private CardScenarioProvider firstScenarioProviderMock;

  @BeforeEach
  void setUp() {
    scenarioTransitionServiceMock = mock(ScenarioTransitionService.class);
    sessionAccessorMock = mock(SessionAccessor.class);
    scenarioProcessingProviderStrategyServiceMock =
        mock(ScenarioProcessingProviderStrategyService.class);
    scenarioProviderStrategyServiceMock = mock(ScenarioProviderStrategyService.class);
    firstScenarioProviderMock = mock(CardScenarioProvider.class);
    sut =
        new StartMessageHandler(
            scenarioTransitionServiceMock,
            sessionAccessorMock,
            scenarioProcessingProviderStrategyServiceMock,
            scenarioProviderStrategyServiceMock,
            firstScenarioProviderMock);
  }

  @Test
  void getMessageType() {
    assertThat(sut.getMessageType()).isEqualTo(StartMessage.class);
  }

  @Test
  void handle() {
    // given
    final var sessionCommunicationMock = mock(SessionCommunication.class);
    final var startMessage =
        StartMessage.builder()
            .clientSessionId("clientSessionId")
            .cardConnectionType(CardConnectionType.CONTACT_STANDARD)
            .build();
    final var openEgkScenarioProcessingServiceMock = mock(OpenEgkScenarioProcessingService.class);

    when(sessionCommunicationMock.getSessionId()).thenReturn("session1");
    when(scenarioProcessingProviderStrategyServiceMock.getProvider(any()))
        .thenReturn(openEgkScenarioProcessingServiceMock);
    final var openEgkScenarioProviderServiceMock = mock(OpenEgkScenariosProvider.class);
    when(scenarioProviderStrategyServiceMock.getProvider(any()))
        .thenReturn(openEgkScenarioProviderServiceMock);
    final var scenario = new Scenario(ScenarioId.OPEN_EGK, List.of());

    // make firstScenarioProvider return a list with a first element to be stored in session
    final var firstScenario = new Scenario(ScenarioId.OPEN_EGK, List.of());
    when(firstScenarioProviderMock.getScenarios())
        .thenReturn(new java.util.LinkedList<>(List.of(firstScenario)));

    when(scenarioTransitionServiceMock.getCurrentScenario("session1")).thenReturn(scenario);

    // when
    sut.handle(startMessage, sessionCommunicationMock);

    // then
    verify(sessionAccessorMock).storeSequenceCounter("session1", 0);
    verify(sessionAccessorMock).storeClientSessionId("session1", "clientSessionId");
    verify(sessionAccessorMock)
        .storeCardConnectionType("session1", CardConnectionType.CONTACT_STANDARD);
    verify(sessionAccessorMock).storeScenario("session1", firstScenario);
    verify(scenarioTransitionServiceMock).getCurrentScenario("session1");
    verify(openEgkScenarioProcessingServiceMock)
        .processScenario(sessionCommunicationMock, scenario, openEgkScenarioProviderServiceMock);
  }

  @Test
  void handle_usesCommunicationModeFromSessionAccessor() {
    // given
    final var sessionCommunicationMock = mock(SessionCommunication.class);
    final var startMessage =
        StartMessage.builder()
            .clientSessionId("clientSessionId")
            .cardConnectionType(CardConnectionType.CONTACT_STANDARD)
            .build();
    when(sessionCommunicationMock.getSessionId()).thenReturn("session1");
    when(sessionCommunicationMock.getTransportSessionId()).thenReturn("transport1");

    when(sessionAccessorMock.getCommunicationModeOrDefaultValue("session1"))
        .thenReturn(CommunicationMode.UNDEFINED);

    final var processingProviderMock = mock(AbstractScenarioProcessingService.class);
    final var cardScenarioProviderMock = mock(CardScenarioProvider.class);

    when(scenarioProcessingProviderStrategyServiceMock.getProvider(CommunicationMode.UNDEFINED))
        .thenReturn(processingProviderMock);
    when(scenarioProviderStrategyServiceMock.getProvider(CommunicationMode.UNDEFINED))
        .thenReturn(cardScenarioProviderMock);

    final var scenario = new Scenario(ScenarioId.OPEN_EGK, List.of());
    when(scenarioTransitionServiceMock.getCurrentScenario("session1")).thenReturn(scenario);
    when(firstScenarioProviderMock.getScenarios())
        .thenReturn(new java.util.LinkedList<>(List.of(scenario)));

    // when
    sut.handle(startMessage, sessionCommunicationMock);

    // then
    verify(scenarioProcessingProviderStrategyServiceMock).getProvider(CommunicationMode.UNDEFINED);
    verify(scenarioProviderStrategyServiceMock).getProvider(CommunicationMode.UNDEFINED);
    verify(sessionAccessorMock).copyConnectionScopedData("transport1", "session1");
  }

  @Test
  void handle_copiesConnectionScopedData_whenTransportSessionIdIsPresent() {
    // given
    final var sessionCommunicationMock = mock(SessionCommunication.class);
    final var startMessage =
        StartMessage.builder()
            .clientSessionId("clientSessionId")
            .cardConnectionType(CardConnectionType.CONTACT_STANDARD)
            .build();
    when(sessionCommunicationMock.getSessionId()).thenReturn("session1");
    when(sessionCommunicationMock.getTransportSessionId()).thenReturn("transport-xyz");

    final var processingProviderMock = mock(AbstractScenarioProcessingService.class);
    final var cardScenarioProviderMock = mock(CardScenarioProvider.class);

    when(scenarioProcessingProviderStrategyServiceMock.getProvider(any()))
        .thenReturn(processingProviderMock);
    when(scenarioProviderStrategyServiceMock.getProvider(any()))
        .thenReturn(cardScenarioProviderMock);

    final var scenario = new Scenario(ScenarioId.OPEN_EGK, List.of());
    when(scenarioTransitionServiceMock.getCurrentScenario("session1")).thenReturn(scenario);
    when(firstScenarioProviderMock.getScenarios())
        .thenReturn(new java.util.LinkedList<>(List.of(scenario)));

    // when
    sut.handle(startMessage, sessionCommunicationMock);

    // then
    verify(sessionAccessorMock).copyConnectionScopedData("transport-xyz", "session1");
  }
}
