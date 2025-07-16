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

import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionContainerTest {

  private SessionContainer sessionContainer;

  @BeforeEach
  void setUp() {
    sessionContainer = new SessionContainer();
  }

  @Test
  void storeInSessionStoresCorrectly() {
    // given
    final var key = SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST;
    final var value = List.of("value1", "value2");
    final var sessionId = "session1";

    // when
    sessionContainer.storeSessionData(sessionId, key, value);

    // then
    assertThat(sessionContainer.retrieveSessionData(sessionId, key, List.class)).contains(value);
  }

  @Test
  void retrieveSessionStorageReturnsEmptyForNonExistentSession() {
    assertThat(
            sessionContainer.retrieveSessionData(
                "nonExistentSession", SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST, List.class))
        .isEmpty();
  }

  @Test
  void retrieveSessionDataReturnsEmptyForNonExistentKey() {
    // given
    final var key = SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST;
    final var value = List.of("value1", "value2");
    final var sessionId = "session1";
    sessionContainer.storeSessionData(sessionId, key, value);

    // when
    final var retrievedValue =
        sessionContainer.retrieveSessionData(sessionId, SessionStorageKey.DEFAULT, String.class);

    // then
    assertThat(retrievedValue).isEmpty();
  }

  @Test
  void retrieveInSessionReturnsCorrectValue() {
    // given
    final var key = SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST;
    final var value = List.of("value1", "value2");
    final var sessionId = "session1";
    sessionContainer.storeSessionData(sessionId, key, value);

    // when
    final var retrievedValue =
        sessionContainer.retrieveSessionData(
            sessionId, SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST, List.class);

    // then
    assertThat(retrievedValue).contains(value);
  }

  @Test
  void removeDataFromSessionStorageRemovesDataCorrectly() {
    // given
    final var key = SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST;
    final var value = List.of("value1", "value2");
    final var sessionId = "session1";
    sessionContainer.storeSessionData(sessionId, key, value);

    // when
    sessionContainer.removeDataFromSessionStorage(sessionId);

    // then
    assertThat(sessionContainer.retrieveSessionData(sessionId, key, String.class)).isEmpty();
  }

  @Test
  void removeDataFromSessionStorageDoNothingForNonExistentSession() {
    // given
    final var key = SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST;
    final var value = List.of("value1", "value2");
    final var sessionId = "session1";
    sessionContainer.storeSessionData(sessionId, key, value);

    // when
    sessionContainer.removeDataFromSessionStorage("nonExistentSession");

    // then
    assertThat(sessionContainer.retrieveSessionData(sessionId, key, List.class)).contains(value);
  }

  @Test
  void storeScenarioStoresScenarioCorrectly() {
    // given
    final var scenario = new Scenario("scenario1", List.of());

    // when
    sessionContainer.storeScenario("scenario1", scenario);

    // then
    assertThat(sessionContainer.retrieveScenario("scenario1")).contains(scenario);
  }

  @Test
  void retrieveScenarioReturnsEmptyForNonExistentSession() {
    assertThat(sessionContainer.retrieveScenario("nonExistentSession")).isEmpty();
  }

  @Test
  void retrieveScenarioReturnsCorrectScenario() {
    // given
    final var scenario = new Scenario("scenario1", List.of());
    sessionContainer.storeScenario("session1", scenario);

    // when
    final var retrievedScenario = sessionContainer.retrieveScenario("session1");

    // then
    assertThat(retrievedScenario).isPresent().get().isEqualTo(scenario);
  }

  @Test
  void removeScenarioRemovesScenarioCorrectly() {
    // given
    sessionContainer.storeScenario("session1", new Scenario("scenario1", List.of()));

    // when
    sessionContainer.removeScenario("session1");

    // then
    assertThat(sessionContainer.containsScenario("session1")).isFalse();
  }

  @Test
  void containsScenarioReturnsFalseForNonExistentSession() {
    assertThat(sessionContainer.containsScenario("nonExistentSession")).isFalse();
  }

  @Test
  void containsDataInSessionStorageReturnsFalseForNonExistentSession() {
    assertThat(
            sessionContainer.containsDataInSessionStorage(
                "nonExistentSession", SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST))
        .isFalse();
  }

  @Test
  void clearSessionRemovesAllSessions() {
    // given
    final var sessionId = "session1";
    sessionContainer.storeScenario(sessionId, new Scenario("scenario1", List.of()));
    sessionContainer.storeSessionData(
        sessionId, SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST, List.of("value1", "value2"));

    // when
    sessionContainer.clearSession(sessionId);
    sessionContainer.containsDataInSessionStorage(
        sessionId, SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST);

    // then
    assertThat(sessionContainer.containsScenario("session1")).isFalse();
  }

  @Test
  void containsDataInSessionStorage() {
    // given
    final var sessionId = "sessionId";
    sessionContainer.storeSessionData(
        sessionId, SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST, List.of("value1", "value2"));

    // when
    sessionContainer.containsDataInSessionStorage(
        sessionId, SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST);

    // then
    assertThat(
            sessionContainer.containsDataInSessionStorage(
                sessionId, SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST))
        .isTrue();
  }

  @Test
  void containsDataInSessionStorageReturnsFalseForNonExistentKey() {
    // given
    final var sessionId = "sessionId";
    sessionContainer.storeSessionData(
        sessionId, SessionStorageKey.OPEN_CONTACT_ICC_CVC_LIST, List.of("value1", "value2"));

    // when
    sessionContainer.containsDataInSessionStorage(sessionId, SessionStorageKey.DEFAULT);

    // then
    assertThat(sessionContainer.containsDataInSessionStorage(sessionId, SessionStorageKey.DEFAULT))
        .isFalse();
  }
}
