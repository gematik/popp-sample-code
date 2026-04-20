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

package de.gematik.refpopp.popp_server.scenario.contactless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.messages.TokenMessage;
import de.gematik.refpopp.popp_server.communication.ClientCommunicationService;
import de.gematik.refpopp.popp_server.handler.SessionCommunication;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioMessageFactory;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioTransitionService;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.provider.CardScenarioProvider;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioId;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContactLessScenarioProcessingServiceTest {

  private SessionAccessor sessionAccessorMock;
  private SessionCommunication sessionCommunicationMock;
  private ClientCommunicationService clientCommunicationServiceMock;
  private ScenarioTransitionService scenarioTransitionServiceMock;
  private CardScenarioProvider cardScenarioProviderMock;
  private ContactLessScenarioProcessingService sut;

  @BeforeEach
  void setUp() {
    sessionAccessorMock = mock(SessionAccessor.class);
    cardScenarioProviderMock = mock(CardScenarioProvider.class);
    sessionCommunicationMock = mock(SessionCommunication.class);
    final var scenarioMessageFactoryMock = mock(ScenarioMessageFactory.class);
    clientCommunicationServiceMock = mock(ClientCommunicationService.class);
    scenarioTransitionServiceMock = mock(ScenarioTransitionService.class);
    sut =
        new ContactLessScenarioProcessingService(
            sessionAccessorMock,
            scenarioMessageFactoryMock,
            clientCommunicationServiceMock,
            scenarioTransitionServiceMock);
  }

  @Test
  void getSupportedCommunicationModeReturnsContactLess() {
    // when
    final var actual = sut.getSupportedCommunicationMode();

    // then
    assertThat(actual).isEqualTo(CommunicationMode.CONTACTLESS);
  }

  @Test
  void isLastScenarioReturnsTrueForAuthG2() {
    // given
    final var scenario = new Scenario(ScenarioId.AUTH_G2, List.of());

    // when
    final var actual = sut.isLastScenario(scenario);

    // then
    assertThat(actual).isTrue();
  }

  @Test
  void isLastScenarioReturnsFalseForDifferentScenario() {
    // given
    final var scenario = new Scenario(ScenarioId.OPEN_EGK, List.of());

    // when
    final var actual = sut.isLastScenario(scenario);

    // then
    assertThat(actual).isFalse();
  }

  @Test
  void processScenarioStoresNonceAndStoresTypedScenario() {
    // given
    final var stepDefinition1 = new StepDefinition(StepId.READ_VERSION);
    final var stepDefinition2 = new StepDefinition(StepId.READ_X509);
    final var stepDefinition3 = new StepDefinition(StepId.INTERNAL_AUTHENTICATION);
    final var lastScenario = new Scenario(ScenarioId.OPEN_EGK, List.of(stepDefinition1));
    final var nextScenario =
        new Scenario(ScenarioId.AUTH_G2, List.of(stepDefinition2, stepDefinition3));

    when(scenarioTransitionServiceMock.getNextScenario(anyString(), any(), any()))
        .thenReturn(nextScenario);
    final var sessionId = "sessionId";
    when(sessionCommunicationMock.getSessionId()).thenReturn(sessionId);

    // when
    sut.processScenario(sessionCommunicationMock, lastScenario, cardScenarioProviderMock);

    // then
    final ArgumentCaptor<byte[]> nonceCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(sessionAccessorMock).storeNonce(same(sessionId), nonceCaptor.capture());
    assertThat(nonceCaptor.getValue()).isNotNull();
    verify(sessionAccessorMock).storeScenario(sessionId, nextScenario);
  }

  @Test
  void processScenarioIsLastScenario() {
    // given
    final var lastScenario = new Scenario(ScenarioId.AUTH_G2, List.of());
    when(sessionCommunicationMock.getSessionId()).thenReturn("sessionId");
    when(sessionAccessorMock.getPoppToken(anyString())).thenReturn("poppToken");

    // when
    sut.processScenario(sessionCommunicationMock, lastScenario, cardScenarioProviderMock);

    // then
    final ArgumentCaptor<TokenMessage> messageCaptor = ArgumentCaptor.forClass(TokenMessage.class);
    verify(clientCommunicationServiceMock)
        .sendMessage(messageCaptor.capture(), same(sessionCommunicationMock));
    assertThat(messageCaptor.getValue()).isNotNull();
    verify(sessionCommunicationMock).closeSession();
  }
}
