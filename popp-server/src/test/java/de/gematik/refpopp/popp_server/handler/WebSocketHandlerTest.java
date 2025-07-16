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

package de.gematik.refpopp.popp_server.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.poppcommons.api.messages.PoPPMessage;
import de.gematik.poppcommons.api.messages.StartMessage;
import de.gematik.refpopp.popp_server.scenario.common.orchestrator.MessageHandlerOrchestrator;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.contactbased.ContactBasedScenariosProvider;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class WebSocketHandlerTest {

  private WebSocketHandler sut;
  private SessionContainer sessionContainerMock;
  private WebSocketSession sessionMock;
  private MessageHandlerOrchestrator messageHandlerOrchestratorMock;
  private ObjectMapper objectMapper;
  private ContactBasedScenariosProvider contactBasedScenariosProvider;

  @BeforeEach
  void setUp() {
    sessionContainerMock = mock(SessionContainer.class);
    messageHandlerOrchestratorMock = mock(MessageHandlerOrchestrator.class);
    objectMapper = new ObjectMapper();
    contactBasedScenariosProvider = createScenarioProperties();
    sut =
        new WebSocketHandler(
            sessionContainerMock,
            messageHandlerOrchestratorMock,
            objectMapper,
            contactBasedScenariosProvider);
    sessionMock = mock(WebSocketSession.class);
  }

  @Test
  void afterConnectionEstablishedStoresSessionIfNotExists() {
    // given
    when(sessionMock.getId()).thenReturn("session1");
    when(sessionContainerMock.containsScenario("session1")).thenReturn(false);
    final var firstScenario = contactBasedScenariosProvider.getScenarios().getFirst();

    // when
    sut.afterConnectionEstablished(sessionMock);

    // then
    verify(sessionContainerMock).storeScenario("session1", firstScenario);
  }

  @Test
  void handleTextMessageStartsEgkProcessOrchestrator() throws IOException {
    // given
    final var payload =
        """
        {
            "type": "Start",
            "version": "1.0.0",
            "clientSessionId": "123e4567-e89b-12d3-a456-426614174000",
            "cardConnectionType": "contact-standard"
          }

        """;

    final var test =
        """
        {"type":"Start","version":"1.0.0","clientSessionId":"123e4567-e89b-12d3-a456-426614174000","cardConnectionType":"contact-standard"}
        """;
    final var message = new TextMessage(payload);

    final var expectedStartMessage = objectMapper.readValue(test, PoPPMessage.class);
    final var poppMessageCapture = ArgumentCaptor.forClass(PoPPMessage.class);

    // when
    sut.handleTextMessage(sessionMock, message);

    // then
    verify(messageHandlerOrchestratorMock)
        .orchestrate(poppMessageCapture.capture(), any(SessionCommunication.class));
    assertThat(poppMessageCapture.getValue())
        .usingRecursiveComparison()
        .isEqualTo(expectedStartMessage);
  }

  @Test
  void handleTextMessageThrowsJsonProcessingException() throws IOException {
    // given
    final var payload =
        """
        {"type":"START_MESSAGE","version":"1.0","cardConnectionType":"CONNECTOR"}
        """;
    final var message = new TextMessage(payload);
    final var objectMapperMock = mock(ObjectMapper.class);
    final var messageCapture = ArgumentCaptor.forClass(TextMessage.class);
    doThrow(JsonProcessingException.class)
        .when(objectMapperMock)
        .readValue(anyString(), eq(PoPPMessage.class));
    when(objectMapperMock.writeValueAsString(any())).thenReturn("type, ERROR_MESSAGE");
    final var webSocketHandlerWithMockedObjectMapper =
        new WebSocketHandler(
            sessionContainerMock,
            messageHandlerOrchestratorMock,
            objectMapperMock,
            contactBasedScenariosProvider);

    // when
    webSocketHandlerWithMockedObjectMapper.handleTextMessage(sessionMock, message);

    // then
    verify(sessionMock).sendMessage(messageCapture.capture());
    final var textMessage = messageCapture.getValue();
    assertThat(textMessage.getPayload()).contains("type", "ERROR_MESSAGE");
    verifyNoInteractions(messageHandlerOrchestratorMock, sessionContainerMock);
  }

  @Test
  void handleTextMessageThrowsScenarioException() throws IOException {
    // given
    final var payload =
        """
        {"type":"Start","version":"1.0","cardConnectionType":"contact-connector"}
        """;
    final var message = new TextMessage(payload);
    doThrow(ScenarioException.class).when(messageHandlerOrchestratorMock).orchestrate(any(), any());
    final var messageCapture = ArgumentCaptor.forClass(TextMessage.class);

    // when
    sut.handleTextMessage(sessionMock, message);

    // then
    verify(messageHandlerOrchestratorMock)
        .orchestrate(any(StartMessage.class), any(SessionCommunication.class));
    verify(sessionMock).sendMessage(messageCapture.capture());
    final var textMessage = messageCapture.getValue();
    assertThat(textMessage.getPayload()).contains("type", "Error");
    verifyNoInteractions(sessionContainerMock);
  }

  @Test
  void afterConnectionClosedRemovesSession() {
    // given
    when(sessionMock.getId()).thenReturn("session1");
    final CloseStatus status = CloseStatus.NORMAL;

    // when
    sut.afterConnectionClosed(sessionMock, status);

    // then
    verify(sessionContainerMock).clearSession("session1");
  }

  private ContactBasedScenariosProvider createScenarioProperties() {
    final var expectedStatusWord = List.of("9000", "6281");
    final var state1 = new StepDefinition("name1", "scenario1 state1", "apdu1", expectedStatusWord);
    final var state2 = new StepDefinition("name2", "scenario1 state2", "apdu2", expectedStatusWord);
    final var scenario1 = new Scenario("scenario1", List.of(state1, state2));
    final var scenario2State1 =
        new StepDefinition("name21", "scenario2 state1", "apdu1", expectedStatusWord);
    final var scenario2State2 =
        new StepDefinition("name22", "scenario2 state2", "apdu2", expectedStatusWord);
    final var scenario2 = new Scenario("scenario2", List.of(scenario2State1, scenario2State2));

    return new ContactBasedScenariosProvider(List.of(scenario1, scenario2));
  }
}
