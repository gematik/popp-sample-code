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

package de.gematik.refpopp.popp_server.scenario.openegk;

import static org.assertj.core.api.Assertions.assertThat;
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
import de.gematik.refpopp.popp_server.scenario.common.provider.CardScenarioProvider;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OpenEgkScenarioProcessingServiceTest {

  private OpenEgkScenarioProcessingService sut;
  private ScenarioMessageFactory scenarioMessageFactoryMock;
  private ClientCommunicationService clientCommunicationServiceMock;
  private SessionAccessor sessionAccessorMock;

  @BeforeEach
  void setUp() {
    sessionAccessorMock = mock(SessionAccessor.class);
    scenarioMessageFactoryMock = mock(ScenarioMessageFactory.class);
    clientCommunicationServiceMock = mock(ClientCommunicationService.class);
    final var scenarioTransitionServiceMock = mock(ScenarioTransitionService.class);
    sut =
        new OpenEgkScenarioProcessingService(
            sessionAccessorMock,
            scenarioMessageFactoryMock,
            clientCommunicationServiceMock,
            scenarioTransitionServiceMock);
    ReflectionTestUtils.setField(sut, "timeSpan", 1000);
  }

  @Test
  void getSupportedCommunicationMode() {
    assertThat(sut.getSupportedCommunicationMode())
        .isEqualByComparingTo(CommunicationMode.UNDEFINED);
  }

  @Test
  void isLastScenario() {
    assertThat(sut.isLastScenario(null)).isFalse();
  }

  @Test
  void processScenario() {
    // given
    final var lastScenario = new Scenario("scenarioName", null);
    final var cardScenarioProviderMock = mock(CardScenarioProvider.class);
    final var sessionCommunicationMock = mock(SessionCommunication.class);
    final var nextScenario = new Scenario("nextScenario", null);
    when(cardScenarioProviderMock.getScenarios()).thenReturn(List.of(nextScenario));
    when(sessionCommunicationMock.getSessionId()).thenReturn("session1");
    when(sessionAccessorMock.getSequenceCounter(anyString())).thenReturn(0);
    when(sessionAccessorMock.getClientSessionId(anyString())).thenReturn("clientSessionId");
    final var standardScenarioMessage = mock(StandardScenarioMessage.class);
    when(scenarioMessageFactoryMock.create(any(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(standardScenarioMessage);

    // when
    sut.processScenario(sessionCommunicationMock, lastScenario, cardScenarioProviderMock);

    // then
    verify(scenarioMessageFactoryMock).create(nextScenario, "clientSessionId", 0, 1000, "session1");
    verify(clientCommunicationServiceMock)
        .sendMessage(standardScenarioMessage, sessionCommunicationMock);
  }
}
