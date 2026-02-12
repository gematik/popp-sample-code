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

class StatusWordAndDataTest {

  @Test
  void equalsHashCodeAndToString() {
    final var data1 = new byte[] {0x01, 0x02};
    final var data2 = new byte[] {0x01, 0x02};
    final var data3 = new byte[] {0x02, 0x03};

    final var a = new StatusWordAndData("9000", data1);
    final var b = new StatusWordAndData("9000", data2);
    final var c = new StatusWordAndData("6a82", data2);
    final var d = new StatusWordAndData("9000", data3);

    assertThat(a).isEqualTo(a);
    assertThat(a).isEqualTo(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a).isNotEqualTo(d);
    assertThat(a).isNotEqualTo(new Object());

    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).contains("statusWord='9000'");
    assertThat(a.toString()).contains("[1, 2]");
  }
}
