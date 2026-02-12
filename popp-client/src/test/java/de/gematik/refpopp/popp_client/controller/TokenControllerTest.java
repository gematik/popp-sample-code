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

package de.gematik.refpopp.popp_client.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.refpopp.popp_client.cardreader.CardReader;
import de.gematik.refpopp.popp_client.client.CommunicationService;
import de.gematik.refpopp.popp_client.configuration.CardConnectionTypeConverter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TokenControllerTest {

  private MockMvc mockMvc;
  private CommunicationService communicationServiceMock;
  private CardReader cardReaderServiceMock;
  private static final String EMPTY_CLIENT_SESSION_ID = null;

  @BeforeEach
  void setUp() {
    communicationServiceMock = Mockito.mock(CommunicationService.class);
    cardReaderServiceMock = Mockito.mock(CardReader.class);
    final var cardRestController =
        new TokenController(communicationServiceMock, cardReaderServiceMock);

    mockMvc =
        MockMvcBuilders.standaloneSetup(cardRestController)
            .setCustomArgumentResolvers()
            .setConversionService(withCardConnectionTypeConverter())
            .build();
  }

  private FormattingConversionService withCardConnectionTypeConverter() {
    FormattingConversionService service = new FormattingConversionService();
    service.addConverter(new CardConnectionTypeConverter());
    return service;
  }

  @Test
  void getTokenWithContactStandardCallsProcess() throws Exception {
    // given

    // when / then
    mockMvc
        .perform(
            post("/token")
                .content(
                    "{\"communicationType\": \""
                        + CardConnectionType.CONTACT_STANDARD.getType()
                        + "\"}")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"OK\"}"));

    verify(communicationServiceMock)
        .start(CardConnectionType.CONTACT_STANDARD, EMPTY_CLIENT_SESSION_ID);
    verify(cardReaderServiceMock).startCheckForCardReader();
  }

  @Test
  void getTokenWithContactConnectorCallsProcess() throws Exception {
    // given

    // when / then
    mockMvc
        .perform(
            post("/token")
                .content(
                    "{\"communicationType\": \""
                        + CardConnectionType.CONTACT_CONNECTOR.getType()
                        + "\"}")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"OK\"}"));
    verify(communicationServiceMock)
        .start(CardConnectionType.CONTACT_CONNECTOR, EMPTY_CLIENT_SESSION_ID);
    verify(cardReaderServiceMock, never()).startCheckForCardReader();
  }

  @Test
  void getTokenWithContactConnectorViaTerminalCallsProcess() throws Exception {
    // given
    when(communicationServiceMock.startConnectorMock(any())).thenReturn("dummy-token");

    // when / then
    mockMvc
        .perform(
            post("/token")
                .content(
                    "{\"communicationType\": \""
                        + CardConnectionType.CONTACT_CONNECTOR_VIA_STANDARD_TERMINAL.getType()
                        + "\"}")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.token").exists());

    verify(communicationServiceMock).startConnectorMock(EMPTY_CLIENT_SESSION_ID);
    verify(cardReaderServiceMock).startCheckForCardReader();
  }

  @Test
  void getTokenWithContactlessConnectorCallsProcess() throws Exception {
    // given

    // when / then
    mockMvc
        .perform(
            post("/token")
                .content(
                    "{\"communicationType\": \""
                        + CardConnectionType.CONTACTLESS_CONNECTOR.getType()
                        + "\"}")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"OK\"}"));

    verify(communicationServiceMock)
        .start(CardConnectionType.CONTACTLESS_CONNECTOR, EMPTY_CLIENT_SESSION_ID);
    verify(cardReaderServiceMock, never()).startCheckForCardReader();
  }

  @Test
  void getTokenWithContactlessStandardCallsProcess() throws Exception {
    // given

    // when / then
    mockMvc
        .perform(
            post("/token")
                .content(
                    "{\"communicationType\": \""
                        + CardConnectionType.CONTACTLESS_STANDARD.getType()
                        + "\"}")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"OK\"}"));

    verify(communicationServiceMock)
        .start(CardConnectionType.CONTACTLESS_STANDARD, EMPTY_CLIENT_SESSION_ID);
    verify(cardReaderServiceMock).startCheckForCardReader();
  }

  @Test
  void getTokenWithContactConnectorWithClientSessionId() throws Exception {
    // given
    final var sessionId = UUID.randomUUID().toString();

    // when / then
    mockMvc
        .perform(
            post("/token")
                .content(
                    "{\"communicationType\": \""
                        + CardConnectionType.CONTACT_CONNECTOR.getType()
                        + "\", \"clientSessionId\": \""
                        + sessionId
                        + "\"}")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"OK\"}"));

    verify(communicationServiceMock).start(CardConnectionType.CONTACT_CONNECTOR, sessionId);
    verify(cardReaderServiceMock, never()).startCheckForCardReader();
  }

  @Test
  void getTokenWithG3DoesNotCallProcess() throws Exception {
    // given

    // when / then
    mockMvc
        .perform(
            post("/token")
                .content("{\"communicationType\": \"" + CardConnectionType.G3.getType() + "\"}")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is4xxClientError())
        .andExpect(
            content().json("{\"status\":\"ERROR\",\"errorMessage\":\"G3 not yet implemented\"}"));

    verify(communicationServiceMock, never()).start(any(), any());
    verify(cardReaderServiceMock, never()).startCheckForCardReader();
  }

  @Test
  void getTokenWithWrongParameterDoesNotCallProcess() throws Exception {
    // given

    // when / then
    mockMvc
        .perform(
            post("/token")
                .content(
                    "{\"communicationType\": \"" + CardConnectionType.UNKNOWN.getType() + "\"}")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is4xxClientError())
        .andExpect(
            content()
                .json("{\"status\":\"ERROR\",\"errorMessage\":\"Unsupported type: UNKNOWN\"}"));

    verify(communicationServiceMock, never()).start(any(), any());
  }

  @Test
  void getTokenWithoutParameterDoesNotCallProcess() throws Exception {
    // given

    // when / then
    mockMvc
        .perform(get("/token/").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());

    verify(communicationServiceMock, never()).start(any(), any());
  }

  @Test
  void getTokenReturnsErrorWhenExceptionIsThrown() throws Exception {
    // given
    Mockito.doThrow(new RuntimeException("Error during communication"))
        .when(communicationServiceMock)
        .start(CardConnectionType.CONTACT_STANDARD, EMPTY_CLIENT_SESSION_ID);

    // when / then
    mockMvc
        .perform(
            post("/token")
                .content(
                    "{\"communicationType\": \""
                        + CardConnectionType.CONTACT_STANDARD.getType()
                        + "\"}")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is5xxServerError())
        .andExpect(
            content()
                .string(
                    "{\"status\":\"ERROR\",\"errorMessage\":\"Unexpected error: Error during"
                        + " communication\"}"));

    verify(communicationServiceMock)
        .start(CardConnectionType.CONTACT_STANDARD, EMPTY_CLIENT_SESSION_ID);
  }

  @Test
  void getTokenReturnsGatewayTimeoutWhenTimeoutOccurs() throws Exception {
    Mockito.doThrow(new RuntimeException("wrapper", new java.util.concurrent.TimeoutException()))
        .when(communicationServiceMock)
        .start(CardConnectionType.CONTACT_STANDARD, EMPTY_CLIENT_SESSION_ID);

    mockMvc
        .perform(
            post("/token")
                .content(
                    "{\"communicationType\": \""
                        + CardConnectionType.CONTACT_STANDARD.getType()
                        + "\"}")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isGatewayTimeout())
        .andExpect(
            content()
                .json(
                    """
                    {
                      "status": "ERROR",
                      "errorMessage": "Token generation timed out"
                    }
                    """
                        .trim()));

    verify(communicationServiceMock)
        .start(CardConnectionType.CONTACT_STANDARD, EMPTY_CLIENT_SESSION_ID);
    verify(cardReaderServiceMock).startCheckForCardReader();
  }
}
