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

package de.gematik.refpopp.popp_server.scenario.contactbased.readcvc;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.refpopp.popp_server.certificates.CvcFactory;
import de.gematik.smartcards.g2icc.cvc.Cvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

class CvcFactoryTest {

  private CvcFactory sut;

  @BeforeEach
  void setUp() {
    sut = new CvcFactory();
  }

  @Test
  void createReturnsNewCvc() {
    try (final MockedConstruction<Cvc> mocked = Mockito.mockConstruction(Cvc.class)) {
      // given
      final byte[] cvcData = new byte[] {0x01, 0x02, 0x03};

      // when
      final var cvc = sut.create(cvcData);

      // then
      assertThat(cvc).isNotNull();
      final var constructedCvc = mocked.constructed().getFirst();
      assertThat(constructedCvc).isNotNull();
      assertThat(mocked.constructed()).hasSize(1);
    }
  }
}
