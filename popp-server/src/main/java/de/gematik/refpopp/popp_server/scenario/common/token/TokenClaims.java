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

import static de.gematik.poppcommons.api.enums.CardConnectionType.CONTACTLESS_CONNECTOR;
import static de.gematik.poppcommons.api.enums.CardConnectionType.CONTACTLESS_STANDARD;
import static de.gematik.poppcommons.api.enums.CardConnectionType.CONTACT_CONNECTOR;
import static de.gematik.poppcommons.api.enums.CardConnectionType.CONTACT_STANDARD;
import static de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey.CARD_CONNECTION_TYPE;
import static de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey.PATIENT_PROOF_TIME;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.enums.ProofMethod;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509Data;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class TokenClaims {

  @Value("${jwt-token.popp.issuer:https://popp.example.com}")
  private String issuer;

  @Value("${jwt-token.popp.version:1.0.0}")
  private String version;

  @Value("${jwt-token.popp.actor-id:telematik-id}")
  private String actorId;

  @Value("${jwt-token.popp.actor-profession-oid:1.2.276.0.76.4.50}")
  private String actorProfessionOid;

  @Value("${jwt-token.popp.authorization-details:details}")
  private String authorizationDetails;

  private final SessionContainer sessionContainer;

  TokenClaims(final SessionContainer sessionContainer) {
    this.sessionContainer = sessionContainer;
  }

  Map<String, Object> createPoppClaims(final X509Data x509Data, final String sessionId) {
    final var claims = new HashMap<String, Object>();
    claims.put(EnumTokenClaimsKey.VERSION.getKeyValue(), version);
    claims.put(EnumTokenClaimsKey.ISSUER.getKeyValue(), issuer);
    claims.put(EnumTokenClaimsKey.IAT.getKeyValue(), Instant.now().getEpochSecond());
    claims.put(EnumTokenClaimsKey.PROOF_METHOD.getKeyValue(), getProofMethod(sessionId));
    claims.put(EnumTokenClaimsKey.PATIENT_PROOF_TIME.getKeyValue(), getPatientProofTime(sessionId));
    claims.put(EnumTokenClaimsKey.PATIENT_ID.getKeyValue(), x509Data.getSubject().kvNr());
    claims.put(EnumTokenClaimsKey.INSURER_ID.getKeyValue(), x509Data.getSubject().ikNr());
    claims.put(EnumTokenClaimsKey.ACTOR_ID.getKeyValue(), actorId);
    claims.put(EnumTokenClaimsKey.ACTOR_PROFESSION_OID.getKeyValue(), actorProfessionOid);
    claims.put(EnumTokenClaimsKey.AUTHORIZATION_DETAILS.getKeyValue(), authorizationDetails);
    return claims;
  }

  Map<String, Object> createConnectorClaims(final StandardScenarioMessage standardScenarioMessage) {
    final var claims = new HashMap<String, Object>();
    claims.put("message", standardScenarioMessage);

    return claims;
  }

  private Long getPatientProofTime(final String sessionId) {
    return (Long)
        sessionContainer
            .retrieveSessionData(sessionId, PATIENT_PROOF_TIME, Long.class)
            .orElse(Instant.now().getEpochSecond());
  }

  private String getProofMethod(final String sessionId) {
    final var cardConnectionType =
        (CardConnectionType)
            sessionContainer
                .retrieveSessionData(sessionId, CARD_CONNECTION_TYPE, CardConnectionType.class)
                .orElseThrow(
                    () ->
                        new ScenarioException(
                            sessionId, "No card connection type found", "errorCode"));
    if (cardConnectionType == CONTACT_STANDARD || cardConnectionType == CONTACT_CONNECTOR) {
      return ProofMethod.EHC_PRACTITIONER_TRUSTEDCHANNEL.toString();
    } else if (cardConnectionType == CONTACTLESS_STANDARD
        || cardConnectionType == CONTACTLESS_CONNECTOR) {
      return ProofMethod.EHC_PRACTITIONER_CVC_AUTHENTICATED.toString();
    }

    return "unknown";
  }
}
