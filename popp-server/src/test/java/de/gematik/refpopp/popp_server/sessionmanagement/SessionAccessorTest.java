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

package de.gematik.refpopp.popp_server.sessionmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionAccessorTest {

  private SessionContainer sessionContainerMock;
  private SessionAccessor sut;

  @BeforeEach
  void setUp() {
    sessionContainerMock = mock(SessionContainer.class);
    sut = new SessionAccessor(sessionContainerMock);
  }

  @Test
  void getSequenceCounter() {
    // given
    final var sessionId = "sessionId";
    when(sessionContainerMock.retrieveSessionData(
            sessionId, SessionStorageKey.SCENARIO_COUNTER, Integer.class))
        .thenReturn(Optional.of(1));

    // when
    final var sequenceCounter = sut.getSequenceCounter(sessionId);

    // then
    assertThat(sequenceCounter).isEqualTo(1);
    verify(sessionContainerMock)
        .retrieveSessionData(sessionId, SessionStorageKey.SCENARIO_COUNTER, Integer.class);
  }

  @Test
  void getClientSessionId() {
    // given
    final var sessionId = "sessionId";
    when(sessionContainerMock.retrieveSessionData(
            sessionId, SessionStorageKey.CLIENT_SESSION_ID, String.class))
        .thenReturn(Optional.of("clientSessionId"));

    // when
    final var clientSessionId = sut.getClientSessionId(sessionId);

    // then
    assertThat(clientSessionId).isEqualTo("clientSessionId");
    verify(sessionContainerMock)
        .retrieveSessionData(sessionId, SessionStorageKey.CLIENT_SESSION_ID, String.class);
  }

  @Test
  void getPoppToken() {
    // given
    final var sessionId = "sessionId";
    when(sessionContainerMock.retrieveSessionData(
            sessionId, SessionStorageKey.JWT_TOKEN, String.class))
        .thenReturn(Optional.of("poppToken"));

    // when
    final var poppToken = sut.getPoppToken(sessionId);

    // then
    assertThat(poppToken).isEqualTo("poppToken");
    verify(sessionContainerMock)
        .retrieveSessionData(sessionId, SessionStorageKey.JWT_TOKEN, String.class);
  }

  @Test
  void getCardConnectionType() {
    // given
    final var sessionId = "sessionId";
    when(sessionContainerMock.retrieveSessionData(
            sessionId, SessionStorageKey.CARD_CONNECTION_TYPE, CardConnectionType.class))
        .thenReturn(Optional.of(CardConnectionType.CONTACT_STANDARD));

    // when
    final var cardConnectionType = sut.getCardConnectionType(sessionId);

    // then
    assertThat(cardConnectionType).isEqualTo(CardConnectionType.CONTACT_STANDARD);
    verify(sessionContainerMock)
        .retrieveSessionData(
            sessionId, SessionStorageKey.CARD_CONNECTION_TYPE, CardConnectionType.class);
  }

  @Test
  void getCommunicationMode() {
    // given
    final var sessionId = "sessionId";
    when(sessionContainerMock.retrieveSessionData(
            sessionId, SessionStorageKey.COMMUNICATION_MODE, CommunicationMode.class))
        .thenReturn(Optional.of(CommunicationMode.CONTACT));

    // when
    final var cardVersion = sut.getCommunicationMode(sessionId);

    // then
    assertThat(cardVersion).isEqualTo(CommunicationMode.CONTACT);
    verify(sessionContainerMock)
        .retrieveSessionData(
            sessionId, SessionStorageKey.COMMUNICATION_MODE, CommunicationMode.class);
  }

  @Test
  void getPatientProofTime() {
    // given
    final var sessionId = "sessionId";
    when(sessionContainerMock.retrieveSessionData(anyString(), any(), any()))
        .thenReturn(Optional.of(1L));

    // when
    final var patientProofTime = sut.getPatientProofTime(sessionId);

    // then
    assertThat(patientProofTime).isEqualTo(1L);
    verify(sessionContainerMock)
        .retrieveSessionData(sessionId, SessionStorageKey.PATIENT_PROOF_TIME, Long.class);
  }

  @Test
  void getOpenContactIccCvcList() {
    // given
    final var sessionId = "sessionId";
    when(sessionContainerMock.retrieveSessionData(
            eq(sessionId), eq(SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST), any()))
        .thenReturn(
            Optional.of(
                List.of(
                    new StepDefinition(
                        "name1", "description", "commandApdu", List.of("expectedStatusWord")))));

    // when
    final var openContactIccCvcList = sut.getOpenContactIccCvcList(sessionId);

    // then
    assertThat(openContactIccCvcList).isPresent();
    final var stepDefinitions = openContactIccCvcList.get();
    assertThat(stepDefinitions).hasSize(1);
  }

  @Test
  void getCommunicationModeOrDefaultValue() {
    // given
    final var sessionId = "sessionId";
    when(sessionContainerMock.retrieveSessionData(anyString(), any(), any()))
        .thenReturn(Optional.of(CommunicationMode.CONTACT));

    // when
    final var value = sut.getCommunicationModeOrDefaultValue(sessionId);

    // then
    assertThat(value).isEqualTo(CommunicationMode.CONTACT);
    verify(sessionContainerMock)
        .retrieveSessionData(
            sessionId, SessionStorageKey.COMMUNICATION_MODE, CommunicationMode.class);
  }

  @Test
  void getCommunicationModeOrDefaultValueReturnsDefaultValue() {
    // given
    final var sessionId = "sessionId";

    // when
    final var value = sut.getCommunicationModeOrDefaultValue(sessionId);

    // then
    assertThat(value).isEqualTo(CommunicationMode.UNDEFINED);
    verify(sessionContainerMock)
        .retrieveSessionData(
            sessionId, SessionStorageKey.COMMUNICATION_MODE, CommunicationMode.class);
  }

  @Test
  void getNonce() {
    // given
    final var sessionId = "sessionId";
    when(sessionContainerMock.retrieveSessionData(anyString(), any(), any()))
        .thenReturn(Optional.of("nonce".getBytes()));

    // when
    final var nonce = sut.getNonce(sessionId);

    // then
    assertThat(nonce).isNotEmpty();
    verify(sessionContainerMock)
        .retrieveSessionData(sessionId, SessionStorageKey.NONCE, byte[].class);
  }

  @Test
  void storeNonce() {
    // given
    final var sessionId = "sessionId";

    // when
    sut.storeNonce(sessionId, "nonce".getBytes());

    // then
    verify(sessionContainerMock)
        .storeSessionData(sessionId, SessionStorageKey.NONCE, "nonce".getBytes());
  }

  @Test
  void storeCommunicationMode() {
    // given
    final var sessionId = "sessionId";

    // when
    sut.storeCommunicationMode(sessionId, CommunicationMode.CONTACT);

    // then
    verify(sessionContainerMock)
        .storeSessionData(
            sessionId, SessionStorageKey.COMMUNICATION_MODE, CommunicationMode.CONTACT);
  }

  @Test
  void storeAdditionalSteps() {
    // given
    final var sessionId = "sessionId";
    final var cvcList =
        List.of(new StepDefinition("name1", "description", "commandApdu", List.of("9000")));
    // when
    sut.storeAdditionalSteps(sessionId, cvcList);

    // then
    verify(sessionContainerMock)
        .storeSessionData(sessionId, SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST, cvcList);
  }

  @Test
  void storeClientSessionId() {
    // given
    final var sessionId = "sessionId";
    final var clientSessionId = "clientSessionId";

    // when
    sut.storeClientSessionId(sessionId, clientSessionId);

    // then
    verify(sessionContainerMock)
        .storeSessionData(sessionId, SessionStorageKey.CLIENT_SESSION_ID, clientSessionId);
  }

  @Test
  void storeCardConnectionType() {
    // given
    final var sessionId = "sessionId";

    // when
    sut.storeCardConnectionType(sessionId, CardConnectionType.CONTACT_CONNECTOR);

    // then
    verify(sessionContainerMock)
        .storeSessionData(
            sessionId,
            SessionStorageKey.CARD_CONNECTION_TYPE,
            CardConnectionType.CONTACT_CONNECTOR);
  }

  @Test
  void storeSequenceCounter() {
    // given
    final var sessionId = "sessionId";

    // when
    sut.storeSequenceCounter(sessionId, 1);

    // then
    verify(sessionContainerMock).storeSessionData(sessionId, SessionStorageKey.SCENARIO_COUNTER, 1);
  }

  @Test
  void storeJwtToken() {
    // given
    final var sessionId = "sessionId";

    // when
    sut.storeJwtToken(sessionId, "token");

    // then
    verify(sessionContainerMock).storeSessionData(sessionId, SessionStorageKey.JWT_TOKEN, "token");
  }

  @Test
  void storeCvc() {
    // given
    final var sessionId = "sessionId";
    final var cvc = new byte[] {1, 2, 3};

    // when
    sut.storeCvc(sessionId, cvc);

    // then
    verify(sessionContainerMock).storeSessionData(sessionId, SessionStorageKey.CVC, cvc);
  }

  @Test
  void getCvc() {
    // given
    final var sessionId = "sessionId";
    final var cvc = new byte[] {1, 2, 3};
    when(sessionContainerMock.retrieveSessionData(sessionId, SessionStorageKey.CVC, byte[].class))
        .thenReturn(Optional.of(cvc));

    // when
    final var result = sut.getCvc(sessionId);

    // then
    assertThat(result).isEqualTo(cvc);
    verify(sessionContainerMock)
        .retrieveSessionData(sessionId, SessionStorageKey.CVC, byte[].class);
  }

  @Test
  void storeCvcCA_storesCvcCAInSession() {
    final var sessionId = "sessionId";
    final var cvc = new byte[] {1, 2, 3};

    sut.storeCvcCA(sessionId, cvc);

    verify(sessionContainerMock).storeSessionData(sessionId, SessionStorageKey.CVC_CA, cvc);
  }

  @Test
  void getCvcCA_returnsStoredCvcCA() {
    final var sessionId = "sessionId";
    final var cvc = new byte[] {1, 2, 3};
    when(sessionContainerMock.retrieveSessionData(
            sessionId, SessionStorageKey.CVC_CA, byte[].class))
        .thenReturn(Optional.of(cvc));

    final var result = sut.getCvcCA(sessionId);

    assertThat(result).isEqualTo(cvc);
    verify(sessionContainerMock)
        .retrieveSessionData(sessionId, SessionStorageKey.CVC_CA, byte[].class);
  }

  @Test
  void storeAut() {
    // given
    final var sessionId = "sessionId";
    final var aut = new byte[] {1, 2, 3};

    // when
    sut.storeAut(sessionId, aut);

    // then
    verify(sessionContainerMock).storeSessionData(sessionId, SessionStorageKey.AUT, aut);
  }

  @Test
  void getAut() {
    // given
    final var sessionId = "sessionId";
    final var aut = new byte[] {1, 2, 3};
    when(sessionContainerMock.retrieveSessionData(sessionId, SessionStorageKey.AUT, byte[].class))
        .thenReturn(Optional.of(aut));

    // when
    final var result = sut.getAut(sessionId);

    // then
    assertThat(result).isEqualTo(aut);
    verify(sessionContainerMock)
        .retrieveSessionData(sessionId, SessionStorageKey.AUT, byte[].class);
  }

  @Test
  void clearSessionData() {
    // given
    final var sessionId = "sessionId";

    // when
    sut.clearSessionData(sessionId);

    // then
    verify(sessionContainerMock).clearSession(sessionId);
  }

  @Test
  void storeScenario() {
    // given
    final var sessionId = "sessionId";
    final var scenario =
        new Scenario(
            "scenarioName",
            List.of(
                new StepDefinition(
                    "name1", "description", "commandApdu", List.of("expectedStatusWord"))));

    // when
    sut.storeScenario(sessionId, scenario);

    // then
    verify(sessionContainerMock).storeScenario(sessionId, scenario);
  }

  @Test
  void storeSessionData() {
    // given
    final var sessionId = "sessionId";

    // when
    sut.storeSessionData(
        sessionId, SessionStorageKey.CARD_CONNECTION_TYPE, CardConnectionType.CONTACT_STANDARD);

    // then
    verify(sessionContainerMock)
        .storeSessionData(
            sessionId, SessionStorageKey.CARD_CONNECTION_TYPE, CardConnectionType.CONTACT_STANDARD);
  }

  @Test
  void storeScenarioCounter() {
    // given
    final var sessionId = "sessionId";

    // when
    sut.storeScenarioCounter(sessionId, 1);

    // then
    verify(sessionContainerMock).storeSessionData(sessionId, SessionStorageKey.SCENARIO_COUNTER, 1);
  }

  @Test
  void retrieveSessionData() {
    // given
    final var sessionId = "sessionId";
    when(sessionContainerMock.retrieveSessionData(
            sessionId, SessionStorageKey.CARD_CONNECTION_TYPE, CardConnectionType.class))
        .thenReturn(Optional.of(CardConnectionType.CONTACT_STANDARD));

    // when
    final var cardConnectionType =
        sut.retrieveSessionData(
            sessionId, SessionStorageKey.CARD_CONNECTION_TYPE, CardConnectionType.class);

    // then
    assertThat(cardConnectionType).isPresent().contains(CardConnectionType.CONTACT_STANDARD);
  }
}
