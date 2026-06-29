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

import java.math.BigInteger;
import java.util.HexFormat;
import java.util.Locale;

final class VirtualCardPureHelper {
  private VirtualCardPureHelper() {}

  static String normalize(final String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
  }

  static String toHex(final byte[] value) {
    return HexFormat.of().withUpperCase().formatHex(value);
  }

  static byte[] concatenate(final byte[]... values) {
    int totalLength = 0;
    for (byte[] value : values) {
      totalLength += value.length;
    }

    final byte[] result = new byte[totalLength];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  static byte[] toFixedLength(final BigInteger value, final int length) {
    return toFixedLength(value.toByteArray(), length);
  }

  static byte[] toFixedLength(final byte[] value, final int length) {
    if (value.length == length) {
      return value;
    }
    final byte[] result = new byte[length];
    if (value.length > length) {
      System.arraycopy(value, value.length - length, result, 0, length);
    } else {
      System.arraycopy(value, 0, result, length - value.length, value.length);
    }
    return result;
  }
}
