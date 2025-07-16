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

package de.gematik.refpopp.popp_server.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.ConnectorScenarioMessage;
import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioMessageFactory;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.token.JwtTokenCreator;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioMessageFactoryTest {

  private ScenarioMessageFactory sut;
  private SessionAccessor sessionAccessorMock;
  private JwtTokenCreator tokenCreatorMock;

  @BeforeEach
  void setUp() {
    sessionAccessorMock = mock(SessionAccessor.class);
    tokenCreatorMock = mock(JwtTokenCreator.class);
    sut = new ScenarioMessageFactory(sessionAccessorMock, tokenCreatorMock);
  }

  @Test
  void createCreatesStandardScenarioMessage() {
    // given
    final var sessionId = "sessionId";
    final var state =
        new StepDefinition("name1", "description", "commandApdu", List.of("expectedStatusWord"));
    final var scenario = new Scenario("scenarioName", List.of(state));
    final var clientSessionId = "clientSessionId";
    final int sequenceCounter = 1;
    final int timeSpan = 1;
    when(sessionAccessorMock.getCardConnectionType(sessionId))
        .thenReturn(CardConnectionType.CONTACT_STANDARD);

    // when
    final var actual =
        (StandardScenarioMessage)
            sut.create(scenario, clientSessionId, sequenceCounter, timeSpan, sessionId);

    // then
    assertThat(actual.getVersion()).isEqualTo("1.0.0");
    assertThat(actual.getClientSessionId()).isEqualTo(clientSessionId);
    assertThat(actual.getSequenceCounter()).isEqualTo(1);
    assertThat(actual.getTimeSpan()).isEqualTo(1);
    assertThat(actual.getSteps()).hasSize(1);
    verify(sessionAccessorMock).getCardConnectionType(sessionId);
  }

  @Test
  void createCreatesConnectorScenarioMessage() {
    // given
    final var sessionId = "sessionId";
    final var state =
        new StepDefinition("name1", "description", "commandApdu", List.of("expectedStatusWord"));
    final var scenario = new Scenario("scenarioName", List.of(state));
    final var clientSessionId = "clientSessionId";
    final int sequenceCounter = 1;
    final int timeSpan = 1;
    when(sessionAccessorMock.getCardConnectionType(sessionId))
        .thenReturn(CardConnectionType.CONTACT_CONNECTOR);

    // when
    final var actual = sut.create(scenario, clientSessionId, sequenceCounter, timeSpan, sessionId);

    // then
    assertThat(actual).isInstanceOf(ConnectorScenarioMessage.class);
    verify(sessionAccessorMock).getCardConnectionType(sessionId);
    verify(tokenCreatorMock)
        .createConnectorToken(any(StandardScenarioMessage.class), eq(sessionId));
  }
}
