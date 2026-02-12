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

package de.gematik.refpopp.popp_server.scenario.common.token;

import static de.gematik.refpopp.popp_server.scenario.common.token.EnumTokenClaimsKey.ACTOR_ID;
import static de.gematik.refpopp.popp_server.scenario.common.token.EnumTokenClaimsKey.ACTOR_PROFESSION_OID;
import static de.gematik.refpopp.popp_server.scenario.common.token.EnumTokenClaimsKey.AUTHORIZATION_DETAILS;
import static de.gematik.refpopp.popp_server.scenario.common.token.EnumTokenClaimsKey.INSURER_ID;
import static de.gematik.refpopp.popp_server.scenario.common.token.EnumTokenClaimsKey.ISSUER;
import static de.gematik.refpopp.popp_server.scenario.common.token.EnumTokenClaimsKey.PATIENT_ID;
import static de.gematik.refpopp.popp_server.scenario.common.token.EnumTokenClaimsKey.PATIENT_PROOF_TIME;
import static de.gematik.refpopp.popp_server.scenario.common.token.EnumTokenClaimsKey.PROOF_METHOD;
import static de.gematik.refpopp.popp_server.scenario.common.token.EnumTokenClaimsKey.VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.enums.ProofMethod;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509Data;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TokenClaimsTest {

  SessionContainer sessionContainer;
  X509Data x509Data;
  String sessionId;

  String version = "1.0.0";
  String issuer = "https://popp.example.com";

  Long patientProofTime = 11223344L;
  String actorId = "telematik-id";
  String actorProfessionOid = "1.2.276.0.76.4.50";
  String authorizationDetails = "details";
  X509Data.Subject subject;

  TokenClaims sut;

  @BeforeEach
  void setUp() {
    sessionId = UUID.randomUUID().toString();
    sessionContainer = mock(SessionContainer.class);
    x509Data = mock(X509Data.class);
    sut = new TokenClaims(sessionContainer);
    subject =
        new X509Data.Subject(
            "Hans Maier",
            "Dr.",
            "Hans",
            "Maier",
            "kv-1234567890",
            "ik-1234567890",
            "BKK ProNova",
            "DE");

    ReflectionTestUtils.setField(sut, "version", version);
    ReflectionTestUtils.setField(sut, "issuer", issuer);
    ReflectionTestUtils.setField(sut, "actorId", actorId);
    ReflectionTestUtils.setField(sut, "actorProfessionOid", actorProfessionOid);
    ReflectionTestUtils.setField(sut, "authorizationDetails", authorizationDetails);

    when(sessionContainer.retrieveSessionData(
            sessionId,
            SessionContainer.SessionStorageKey.CARD_CONNECTION_TYPE,
            CardConnectionType.class))
        .thenReturn(Optional.of(CardConnectionType.CONTACT_STANDARD));

    when(sessionContainer.retrieveSessionData(
            sessionId, SessionContainer.SessionStorageKey.PATIENT_PROOF_TIME, Long.class))
        .thenReturn(Optional.ofNullable(patientProofTime));

    when(x509Data.getSubject()).thenReturn(subject);
  }

  @Test
  void createPoppClaimsWithContactProofMethod() {
    // given

    // when
    final Map<String, Object> claims = sut.createPoppClaims(x509Data, sessionId);

    // then
    assertThat(claims)
        .containsEntry(VERSION.getKeyValue(), version)
        .containsEntry(ISSUER.getKeyValue(), issuer)
        .containsEntry(PATIENT_PROOF_TIME.getKeyValue(), patientProofTime)
        .containsEntry(
            PROOF_METHOD.getKeyValue(), ProofMethod.EHC_PRACTITIONER_TRUSTEDCHANNEL.getValue())
        .containsEntry(PATIENT_ID.getKeyValue(), subject.kvNr())
        .containsEntry(INSURER_ID.getKeyValue(), subject.ikNr())
        .containsEntry(ACTOR_ID.getKeyValue(), actorId)
        .containsEntry(ACTOR_PROFESSION_OID.getKeyValue(), actorProfessionOid)
        .containsEntry(AUTHORIZATION_DETAILS.getKeyValue(), authorizationDetails);
  }

  @Test
  void createPoppClaimsWithUnknownProofMethod() {
    // given
    when(sessionContainer.retrieveSessionData(
            sessionId,
            SessionContainer.SessionStorageKey.CARD_CONNECTION_TYPE,
            CardConnectionType.class))
        .thenReturn(Optional.of(CardConnectionType.UNKNOWN));

    // when
    final Map<String, Object> claims = sut.createPoppClaims(x509Data, sessionId);

    // then
    assertThat(claims)
        .containsEntry(VERSION.getKeyValue(), version)
        .containsEntry(ISSUER.getKeyValue(), issuer)
        .containsEntry(PATIENT_PROOF_TIME.getKeyValue(), patientProofTime)
        .containsEntry(PROOF_METHOD.getKeyValue(), "unknown")
        .containsEntry(PATIENT_ID.getKeyValue(), subject.kvNr())
        .containsEntry(INSURER_ID.getKeyValue(), subject.ikNr())
        .containsEntry(ACTOR_ID.getKeyValue(), actorId)
        .containsEntry(ACTOR_PROFESSION_OID.getKeyValue(), actorProfessionOid)
        .containsEntry(AUTHORIZATION_DETAILS.getKeyValue(), authorizationDetails);
  }

  @Test
  void createPoppClaimsWithContactlessProofMethod() {
    // given
    when(sessionContainer.retrieveSessionData(
            sessionId,
            SessionContainer.SessionStorageKey.CARD_CONNECTION_TYPE,
            CardConnectionType.class))
        .thenReturn(Optional.of(CardConnectionType.CONTACTLESS_STANDARD));

    // when
    final Map<String, Object> claims = sut.createPoppClaims(x509Data, sessionId);

    // then
    assertThat(claims)
        .containsEntry(VERSION.getKeyValue(), version)
        .containsEntry(ISSUER.getKeyValue(), issuer)
        .containsEntry(PATIENT_PROOF_TIME.getKeyValue(), patientProofTime)
        .containsEntry(
            PROOF_METHOD.getKeyValue(), ProofMethod.EHC_PRACTITIONER_CVC_AUTHENTICATED.getValue())
        .containsEntry(PATIENT_ID.getKeyValue(), subject.kvNr())
        .containsEntry(INSURER_ID.getKeyValue(), subject.ikNr())
        .containsEntry(ACTOR_ID.getKeyValue(), actorId)
        .containsEntry(ACTOR_PROFESSION_OID.getKeyValue(), actorProfessionOid)
        .containsEntry(AUTHORIZATION_DETAILS.getKeyValue(), authorizationDetails);
  }

  @Test
  void createPoppClaimsWhenNoConnectionTypeAvailable() {
    // given
    when(sessionContainer.retrieveSessionData(
            sessionId,
            SessionContainer.SessionStorageKey.CARD_CONNECTION_TYPE,
            CardConnectionType.class))
        .thenReturn(Optional.empty());

    // when / then
    assertThatExceptionOfType(ScenarioException.class)
        .isThrownBy(() -> sut.createPoppClaims(x509Data, sessionId))
        .withMessage("No card connection type found");
  }

  @Test
  void createConnectorClaims() {
    // given
    final var standardScenarioMessage = StandardScenarioMessage.builder().build();

    // when
    final var connectorClaims = sut.createConnectorClaims(standardScenarioMessage);

    // then
    assertThat(connectorClaims).containsEntry("message", standardScenarioMessage);
  }
}
