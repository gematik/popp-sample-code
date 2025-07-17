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
  void statusWordAndDataToStringReturnsCorrectFormat() {
    // given
    final StatusWordAndData statusWordAndData =
        new StatusWordAndData("9000", new byte[] {0x01, 0x02});

    // when
    final String result = statusWordAndData.toString();

    // then
    assertThat(result).isEqualTo("StatusWordAndData{statusWord='9000', data=[1, 2]}");
  }

  @Test
  void statusWordAndDataEqualsReturnsTrueForSameObject() {
    // given
    final StatusWordAndData statusWordAndData =
        new StatusWordAndData("9000", new byte[] {0x01, 0x02});

    // when
    final boolean result = statusWordAndData.equals(statusWordAndData);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void statusWordAndDataEqualsReturnsTrueForEqualObjects() {
    // given
    final StatusWordAndData statusWordAndData1 =
        new StatusWordAndData("9000", new byte[] {0x01, 0x02});
    final StatusWordAndData statusWordAndData2 =
        new StatusWordAndData("9000", new byte[] {0x01, 0x02});

    // when
    final boolean result = statusWordAndData1.equals(statusWordAndData2);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void statusWordAndDataEqualsReturnsFalseForDifferentStatusWords() {
    // given
    final StatusWordAndData statusWordAndData1 =
        new StatusWordAndData("9000", new byte[] {0x01, 0x02});
    final StatusWordAndData statusWordAndData2 =
        new StatusWordAndData("9001", new byte[] {0x01, 0x02});

    // when
    final boolean result = statusWordAndData1.equals(statusWordAndData2);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void statusWordAndDataEqualsReturnsFalseForDifferentData() {
    // given
    final StatusWordAndData statusWordAndData1 =
        new StatusWordAndData("9000", new byte[] {0x01, 0x02});
    final StatusWordAndData statusWordAndData2 =
        new StatusWordAndData("9000", new byte[] {0x03, 0x04});

    // when
    final boolean result = statusWordAndData1.equals(statusWordAndData2);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void statusWordAndDataEqualsReturnsFalseForDifferentInstance() {
    // given
    final StatusWordAndData statusWordAndData =
        new StatusWordAndData("9000", new byte[] {0x01, 0x02});

    // when
    final boolean result = statusWordAndData.equals("9000");

    // then
    assertThat(result).isFalse();
  }

  @Test
  void statusWordAndDataHashCodeReturnsSameHashCodeForEqualObjects() {
    // given
    final StatusWordAndData statusWordAndData1 =
        new StatusWordAndData("9000", new byte[] {0x01, 0x02});
    final StatusWordAndData statusWordAndData2 =
        new StatusWordAndData("9000", new byte[] {0x01, 0x02});

    // when
    final int hashCode1 = statusWordAndData1.hashCode();
    final int hashCode2 = statusWordAndData2.hashCode();

    // then
    assertThat(hashCode1).isEqualTo(hashCode2);
  }

  @Test
  void statusWordAndDataHashCodeReturnsDifferentHashCodeForDifferentObjects() {
    // given
    final StatusWordAndData statusWordAndData1 =
        new StatusWordAndData("9000", new byte[] {0x01, 0x02});
    final StatusWordAndData statusWordAndData2 =
        new StatusWordAndData("9001", new byte[] {0x03, 0x04});

    // when
    final int hashCode1 = statusWordAndData1.hashCode();
    final int hashCode2 = statusWordAndData2.hashCode();

    // then
    assertThat(hashCode1).isNotEqualTo(hashCode2);
  }
}
