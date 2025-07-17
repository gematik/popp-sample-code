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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.refpopp.popp_client.cardreader.CardReader;
import de.gematik.refpopp.popp_client.client.CommunicationService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CardRestControllerTest {

  private MockMvc mockMvc;
  private CommunicationService communicationServiceMock;
  private CardReader cardReaderServiceMock;
  private static final String EMPTY_CLIENT_SESSION_ID = "";

  @BeforeEach
  void setUp() {
    communicationServiceMock = Mockito.mock(CommunicationService.class);
    cardReaderServiceMock = Mockito.mock(CardReader.class);
    final var cardRestController =
        new CardRestController(communicationServiceMock, cardReaderServiceMock);

    mockMvc = MockMvcBuilders.standaloneSetup(cardRestController).build();
  }

  @Test
  void getTokenWithContactStandardCallsProcess() throws Exception {
    // given

    // when / then
    mockMvc
        .perform(get("/token/contact-standard").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string("OK"));

    verify(communicationServiceMock)
        .start(CardConnectionType.CONTACT_STANDARD, EMPTY_CLIENT_SESSION_ID);
    verify(cardReaderServiceMock).startCheckForCardReader();
  }

  @Test
  void getTokenWithContactConnectorCallsProcess() throws Exception {
    // given

    // when / then
    mockMvc
        .perform(get("/token/contact-connector").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string("OK"));
    verify(communicationServiceMock).start(CardConnectionType.CONTACT_CONNECTOR, "");
    verify(cardReaderServiceMock, never()).startCheckForCardReader();
  }

  @Test
  void getTokenWithContactConnectorViaTerminalCallsProcess() throws Exception {
    // given

    // when / then
    mockMvc
        .perform(
            get("/token/contact-connector-via-standard-terminal")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string("OK"));

    verify(communicationServiceMock)
        .startConnectorMock(CardConnectionType.CONTACT_CONNECTOR, EMPTY_CLIENT_SESSION_ID);
    verify(cardReaderServiceMock).startCheckForCardReader();
  }

  @Test
  void getTokenWithContactlessConnectorCallsProcess() throws Exception {
    // given

    // when / then
    mockMvc
        .perform(get("/token/contactless-connector").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string("OK"));

    verify(communicationServiceMock)
        .start(CardConnectionType.CONTACTLESS_CONNECTOR, EMPTY_CLIENT_SESSION_ID);
    verify(cardReaderServiceMock, never()).startCheckForCardReader();
  }

  @Test
  void getTokenWithContactlessStandardCallsProcess() throws Exception {
    // given

    // when / then
    mockMvc
        .perform(get("/token/contactless-standard").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string("OK"));

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
            get("/token/contact-connector?clientsessionid=" + sessionId)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string("OK"));

    verify(communicationServiceMock).start(CardConnectionType.CONTACT_CONNECTOR, sessionId);
    verify(cardReaderServiceMock, never()).startCheckForCardReader();
  }

  @Test
  void getTokenWithG3DoesNotCallProcess() throws Exception {
    // given

    // when / then
    mockMvc
        .perform(get("/token/g3").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string("G3 not yet implemented"));

    verify(communicationServiceMock, never()).start(any(), any());
    verify(cardReaderServiceMock, never()).startCheckForCardReader();
  }

  @Test
  void getTokenWithWrongParameterDoesNotCallProcess() throws Exception {
    // given

    // when / then
    mockMvc
        .perform(get("/token/unknown").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string("Unknown command"));

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
        .perform(get("/token/contact-standard").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string("Error during communication"));

    verify(communicationServiceMock)
        .start(CardConnectionType.CONTACT_STANDARD, EMPTY_CLIENT_SESSION_ID);
  }
}
