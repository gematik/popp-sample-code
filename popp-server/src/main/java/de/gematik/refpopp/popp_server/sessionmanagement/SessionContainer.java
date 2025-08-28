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
import org.springframework.lang.NonNull;
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
    DEFAULT
  }

  private final Map<String, Scenario> scenarioMap = new ConcurrentHashMap<>();

  private final Map<String, Map<SessionStorageKey, Object>> customSessionStorage =
      new ConcurrentHashMap<>();

  public <T> void storeSessionData(
      @NonNull final String sessionId,
      @NonNull final SessionStorageKey key,
      @NonNull final T value) {
    customSessionStorage.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(key, value);
  }

  @SuppressWarnings("unchecked")
  public <T> Optional<T> retrieveSessionData(
      @NonNull final String sessionId,
      @NonNull final SessionStorageKey key,
      @NonNull final Type type) {
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

  public void storeScenario(@NonNull final String sessionId, @NonNull final Scenario scenario) {
    scenarioMap.put(sessionId, scenario);
  }

  public Optional<Scenario> retrieveScenario(@NonNull final String sessionId) {
    return Optional.ofNullable(scenarioMap.get(sessionId));
  }

  public void removeScenario(@NonNull final String sessionId) {
    scenarioMap.remove(sessionId);
  }

  public boolean containsScenario(final String sessionId) {
    return scenarioMap.containsKey(sessionId);
  }

  public void clearSession(@NonNull final String sessionId) {
    scenarioMap.remove(sessionId);
    customSessionStorage.remove(sessionId);
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
