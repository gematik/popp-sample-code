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

import org.junit.jupiter.api.Test;

class VirtualCardImageDataTest {

  @Test
  void constructorCopiesPrivateKeyDefensively() {
    final byte[] key = new byte[] {0x01, 0x02, 0x03};

    final VirtualCardImageData data = new VirtualCardImageData("cv", "auth", "sub", "version", key);
    key[0] = 0x7F;

    assertThat(data.egkAuthCvcPrivateKey()).containsExactly(0x01, 0x02, 0x03);
  }

  @Test
  void getterReturnsCopyOfPrivateKey() {
    final VirtualCardImageData data =
        new VirtualCardImageData("cv", "auth", "sub", "version", new byte[] {0x01, 0x02, 0x03});

    final byte[] fromGetter = data.egkAuthCvcPrivateKey();
    fromGetter[1] = 0x55;

    assertThat(data.egkAuthCvcPrivateKey()).containsExactly(0x01, 0x02, 0x03);
  }

  @Test
  void nullPrivateKeyIsNormalizedToEmptyArray() {
    final VirtualCardImageData data =
        new VirtualCardImageData("cv", "auth", "sub", "version", null);

    assertThat(data.egkAuthCvcPrivateKey()).isEmpty();
  }
}
