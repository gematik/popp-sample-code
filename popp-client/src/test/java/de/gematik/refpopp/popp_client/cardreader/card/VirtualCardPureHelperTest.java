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
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class VirtualCardPureHelperTest {

  @Test
  void normalizeReturnsEmptyForNullAndBlank() {
    assertAll(
        () -> assertThat(VirtualCardPureHelper.normalize(null)).isEmpty(),
        () -> assertThat(VirtualCardPureHelper.normalize("")).isEmpty(),
        () -> assertThat(VirtualCardPureHelper.normalize("  \n\t  ")).isEmpty());
  }

  @Test
  void toFixedLengthPadsOrTrims() {
    byte[] padded = VirtualCardPureHelper.toFixedLength(new byte[] {0x01, 0x02}, 4);
    byte[] trimmed = VirtualCardPureHelper.toFixedLength(new byte[] {0x01, 0x02, 0x03}, 2);

    assertAll(
        () -> assertThat(padded).containsExactly(0x00, 0x00, 0x01, 0x02),
        () -> assertThat(trimmed).containsExactly(0x02, 0x03));
  }

  @Test
  void normalizeRemovesAllWhitespaceAndUpperCases() {
    String normalized = VirtualCardPureHelper.normalize("00 b0\n0a\t00");
    assertThat(normalized).isEqualTo("00B00A00");
  }

  @Test
  void toHexReturnsUppercaseHexString() {
    assertThat(VirtualCardPureHelper.toHex(new byte[] {0x00, 0x0A, (byte) 0xFF}))
        .isEqualTo("000AFF");
  }

  @Test
  void concatenateJoinsArraysInOrder() {
    byte[] combined =
        VirtualCardPureHelper.concatenate(new byte[] {0x01}, new byte[0], new byte[] {0x02, 0x03});
    assertThat(combined).containsExactly(0x01, 0x02, 0x03);
  }

  @Test
  void toFixedLengthReturnsSameArrayIfLengthAlreadyMatches() {
    byte[] input = new byte[] {0x01, 0x02};
    byte[] result = VirtualCardPureHelper.toFixedLength(input, 2);
    assertThat(result).isSameAs(input);
  }

  @Test
  void toFixedLengthFromBigIntegerHandlesLeadingSignByte() {
    byte[] result = VirtualCardPureHelper.toFixedLength(new BigInteger("80", 16), 1);
    assertThat(result).containsExactly((byte) 0x80);
  }
}
