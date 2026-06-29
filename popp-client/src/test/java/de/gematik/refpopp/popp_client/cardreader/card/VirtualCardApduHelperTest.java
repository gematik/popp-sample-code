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

package de.gematik.refpopp.popp_client.cardreader.card;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.openhealth.healthcard.HealthCardCommand;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class VirtualCardApduHelperTest {

  private static final byte[] EGK_AID = HexFormat.of().parseHex("D2760001448000");

  @Test
  void buildApduHexForSelectAidReturnsExpectedApdu() {
    final String apdu =
        VirtualCardApduHelper.buildApduHex(
            () -> HealthCardCommand.Companion.selectAid(EGK_AID), false);

    assertThat(apdu).isEqualTo("00A4040C07D2760001448000");
  }

  @Test
  void buildApduPrefixMatchesFirstEightCharactersOfHexApdu() {
    final String apduHex =
        VirtualCardApduHelper.buildApduHex(
            () -> HealthCardCommand.Companion.selectAid(EGK_AID), false);
    final String apduPrefix =
        VirtualCardApduHelper.buildApduPrefix(
            () -> HealthCardCommand.Companion.selectAid(EGK_AID), false);

    assertThat(apduPrefix).hasSize(8).isEqualTo(apduHex.substring(0, 8));
  }

  @Test
  void buildApduHexReturnsUpperCaseHex() {
    final String apdu =
        VirtualCardApduHelper.buildApduHex(
            () -> HealthCardCommand.Companion.selectAid(EGK_AID), false);

    assertThat(apdu).isEqualTo(apdu.toUpperCase());
  }
}
