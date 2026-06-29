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
import static org.mockito.Mockito.*;

import de.gematik.poppcommons.api.enums.BdeErrorCode;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.poppcommons.api.messages.PoPPMessage;
import de.gematik.poppcommons.api.messages.StartMessage;
import de.gematik.refpopp.popp_server.scenario.common.orchestrator.MessageHandlerOrchestrator;
import de.gematik.refpopp.popp_server.scenario.common.token.UserInfo;
import de.gematik.refpopp.popp_server.scenario.contactbased.ContactBasedScenariosProvider;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.ObjectMapper;

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
    contactBasedScenariosProvider = new ContactBasedScenariosProvider();
    sut =
        new WebSocketHandler(
            sessionContainerMock,
            messageHandlerOrchestratorMock,
            objectMapper,
            contactBasedScenariosProvider);
    sessionMock = mock(WebSocketSession.class);
    when(sessionMock.getHandshakeHeaders()).thenReturn(new HttpHeaders());
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
  void handleTextMessageStartsEgkProcessOrchestrator() {
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
        {"type":"START_MESSAGE","version":"1.0.0","cardConnectionType":"CONNECTOR"}
        """;
    final var message = new TextMessage(payload);
    final var objectMapperMock = mock(ObjectMapper.class);
    final var messageCapture = ArgumentCaptor.forClass(TextMessage.class);
    doThrow(new StreamReadException("invalid json"))
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
        {"type":"Start","version":"1.0.0","cardConnectionType":"contact-connector"}
        """;
    final var message = new TextMessage(payload);
    doThrow(new ScenarioException("test", "error", BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR))
        .when(messageHandlerOrchestratorMock)
        .orchestrate(any(), any());
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

  @Test
  void afterConnectionEstablishedStoresUserInfoWhenHeaderContainsValidBase64Json() {
    // given
    when(sessionMock.getId()).thenReturn("session1");
    final var headers = new HttpHeaders();
    final var userInfoJson =
        """
        {"identifier":"actor-123","professionOID":"1.2.276.0.76.4.50","organizationName":"gematik","commonName":"max.mustermann"}
        """;
    headers.add(
        "zeta-user-info",
        Base64.getEncoder().encodeToString(userInfoJson.getBytes(StandardCharsets.UTF_8)));
    when(sessionMock.getHandshakeHeaders()).thenReturn(headers);
    final var userInfoCaptor = ArgumentCaptor.forClass(UserInfo.class);

    // when
    sut.afterConnectionEstablished(sessionMock);

    // then
    verify(sessionContainerMock)
        .storeSessionData(
            eq("session1"),
            eq(SessionContainer.SessionStorageKey.ZETA_USER_INFO),
            userInfoCaptor.capture());
    assertThat(userInfoCaptor.getValue())
        .isEqualTo(new UserInfo("actor-123", "1.2.276.0.76.4.50", "gematik", "max.mustermann"));
  }

  @Test
  void afterConnectionEstablishedDoesNotStoreUserInfoWhenHeaderIsInvalidBase64() {
    // given
    when(sessionMock.getId()).thenReturn("session1");
    final var headers = new HttpHeaders();
    headers.add("zeta-user-info", "%%%invalid%%%");
    when(sessionMock.getHandshakeHeaders()).thenReturn(headers);

    // when
    sut.afterConnectionEstablished(sessionMock);

    // then
    verify(sessionContainerMock, never())
        .storeSessionData(
            eq("session1"),
            eq(SessionContainer.SessionStorageKey.ZETA_USER_INFO),
            any(UserInfo.class));
  }

  @Test
  void afterConnectionEstablishedDoesNotStoreUserInfoWhenHeaderContainsInvalidJson() {
    // given
    when(sessionMock.getId()).thenReturn("session1");
    final var headers = new HttpHeaders();
    final var invalidJson = "{not-valid-json}";
    headers.add(
        "zeta-user-info",
        Base64.getEncoder().encodeToString(invalidJson.getBytes(StandardCharsets.UTF_8)));
    when(sessionMock.getHandshakeHeaders()).thenReturn(headers);

    // when
    sut.afterConnectionEstablished(sessionMock);

    // then
    verify(sessionContainerMock, never())
        .storeSessionData(
            eq("session1"),
            eq(SessionContainer.SessionStorageKey.ZETA_USER_INFO),
            any(UserInfo.class));
  }

  @Test
  void handleTextMessageClosesSessionWhenScenarioExceptionOccurs() throws IOException {
    // given
    final var payload =
        """
        {"type":"Start","version":"1.0.0","cardConnectionType":"contact-connector"}
        """;
    final var message = new TextMessage(payload);
    doThrow(new ScenarioException("test", "error", BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR))
        .when(messageHandlerOrchestratorMock)
        .orchestrate(any(), any());

    // when
    sut.handleTextMessage(sessionMock, message);

    // then
    verify(sessionMock).sendMessage(any(TextMessage.class));
    verify(sessionMock).close();
  }

  @Test
  void handleTextMessageClosesSessionWhenJsonProcessingFails() throws IOException {
    // given
    final var payload =
        """
        {"type":"START_MESSAGE","version":"1.0.0","cardConnectionType":"CONNECTOR"}
        """;
    final var message = new TextMessage(payload);
    final var objectMapperMock = mock(ObjectMapper.class);
    doThrow(new StreamReadException("invalid json"))
        .when(objectMapperMock)
        .readValue(anyString(), eq(PoPPMessage.class));
    when(objectMapperMock.writeValueAsString(any())).thenReturn("type, ERROR_MESSAGE");
    final var handlerWithMockedMapper =
        new WebSocketHandler(
            sessionContainerMock,
            messageHandlerOrchestratorMock,
            objectMapperMock,
            contactBasedScenariosProvider);

    // when
    handlerWithMockedMapper.handleTextMessage(sessionMock, message);

    // then
    verify(sessionMock).sendMessage(any(TextMessage.class));
    verify(sessionMock).close();
  }
}
