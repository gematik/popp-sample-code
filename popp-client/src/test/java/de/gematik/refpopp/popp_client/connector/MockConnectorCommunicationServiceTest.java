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

package de.gematik.refpopp.popp_client.connector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MockConnectorCommunicationServiceTest {

  private MockConnectorCommunicationService sut;

  @BeforeEach
  void setUp() {
    sut = new MockConnectorCommunicationService();
  }

  @Test
  void getConnectedEgkCard() {
    // when
    final var cardHandle = sut.getConnectedEgkCard();

    // then
    assertThat(cardHandle).isEqualTo("58366430-60ad-417b-ac8c-2a99467c6cf6");
  }

  @Test
  void startCardSession() {
    // when
    final var cardHandle = sut.startCardSession("58366430-60ad-417b-ac8c-2a99467c6cf6");

    // then
    assertThat(cardHandle).isEqualTo("e4eaaaf2-d142-11e1-b3e4-080027620cdd");
  }

  @Test
  void stopCardSession() {
    // when
    final var status = sut.stopCardSession("uuidSessionId");

    // then
    assertThat(status.getResult()).isEqualTo("OK");
  }

  @Test
  void secureSendApduReturnsResponse() {
    // when / then
    var response = sut.secureSendApdu("signedScenario");
    assertThat(response).isNotEmpty().hasSize(2);

    response = sut.secureSendApdu("signedScenario");
    assertThat(response).isNotEmpty().hasSize(3);

    response = sut.secureSendApdu("signedScenario");
    assertThat(response).isNotEmpty().hasSize(2);

    response = sut.secureSendApdu("signedScenario");
    assertThat(response).isNotEmpty().hasSize(3);
  }

  @Test
  void secureSendApduReturnsEmptyList() {
    // given
    ReflectionTestUtils.setField(sut, "counter", new AtomicInteger(5));

    // when
    final var response = sut.secureSendApdu("signedScenario");

    // then
    assertThat(response).isEmpty();
  }
}
