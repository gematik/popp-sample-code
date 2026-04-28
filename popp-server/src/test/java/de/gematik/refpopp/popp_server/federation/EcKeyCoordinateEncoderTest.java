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

package de.gematik.refpopp.popp_server.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EcKeyCoordinateEncoderTest {

  @Test
  void toUnsignedFixedLengthReturnsInputWhenAlready32BytesLong() {
    var input = new byte[32];
    input[0] = 1;
    input[31] = 42;
    var value = new BigInteger(1, input);

    var result = EcKeyCoordinateEncoder.toUnsignedFixedLength(value);

    assertThat(result).hasSize(32).isEqualTo(input);
  }

  @Test
  void toUnsignedFixedLengthRemovesLeadingSignByteFor33BytePositiveValue() {
    var coordinate = new byte[32];
    coordinate[0] = (byte) 0x80;
    Arrays.fill(coordinate, 1, coordinate.length, (byte) 7);
    var value = new BigInteger(1, coordinate);

    var result = EcKeyCoordinateEncoder.toUnsignedFixedLength(value);

    assertThat(value.toByteArray()).hasSize(33);
    assertThat(result).hasSize(32).isEqualTo(coordinate);
  }

  @Test
  void toUnsignedFixedLengthLeftPadsShortValueWithZeros() {
    var value = BigInteger.valueOf(0x1234);

    var result = EcKeyCoordinateEncoder.toUnsignedFixedLength(value);

    assertThat(result).hasSize(32);
    assertThat(result[30]).isEqualTo((byte) 0x12);
    assertThat(result[31]).isEqualTo((byte) 0x34);
    assertThat(new BigInteger(1, result)).isEqualTo(value);
  }

  @Test
  void toUnsignedFixedLengthReturnsAllZerosForZeroValue() {
    var result = EcKeyCoordinateEncoder.toUnsignedFixedLength(BigInteger.ZERO);

    assertThat(result).hasSize(32).containsOnly((byte) 0);
  }

  @Test
  void toUnsignedFixedLengthThrowsWhenCoordinateExceeds32Bytes() {
    var tooLarge = new byte[33];
    tooLarge[0] = 1;
    var value = new BigInteger(1, tooLarge);

    assertThat(value.toByteArray()).hasSize(33);

    assertThatThrownBy(() -> EcKeyCoordinateEncoder.toUnsignedFixedLength(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Coordinate too large for 32 bytes");
  }
}
