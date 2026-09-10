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

import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class SessionContainer {

  public enum SessionStorageKey {
    OPEN_CONTACT_ICC_CVC_LIST,
    JWT_TOKEN,
    CARD_CONNECTION_TYPE,
    PATIENT_PROOF_TIME,
    CLIENT_SESSION_ID,
    SCENARIO_COUNTER,
    COMMUNICATION_MODE,
    NONCE,
    CVC,
    CVC_CA,
    AUT,
    ZETA_USER_INFO,
    OCSP_RESPONSE,
    DEFAULT
  }

  private final Map<String, Scenario> scenarioMap = new ConcurrentHashMap<>();

  private final Map<String, Map<SessionStorageKey, Object>> customSessionStorage =
      new ConcurrentHashMap<>();

  public <T> void storeSessionData(
      final String sessionId, final SessionStorageKey key, final T value) {
    customSessionStorage.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(key, value);
  }

  @SuppressWarnings("unchecked")
  public <T> T computeSessionDataIfAbsent(
      final String sessionId, final SessionStorageKey key, final Supplier<T> valueSupplier) {
    return (T)
        customSessionStorage
            .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(key, k -> valueSupplier.get());
  }

  @SuppressWarnings("unchecked")
  public <T> Optional<T> retrieveSessionData(
      final String sessionId, final SessionStorageKey key, final Type type) {
    return Optional.ofNullable(customSessionStorage.get(sessionId))
        .map(data -> data.get(key))
        .filter(value -> isInstanceOfType(value, type))
        .map(value -> (T) value);
  }

  public boolean containsDataInSessionStorage(final String sessionId, final SessionStorageKey key) {
    return Optional.ofNullable(customSessionStorage.get(sessionId))
        .map(data -> data.containsKey(key))
        .orElse(false);
  }

  public void removeDataFromSessionStorage(final String sessionId) {
    if (customSessionStorage.get(sessionId) != null) {
      customSessionStorage.remove(sessionId);
    }
  }

  public void storeScenario(final String sessionId, final Scenario scenario) {
    scenarioMap.put(sessionId, scenario);
  }

  public Optional<Scenario> retrieveScenario(final String sessionId) {
    return Optional.ofNullable(scenarioMap.get(sessionId));
  }

  public void removeScenario(final String sessionId) {
    scenarioMap.remove(sessionId);
  }

  public boolean containsScenario(final String sessionId) {
    return scenarioMap.containsKey(sessionId);
  }

  public void clearSession(final String sessionId) {
    scenarioMap.remove(sessionId);
    customSessionStorage.remove(sessionId);
  }

  /**
   * Removes all state belonging to a transport (WebSocket) connection: the connection scoped entry
   * itself (keyed by the transport session id, e.g. ZETA user info) as well as every logical
   * session entry that was derived from this transport id ({@code transportId::clientSessionId}).
   */
  public void clearConnection(final String transportSessionId) {
    final var prefix = LogicalSessionId.transportPrefix(transportSessionId);
    scenarioMap.remove(transportSessionId);
    customSessionStorage.remove(transportSessionId);
    scenarioMap.keySet().removeIf(key -> key.startsWith(prefix));
    customSessionStorage.keySet().removeIf(key -> key.startsWith(prefix));
  }

  /**
   * Clears all state belonging to a single logical session, which is scoped to a single token
   * request.
   */
  public void clearRequestState(final String logicalSessionId) {
    scenarioMap.remove(logicalSessionId);
    customSessionStorage.remove(logicalSessionId);
  }

  /** Copies a single storage value from one session id to another, if present. */
  public void copySessionData(
      final String fromSessionId, final String toSessionId, final SessionStorageKey key) {
    final var source = customSessionStorage.get(fromSessionId);
    if (source != null && source.containsKey(key)) {
      storeSessionData(toSessionId, key, source.get(key));
    }
  }

  private boolean isInstanceOfType(final Object value, final Type type) {
    if (type instanceof final ParameterizedType parameterizedType) {
      return ((Class<?>) parameterizedType.getRawType()).isInstance(value);
    } else if (type instanceof final Class<?> clazz) {
      return clazz.isInstance(value);
    }
    return false;
  }
}
