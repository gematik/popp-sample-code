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

import de.gematik.smartcards.sdcom.apdu.CommandApdu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

class CommandApduFactoryTest {

  private CommandApduFactory sut;

  @BeforeEach
  void setUp() {
    sut = new CommandApduFactory();
  }

  @Test
  void createCommandApduReturnsNewCommandApdu() {
    try (final MockedConstruction<CommandApdu> mocked =
        Mockito.mockConstruction(CommandApdu.class)) {
      // given
      final int cla = 0x00;
      final int ins = 0xA4;
      final int p1 = 0x04;
      final int p2 = 0x00;
      final byte[] data = new byte[] {0x01, 0x02, 0x03};

      // when
      final var commandApdu = sut.createCommandApdu(cla, ins, p1, p2, data);

      // then
      assertThat(commandApdu).isNotNull();
      assertThat(mocked.constructed()).hasSize(1);
    }
  }
}
