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

import de.gematik.poppcommons.api.enums.BdeErrorCode;
import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey;
import java.util.List;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/** Provides typed access to data stored in logical and transport sessions. */
@Component
public class SessionAccessor {

  private final SessionContainer sessionContainer;

  /**
   * Creates an accessor backed by the supplied session container.
   *
   * @param sessionContainer the container that persists session data
   */
  public SessionAccessor(final SessionContainer sessionContainer) {
    this.sessionContainer = sessionContainer;
  }

  /**
   * Returns the PoPP token stored for a session.
   *
   * @param sessionId the identifier of the session
   * @return the stored PoPP token
   * @throws ScenarioException if no token is stored
   */
  public String getPoppToken(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId,
        SessionContainer.SessionStorageKey.JWT_TOKEN,
        String.class,
        "No JWT token found");
  }

  /**
   * Returns the client session identifier stored for a session.
   *
   * @param sessionId the identifier of the session
   * @return the client session identifier
   * @throws ScenarioException if no client session identifier is stored
   */
  public String getClientSessionId(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId, CLIENT_SESSION_ID, String.class, "No client session ID found");
  }

  /**
   * Returns the scenario sequence counter stored for a session.
   *
   * @param sessionId the identifier of the session
   * @return the sequence counter
   * @throws ScenarioException if no sequence counter is stored
   */
  public Integer getSequenceCounter(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId, SCENARIO_COUNTER, Integer.class, "No sequence counter found");
  }

  /**
   * Returns the card connection type stored for a session.
   *
   * @param sessionId the identifier of the session
   * @return the card connection type
   * @throws ScenarioException if no card connection type is stored
   */
  public CardConnectionType getCardConnectionType(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId,
        SessionContainer.SessionStorageKey.CARD_CONNECTION_TYPE,
        CardConnectionType.class,
        "No card connection type found");
  }

  /**
   * Returns the communication mode stored for a session.
   *
   * @param sessionId the identifier of the session
   * @return the communication mode
   * @throws ScenarioException if no communication mode is stored
   */
  public CommunicationMode getCommunicationMode(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId,
        SessionContainer.SessionStorageKey.COMMUNICATION_MODE,
        CommunicationMode.class,
        "No card communication mode found");
  }

  /**
   * Returns the stored communication mode or {@link CommunicationMode#UNDEFINED} if none exists.
   *
   * @param sessionId the identifier of the session
   * @return the stored communication mode or the default value
   */
  public CommunicationMode getCommunicationModeOrDefaultValue(final String sessionId) {
    return retrieveSessionData(sessionId, COMMUNICATION_MODE, CommunicationMode.class)
        .orElse(CommunicationMode.UNDEFINED);
  }

  /**
   * Returns the patient proof time stored for a session.
   *
   * @param sessionId the identifier of the session
   * @return the patient proof time
   * @throws ScenarioException if no patient proof time is stored
   */
  public long getPatientProofTime(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId,
        SessionContainer.SessionStorageKey.PATIENT_PROOF_TIME,
        Long.class,
        "No patient proof time found");
  }

  /**
   * Returns the nonce stored for a session.
   *
   * @param sessionId the identifier of the session
   * @return the nonce
   * @throws ScenarioException if no nonce is stored
   */
  public byte[] getNonce(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId, SessionContainer.SessionStorageKey.NONCE, byte[].class, "No nonce found");
  }

  /**
   * Returns additional contact-card CVC steps, if they are stored for a session.
   *
   * @param sessionId the identifier of the session
   * @return an optional containing the additional CVC steps
   */
  public Optional<List<StepDefinition>> getOpenContactIccCvcList(final String sessionId) {
    final ParameterizedTypeReference<List<StepDefinition>> typeRef =
        new ParameterizedTypeReference<>() {};
    return sessionContainer.retrieveSessionData(
        sessionId, SessionContainer.SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST, typeRef.getType());
  }

  /**
   * Stores a typed value under the supplied session storage key.
   *
   * @param sessionId the identifier of the session
   * @param key the key under which to store the value
   * @param value the value to store
   * @param <T> the value type
   */
  public <T> void storeSessionData(
      final String sessionId, final SessionContainer.SessionStorageKey key, final T value) {
    sessionContainer.storeSessionData(sessionId, key, value);
  }

  /**
   * Stores a nonce for a session.
   *
   * @param sessionId the identifier of the session
   * @param nonce the nonce to store
   */
  public void storeNonce(final String sessionId, final byte[] nonce) {
    storeSessionData(sessionId, SessionStorageKey.NONCE, nonce);
  }

  /**
   * Stores the card communication mode for a session.
   *
   * @param sessionId the identifier of the session
   * @param communicationMode the communication mode to store
   */
  public void storeCommunicationMode(
      final String sessionId, final CommunicationMode communicationMode) {
    storeSessionData(sessionId, SessionStorageKey.COMMUNICATION_MODE, communicationMode);
  }

  /**
   * Stores the scenario sequence counter for a session.
   *
   * @param sessionId the identifier of the session
   * @param value the sequence counter to store
   */
  public void storeScenarioCounter(final String sessionId, final int value) {
    sessionContainer.storeSessionData(sessionId, SCENARIO_COUNTER, value);
  }

  /**
   * Stores the generated JWT token for a session.
   *
   * @param sessionId the identifier of the session
   * @param jwtToken the JWT token to store
   */
  public void storeJwtToken(final String sessionId, final String jwtToken) {
    sessionContainer.storeSessionData(
        sessionId, SessionContainer.SessionStorageKey.JWT_TOKEN, jwtToken);
  }

  /**
   * Stores additional CVC-related steps for a contact-card session.
   *
   * @param sessionId the identifier of the session
   * @param cvcList the additional steps to store
   */
  public void storeAdditionalSteps(final String sessionId, final List<StepDefinition> cvcList) {
    sessionContainer.storeSessionData(
        sessionId, SessionContainer.SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST, cvcList);
  }

  /**
   * Retrieves typed data stored under a session storage key.
   *
   * @param sessionId the identifier of the session
   * @param key the key of the data to retrieve
   * @param type the expected data type
   * @param <T> the expected data type
   * @return an optional containing the stored value
   */
  public <T> Optional<T> retrieveSessionData(
      final String sessionId, final SessionContainer.SessionStorageKey key, final Class<T> type) {
    return sessionContainer.retrieveSessionData(sessionId, key, type);
  }

  /**
   * Clears all data stored for a session.
   *
   * @param sessionId the identifier of the session
   */
  public void clearSessionData(final String sessionId) {
    sessionContainer.clearSession(sessionId);
  }

  /**
   * Clears request-scoped data for a session.
   *
   * @param sessionId the identifier of the session
   */
  public void clearRequestState(final String sessionId) {
    sessionContainer.clearRequestState(sessionId);
  }

  /**
   * Clears the connection associated with a transport session.
   *
   * @param transportSessionId the identifier of the transport session
   */
  public void clearConnection(final String transportSessionId) {
    sessionContainer.clearConnection(transportSessionId);
  }

  /**
   * Copies connection scoped data (currently the ZETA user info) from the transport session into a
   * logical session, so that request scoped token generation can read it under the logical id.
   *
   * @param transportSessionId the identifier of the transport session
   * @param logicalSessionId the identifier of the logical session
   */
  public void copyConnectionScopedData(
      final String transportSessionId, final String logicalSessionId) {
    sessionContainer.copySessionData(
        transportSessionId, logicalSessionId, SessionStorageKey.ZETA_USER_INFO);
  }

  /**
   * Stores the current scenario for a session.
   *
   * @param sessionId the identifier of the session
   * @param scenario the scenario to store
   */
  public void storeScenario(final String sessionId, final Scenario scenario) {
    sessionContainer.storeScenario(sessionId, scenario);
  }

  /**
   * Stores the client-provided session identifier.
   *
   * @param sessionId the identifier of the logical session
   * @param clientSessionId the client session identifier to store
   */
  public void storeClientSessionId(final String sessionId, final String clientSessionId) {
    storeSessionData(sessionId, CLIENT_SESSION_ID, clientSessionId);
  }

  /**
   * Stores the card connection type for a session.
   *
   * @param sessionId the identifier of the session
   * @param cardConnectionType the card connection type to store
   */
  public void storeCardConnectionType(
      final String sessionId, final CardConnectionType cardConnectionType) {
    storeSessionData(sessionId, CARD_CONNECTION_TYPE, cardConnectionType);
  }

  /**
   * Stores the sequence counter for a session.
   *
   * @param sessionId the identifier of the session
   * @param value the sequence counter to store
   */
  public void storeSequenceCounter(final String sessionId, final int value) {
    storeSessionData(sessionId, SCENARIO_COUNTER, value);
  }

  /**
   * Stores the end-entity CVC for a session.
   *
   * @param sessionId the identifier of the session
   * @param cvc the encoded end-entity CVC
   */
  public void storeCvc(final String sessionId, final byte[] cvc) {
    storeSessionData(sessionId, SessionStorageKey.CVC, cvc);
  }

  /**
   * Stores the certificate authority CVC for a session.
   *
   * @param sessionId the identifier of the session
   * @param cvc the encoded certificate authority CVC
   */
  public void storeCvcCA(final String sessionId, final byte[] cvc) {
    storeSessionData(sessionId, SessionStorageKey.CVC_CA, cvc);
  }

  /**
   * Returns the end-entity CVC stored for a session.
   *
   * @param sessionId the identifier of the session
   * @return the encoded end-entity CVC
   * @throws ScenarioException if no end-entity CVC is stored
   */
  public byte[] getCvc(final String sessionId) {
    return getSessionDataOrThrow(sessionId, SessionStorageKey.CVC, byte[].class, "No CVC found");
  }

  /**
   * Returns the certificate authority CVC stored for a session.
   *
   * @param sessionId the identifier of the session
   * @return the encoded certificate authority CVC
   * @throws ScenarioException if no certificate authority CVC is stored
   */
  public byte[] getCvcCA(final String sessionId) {
    return getSessionDataOrThrow(
        sessionId, SessionStorageKey.CVC_CA, byte[].class, "No CVC CA found");
  }

  /**
   * Stores AUT certificate data for a session.
   *
   * @param sessionId the identifier of the session
   * @param data the encoded AUT certificate data
   */
  public void storeAut(final String sessionId, final byte[] data) {
    storeSessionData(sessionId, SessionStorageKey.AUT, data);
  }

  /**
   * Returns the AUT certificate data stored for a session.
   *
   * @param sessionId the identifier of the session
   * @return the encoded AUT certificate data
   * @throws ScenarioException if no AUT certificate data is stored
   */
  public byte[] getAut(final String sessionId) {
    return getSessionDataOrThrow(sessionId, SessionStorageKey.AUT, byte[].class, "No AUT found");
  }

  private <T> T getSessionDataOrThrow(
      final String sessionId,
      final SessionContainer.SessionStorageKey key,
      final Class<T> type,
      final String errorMessage) {
    return (T)
        sessionContainer
            .retrieveSessionData(sessionId, key, type)
            .orElseThrow(
                () ->
                    new ScenarioException(
                        sessionId, errorMessage, BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR));
  }
}
