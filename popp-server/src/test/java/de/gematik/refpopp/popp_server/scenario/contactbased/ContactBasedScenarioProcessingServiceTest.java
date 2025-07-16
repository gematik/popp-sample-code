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

package de.gematik.refpopp.popp_server.scenario.contactbased;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.poppcommons.api.messages.TokenMessage;
import de.gematik.refpopp.popp_server.communication.ClientCommunicationService;
import de.gematik.refpopp.popp_server.handler.SessionCommunication;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioMessageFactory;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioTransitionService;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.provider.CardScenarioProvider;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ContactBasedScenarioProcessingServiceTest {

  private ContactBasedScenarioProcessingService sut;
  private ClientCommunicationService clientCommunicationServiceMock;
  private SessionAccessor sessionAccessorMock;
  private ScenarioTransitionService scenarioTransitionServiceMock;
  private ScenarioMessageFactory scenarioMessageFactoryMock;

  @BeforeEach
  void setUp() {
    scenarioMessageFactoryMock = mock(ScenarioMessageFactory.class);
    clientCommunicationServiceMock = mock(ClientCommunicationService.class);
    sessionAccessorMock = mock(SessionAccessor.class);
    scenarioTransitionServiceMock = mock(ScenarioTransitionService.class);
    sut =
        new ContactBasedScenarioProcessingService(
            scenarioMessageFactoryMock,
            clientCommunicationServiceMock,
            sessionAccessorMock,
            scenarioTransitionServiceMock);
    ReflectionTestUtils.setField(sut, "readX509ScenarioName", "READ_X509_CERTIFICATE");
    ReflectionTestUtils.setField(sut, "readCvcScenarioName", "READ_CVC");
  }

  @Test
  void isLastScenario() {
    // given
    final var scenario = new Scenario("READ_X509_CERTIFICATE", List.of());

    // when
    final var lastScenario = sut.isLastScenario(scenario);

    // then
    assertThat(lastScenario).isTrue();
  }

  @Test
  void isNotLastScenario() {
    // given
    final var scenario = new Scenario("scenario1", List.of());

    // when
    final var lastScenario = sut.isLastScenario(scenario);

    // then
    assertThat(lastScenario).isFalse();
  }

  @Test
  void processScenarioAddsAdditionalSteps() {
    // given
    final var standardScenarioMessageMock = mock(StandardScenarioMessage.class);
    final var sessionCommunicationMock = mock(SessionCommunication.class);
    final var cardScenarioProviderMock = mock(CardScenarioProvider.class);
    final var stepDefinition1 =
        new StepDefinition("step1", "description1", "apdu1", List.of("9000"));
    final var scenario = new Scenario("READ_CVC", List.of(stepDefinition1));
    final var stepDefinition2 =
        new StepDefinition("step2", "description2", "apdu2", List.of("9000"));
    final var expectedScenario =
        new Scenario("READ_CVC", List.of(stepDefinition1, stepDefinition2));
    when(sessionAccessorMock.getOpenContactIccCvcList(anyString()))
        .thenReturn(Optional.of(List.of(stepDefinition2)));
    when(scenarioTransitionServiceMock.getNextScenario(anyString(), any(), any()))
        .thenReturn(scenario);
    when(sessionCommunicationMock.getSessionId()).thenReturn("sessionId");
    when(sessionAccessorMock.getClientSessionId(anyString())).thenReturn("clientSessionId");
    when(sessionAccessorMock.getSequenceCounter(anyString())).thenReturn(1);
    when(sessionCommunicationMock.getSessionId()).thenReturn("sessionId");
    when(scenarioMessageFactoryMock.create(any(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(standardScenarioMessageMock);

    // when
    sut.processScenario(sessionCommunicationMock, scenario, cardScenarioProviderMock);

    // then
    verify(this.sessionAccessorMock).storeScenario("sessionId", expectedScenario);
    verify(clientCommunicationServiceMock)
        .sendMessage(any(StandardScenarioMessage.class), eq(sessionCommunicationMock));
  }

  @Test
  void processScenarioDoesNotAddAdditionalSteps() {
    // given
    final var standardScenarioMessageMock = mock(StandardScenarioMessage.class);
    final var sessionCommunicationMock = mock(SessionCommunication.class);
    final var cardScenarioProviderMock = mock(CardScenarioProvider.class);
    final var stepDefinition1 =
        new StepDefinition("step1", "description1", "apdu1", List.of("9000"));
    final var scenario = new Scenario("READ_CVC", List.of(stepDefinition1));
    final var sessionContainer = mock(SessionContainer.class);
    when(sessionContainer.retrieveSessionData(
            anyString(), eq(SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST), any()))
        .thenReturn(Optional.empty());
    when(scenarioTransitionServiceMock.getNextScenario(anyString(), any(), any()))
        .thenReturn(scenario);
    when(sessionCommunicationMock.getSessionId()).thenReturn("sessionId");
    when(sessionAccessorMock.getClientSessionId(anyString())).thenReturn("clientSessionId");
    when(sessionAccessorMock.getSequenceCounter(anyString())).thenReturn(1);
    when(sessionCommunicationMock.getSessionId()).thenReturn("sessionId");
    when(scenarioMessageFactoryMock.create(any(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(standardScenarioMessageMock);

    // when
    sut.processScenario(sessionCommunicationMock, scenario, cardScenarioProviderMock);

    // then
    verify(sessionAccessorMock).storeScenario("sessionId", scenario);
    verify(clientCommunicationServiceMock).sendMessage(any(StandardScenarioMessage.class), any());
  }

  @Test
  void processScenarioProcessesLastScenario() {
    // given
    final var sessionCommunicationMock = mock(SessionCommunication.class);
    final var cardScenarioProviderMock = mock(CardScenarioProvider.class);
    final var stepDefinition1 =
        new StepDefinition("step1", "description1", "apdu1", List.of("9000"));
    final var scenario = new Scenario("READ_X509_CERTIFICATE", List.of(stepDefinition1));
    when(sessionCommunicationMock.getSessionId()).thenReturn("sessionId");
    when(sessionAccessorMock.getPoppToken("sessionId")).thenReturn("poppToken");
    final var argumentCaptor = ArgumentCaptor.forClass(TokenMessage.class);

    // when
    sut.processScenario(sessionCommunicationMock, scenario, cardScenarioProviderMock);

    // then
    verify(clientCommunicationServiceMock)
        .sendMessage(argumentCaptor.capture(), eq(sessionCommunicationMock));
    assertThat(argumentCaptor.getValue().getToken()).isEqualTo("poppToken");
    assertThat(argumentCaptor.getValue().getPn()).isEqualTo("pn");
    verify(sessionAccessorMock).getPoppToken("sessionId");
  }
}
