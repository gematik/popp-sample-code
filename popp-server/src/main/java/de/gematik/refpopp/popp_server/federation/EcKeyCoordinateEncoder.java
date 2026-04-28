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

import java.math.BigInteger;
import java.util.Arrays;

public final class EcKeyCoordinateEncoder {

  private static final int COORDINATE_LENGTH = 32;

  private EcKeyCoordinateEncoder() {}

  public static byte[] toUnsignedFixedLength(BigInteger value) {
    byte[] bytes = value.toByteArray();

    if (bytes.length == COORDINATE_LENGTH) {
      return bytes;
    }
    if (bytes.length == COORDINATE_LENGTH + 1 && bytes[0] == 0) {
      return Arrays.copyOfRange(bytes, 1, bytes.length);
    }
    if (bytes.length < COORDINATE_LENGTH) {
      byte[] padded = new byte[COORDINATE_LENGTH];
      System.arraycopy(bytes, 0, padded, COORDINATE_LENGTH - bytes.length, bytes.length);
      return padded;
    }

    throw new IllegalArgumentException("Coordinate too large for " + COORDINATE_LENGTH + " bytes");
  }
}
