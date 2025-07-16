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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.messages.ScenarioResponseMessage;
import de.gematik.refpopp.popp_server.handler.SessionCommunication;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioTransitionService;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractScenarioProcessingService;
import de.gematik.refpopp.popp_server.scenario.common.provider.CardScenarioProvider;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioProcessingProviderStrategyService;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioProviderStrategyService;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultManager;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioResponseMessageHandlerTest {

  private ScenarioTransitionService scenarioTransitionServiceMock;
  private SessionAccessor sessionAccessorMock;
  private ScenarioProcessingProviderStrategyService scenarioProcessingProviderStrategyServiceMock;
  private ScenarioProviderStrategyService scenarioProviderStrategyServiceMock;
  private ScenarioResultManager scenarioResultManagerMock;
  private ScenarioResponseMessageHandler sut;

  @BeforeEach
  void setUp() {
    scenarioTransitionServiceMock = mock(ScenarioTransitionService.class);
    sessionAccessorMock = mock(SessionAccessor.class);
    scenarioProcessingProviderStrategyServiceMock =
        mock(ScenarioProcessingProviderStrategyService.class);
    scenarioResultManagerMock = mock(ScenarioResultManager.class);
    scenarioProviderStrategyServiceMock = mock(ScenarioProviderStrategyService.class);
    sut =
        new ScenarioResponseMessageHandler(
            scenarioResultManagerMock,
            scenarioTransitionServiceMock,
            sessionAccessorMock,
            scenarioProviderStrategyServiceMock,
            scenarioProcessingProviderStrategyServiceMock);
  }

  @Test
  void getMessageType() {
    assertThat(sut.getMessageType()).isEqualTo(ScenarioResponseMessage.class);
  }

  @Test
  void handle() {
    // given
    final var scenarioResponseMessage = new ScenarioResponseMessage(List.of());
    final var sessionCommunicationMock = mock(SessionCommunication.class);
    final var scenario = new Scenario("scenarioName", List.of());
    final var scenarioProcessingServiceMock = mock(AbstractScenarioProcessingService.class);
    final var cardScenarioProviderMock = mock(CardScenarioProvider.class);
    when(sessionCommunicationMock.getSessionId()).thenReturn("session1");
    when(scenarioTransitionServiceMock.getCurrentScenario("session1")).thenReturn(scenario);
    when(sessionAccessorMock.getCommunicationMode("session1"))
        .thenReturn(CommunicationMode.CONTACT);
    when(scenarioProviderStrategyServiceMock.getProvider(CommunicationMode.CONTACT))
        .thenReturn(cardScenarioProviderMock);
    when(scenarioProcessingProviderStrategyServiceMock.getProvider(CommunicationMode.CONTACT))
        .thenReturn(scenarioProcessingServiceMock);

    // when
    sut.handle(scenarioResponseMessage, sessionCommunicationMock);

    // then
    verify(scenarioTransitionServiceMock).getCurrentScenario("session1");
    verify(scenarioResultManagerMock).manage("session1", scenario, List.of());
    verify(sessionAccessorMock, times(2)).getCommunicationMode("session1");
    verify(scenarioProviderStrategyServiceMock).getProvider(CommunicationMode.CONTACT);
    verify(scenarioProcessingProviderStrategyServiceMock).getProvider(CommunicationMode.CONTACT);
    verify(scenarioProcessingServiceMock)
        .processScenario(sessionCommunicationMock, scenario, cardScenarioProviderMock);
  }
}
