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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.enums.BdeErrorCode;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioTransitionService;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.provider.CardScenarioProvider;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioId;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioTransitionServiceTest {

  private SessionContainer sessionContainer;
  private CardScenarioProvider cardScenarioProviderMock;
  private ScenarioTransitionService sut;

  @BeforeEach
  void setUp() {
    sessionContainer = new SessionContainer();
    sut = new ScenarioTransitionService(sessionContainer);
    cardScenarioProviderMock = mock(CardScenarioProvider.class);
  }

  @Test
  void getCurrentScenarioReturnsScenario() {
    // given
    final var sessionId = "sessionId";
    final var step1 = new StepDefinition(StepId.SELECT_MASTER_FILE);
    final var step2 = new StepDefinition(StepId.READ_VERSION);
    final var scenario = new Scenario(ScenarioId.OPEN_EGK, List.of(step1, step2));
    sessionContainer.storeScenario(sessionId, scenario);

    // when
    final var actual = sut.getCurrentScenario(sessionId);

    // then
    assertThat(actual).isEqualTo(scenario);
  }

  @Test
  void getCurrentScenarioThrowsExceptionWhenNoScenarioFound() {
    // given
    final var sessionId = "sessionId";

    // when
    final var actual =
        assertThrows(ScenarioException.class, () -> sut.getCurrentScenario(sessionId));

    // then
    assertThat(actual.getMessage()).isEqualTo("No scenario found");
    assertThat(actual.getErrorCode()).isEqualTo(BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR);
  }

  @Test
  void getNextScenarioReturnsNextScenario() {
    // given
    final var sessionId = "sessionId";
    final var scenario1 =
        new Scenario(
            ScenarioId.READ_CVC, List.of(new StepDefinition(StepId.READ_SUB_CA_CV_CERTIFICATE)));
    final var scenario2 =
        new Scenario(
            ScenarioId.TRUSTED_CHANNEL_STEP_1,
            List.of(new StepDefinition(StepId.SELECT_PRIVATE_KEY)));
    when(cardScenarioProviderMock.getNextScenario(scenario1)).thenReturn(Optional.of(scenario2));

    // when
    final var actual = sut.getNextScenario(sessionId, scenario1, cardScenarioProviderMock);

    // then
    assertThat(actual).isEqualTo(scenario2);
    verify(cardScenarioProviderMock).getNextScenario(scenario1);
  }

  @Test
  void getNextScenarioThrowsExceptionWhenNoNextScenarioFound() {
    // given
    final var sessionId = "sessionId";
    final var scenario1 =
        new Scenario(
            ScenarioId.READ_X509, List.of(new StepDefinition(StepId.READ_EF_C_CH_AUT_E256)));

    // when
    final var actual =
        assertThrows(
            ScenarioException.class,
            () -> sut.getNextScenario(sessionId, scenario1, cardScenarioProviderMock));

    // then
    assertThat(actual.getMessage()).isEqualTo("No next scenario found");
    assertThat(actual.getErrorCode()).isEqualTo(BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR);
    verify(cardScenarioProviderMock).getNextScenario(scenario1);
  }
}
