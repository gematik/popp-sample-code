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

import static de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey.CARD_CONNECTION_TYPE;
import static de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey.CLIENT_SESSION_ID;
import static de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey.COMMUNICATION_MODE;
import static de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey.SCENARIO_COUNTER;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey;
import java.util.List;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class SessionAccessor {

  private final SessionContainer sessionContainer;

  public SessionAccessor(final SessionContainer sessionContainer) {
    this.sessionContainer = sessionContainer;
  }

  public String getPoppToken(@NonNull final String sessionId) {
    return getSessionDataOrThrow(
        sessionId,
        SessionContainer.SessionStorageKey.JWT_TOKEN,
        String.class,
        "No JWT token found");
  }

  public String getClientSessionId(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId, CLIENT_SESSION_ID, String.class, "No client session ID found");
  }

  public Integer getSequenceCounter(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId, SCENARIO_COUNTER, Integer.class, "No sequence counter found");
  }

  public CardConnectionType getCardConnectionType(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId,
        SessionContainer.SessionStorageKey.CARD_CONNECTION_TYPE,
        CardConnectionType.class,
        "No card connection type found");
  }

  public CommunicationMode getCommunicationMode(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId,
        SessionContainer.SessionStorageKey.COMMUNICATION_MODE,
        CommunicationMode.class,
        "No card communication mode found");
  }

  public CommunicationMode getCommunicationModeOrDefaultValue(final String sessionId) {
    return retrieveSessionData(sessionId, COMMUNICATION_MODE, CommunicationMode.class)
        .orElse(CommunicationMode.UNDEFINED);
  }

  public long getPatientProofTime(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId,
        SessionContainer.SessionStorageKey.PATIENT_PROOF_TIME,
        Long.class,
        "No patient proof time found");
  }

  public byte[] getNonce(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId, SessionContainer.SessionStorageKey.NONCE, byte[].class, "No nonce found");
  }

  public Optional<List<StepDefinition>> getOpenContactIccCvcList(final String sessionId) {
    final ParameterizedTypeReference<List<StepDefinition>> typeRef =
        new ParameterizedTypeReference<>() {};
    return sessionContainer.retrieveSessionData(
        sessionId, SessionContainer.SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST, typeRef.getType());
  }

  public <T> void storeSessionData(
      @NonNull final String sessionId,
      @NonNull final SessionContainer.SessionStorageKey key,
      @NonNull final T value) {
    sessionContainer.storeSessionData(sessionId, key, value);
  }

  public void storeNonce(final String sessionId, final byte[] nonce) {
    storeSessionData(sessionId, SessionStorageKey.NONCE, nonce);
  }

  public void storeCommunicationMode(
      final String sessionId, final CommunicationMode communicationMode) {
    storeSessionData(sessionId, SessionStorageKey.COMMUNICATION_MODE, communicationMode);
  }

  public void storeScenarioCounter(@NonNull final String sessionId, final int value) {
    sessionContainer.storeSessionData(sessionId, SCENARIO_COUNTER, value);
  }

  public void storeJwtToken(@NonNull final String sessionId, @NonNull final String jwtToken) {
    sessionContainer.storeSessionData(
        sessionId, SessionContainer.SessionStorageKey.JWT_TOKEN, jwtToken);
  }

  public void storeAdditionalSteps(final String sessionId, final List<StepDefinition> cvcList) {
    sessionContainer.storeSessionData(
        sessionId, SessionContainer.SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST, cvcList);
  }

  public <T> Optional<T> retrieveSessionData(
      @NonNull final String sessionId,
      @NonNull final SessionContainer.SessionStorageKey key,
      @NonNull final Class<T> type) {
    return sessionContainer.retrieveSessionData(sessionId, key, type);
  }

  public void clearSessionData(@NonNull final String sessionId) {
    sessionContainer.clearSession(sessionId);
  }

  public void storeScenario(@NonNull final String sessionId, @NonNull final Scenario scenario) {
    sessionContainer.storeScenario(sessionId, scenario);
  }

  public void storeClientSessionId(final String sessionId, final String clientSessionId) {
    storeSessionData(sessionId, CLIENT_SESSION_ID, clientSessionId);
  }

  public void storeCardConnectionType(
      final String sessionId, final CardConnectionType cardConnectionType) {
    storeSessionData(sessionId, CARD_CONNECTION_TYPE, cardConnectionType);
  }

  public void storeSequenceCounter(final String sessionId, final int value) {
    storeSessionData(sessionId, SCENARIO_COUNTER, value);
  }

  public void storeCvc(final String sessionId, final byte[] cvc) {
    storeSessionData(sessionId, SessionStorageKey.CVC, cvc);
  }

  public void storeCvcCA(final String sessionId, final byte[] cvc) {
    storeSessionData(sessionId, SessionStorageKey.CVC_CA, cvc);
  }

  public byte[] getCvc(final String sessionId) {
    return getSessionDataOrThrow(sessionId, SessionStorageKey.CVC, byte[].class, "No CVC found");
  }

  public byte[] getCvcCA(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId, SessionStorageKey.CVC_CA, byte[].class, "No CVC CA found");
  }

  public void storeAut(final String sessionId, final byte[] data) {
    storeSessionData(sessionId, SessionStorageKey.AUT, data);
  }

  public byte[] getAut(final String sessionId) {
    return getSessionDataOrThrow(sessionId, SessionStorageKey.AUT, byte[].class, "No AUT found");
  }

  private <T> T getSessionDataOrThrow(
      @NonNull final String sessionId,
      @NonNull final SessionContainer.SessionStorageKey key,
      @NonNull final Class<T> type,
      @NonNull final String errorMessage) {
    return (T)
        sessionContainer
            .retrieveSessionData(sessionId, key, type)
            .orElseThrow(() -> new ScenarioException(sessionId, errorMessage, "errorCode"));
  }
}
