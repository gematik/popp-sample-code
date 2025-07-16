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

package de.gematik.refpopp.popp_server.scenario.common.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.scenario.contactbased.ContactBasedScenariosProvider;
import de.gematik.refpopp.popp_server.scenario.openegk.OpenEgkScenariosProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioProviderStrategyServiceTest {

  private ScenarioProviderStrategyService sut;

  @BeforeEach
  void setUp() {
    final var contactBasedScenariosProviderMock = mock(ContactBasedScenariosProvider.class);
    when(contactBasedScenariosProviderMock.getSupportedCommunicationMode())
        .thenReturn(CommunicationMode.CONTACT);
    final var openEgkScenariosProviderMock = mock(OpenEgkScenariosProvider.class);
    when(openEgkScenariosProviderMock.getSupportedCommunicationMode())
        .thenReturn(CommunicationMode.UNDEFINED);
    sut =
        new ScenarioProviderStrategyService(
            List.of(contactBasedScenariosProviderMock, openEgkScenariosProviderMock));
  }

  @Test
  void getProviderReturnsOpenEgkProvider() {
    // given
    final var cardVersion = CommunicationMode.UNDEFINED;

    // when
    final var provider = sut.getProvider(cardVersion);

    // then
    assertThat(provider).isNotNull().isInstanceOf(OpenEgkScenariosProvider.class);
  }

  @Test
  void getProviderReturnsContactBasedProvider() {
    // given
    final var cardVersion = CommunicationMode.CONTACT;

    // when
    final var provider = sut.getProvider(cardVersion);

    // then
    assertThat(provider).isNotNull().isInstanceOf(ContactBasedScenariosProvider.class);
  }

  @Test
  void getProviderThrowsExceptionForUnsupportedVersion() {
    // given
    final var cardVersionMock = mock(CommunicationMode.class);

    // when
    assertThrows(IllegalArgumentException.class, () -> sut.getProvider(cardVersionMock));
  }
}
