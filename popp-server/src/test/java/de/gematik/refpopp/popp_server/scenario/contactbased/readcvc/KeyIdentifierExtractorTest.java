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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeyIdentifierExtractorTest {

  private static final HexFormat HEX_FORMAT = HexFormat.of();
  private KeyIdentifierExtractor sut;

  @BeforeEach
  void setUp() {
    sut = new KeyIdentifierExtractor();
  }

  @Test
  void extractValidDataShouldReturnKeyReferences() {
    // given
    final byte[] data =
        HEX_FORMAT.parseHex(
            "e0154f07d2760001448000b60a83084445475858870222"
                + "e0154f07d2760001448000b60a83084445475858120223"
                + "e0194f07d2760001448000a40e830c000a80276001011699902101"
                + "e0194f07d2760001448000a40e830c4d6f7270686f414343455353"
                + "e0164f07d2760001448000b60b83094d6f7270686f564552"
                + "e0154f07d2760001448000b60a83084445475858860220"
                + "e0154f07d2760001448000b60a83080000000000000013");

    // When
    final var result = sut.extract(data);

    // Then
    assertThat(result)
        .containsExactlyInAnyOrder(
            "4445475858870222",
            "4445475858120223",
            "000a80276001011699902101",
            "4d6f7270686f414343455353",
            "4d6f7270686f564552",
            "4445475858860220",
            "0000000000000013");
  }

  @Test
  void extractThrowsIllegalArgumentExceptionForInvalidData() {
    assertThatThrownBy(() -> sut.extract(new byte[] {0x01, 0x02, 0x03}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid LIST PUBLIC KEY response");
  }
}
