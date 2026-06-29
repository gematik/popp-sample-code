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

import de.gematik.openhealth.asn1.Asn1FfiException;
import de.gematik.openhealth.asn1.Asn1TagClass;
import de.gematik.openhealth.asn1.Asn1TagForm;
import de.gematik.openhealth.asn1.Openhealth_asn1Kt;
import de.gematik.openhealth.crypto.CryptoException;
import de.gematik.openhealth.crypto.Openhealth_cryptoKt;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

final class SecureMessagingSession {
  private static final byte[] EMPTY_BYTES = new byte[0];
  private static final byte[] SECURE_MESSAGING_SUFFIX_MAC = HexFormat.of().parseHex("00000002");

  private final byte[] kmac;
  private final byte[] sscMacRsp = new byte[16];

  private SecureMessagingSession(byte[] kmac) {
    this.kmac = kmac;
  }

  static SecureMessagingSession fromSharedSecret(final byte[] sharedSecret)
      throws NoSuchAlgorithmException {
    return new SecureMessagingSession(deriveSecureMessagingMacKey(sharedSecret));
  }

  String protectResponse(String responseDataHex, String statusWordHex) {
    try {
      final byte[] responseData =
          responseDataHex == null || responseDataHex.isEmpty()
              ? EMPTY_BYTES
              : HexFormat.of().parseHex(responseDataHex);
      final byte[] statusWord = HexFormat.of().parseHex(statusWordHex);

      incrementCounter(sscMacRsp);
      incrementCounter(sscMacRsp);

      final byte[] responseDataDo =
          responseData.length == 0
              ? EMPTY_BYTES
              : Openhealth_asn1Kt.writeTaggedObject(
                  Asn1TagClass.CONTEXT_SPECIFIC, Asn1TagForm.PRIMITIVE, 0x01, responseData);
      final byte[] statusWordDo =
          Openhealth_asn1Kt.writeTaggedObject(
              Asn1TagClass.CONTEXT_SPECIFIC, Asn1TagForm.PRIMITIVE, 0x19, statusWord);
      final byte[] macInput = concatenate(responseDataDo, statusWordDo);
      final byte[] mac =
          Openhealth_cryptoKt.aesCmac(kmac, concatenate(sscMacRsp, padIso(macInput)), 8);
      final byte[] macDo =
          Openhealth_asn1Kt.writeTaggedObject(
              Asn1TagClass.CONTEXT_SPECIFIC, Asn1TagForm.PRIMITIVE, 0x0E, mac);

      return HexFormat.of().formatHex(concatenate(responseDataDo, statusWordDo, macDo))
          + statusWordHex;
    } catch (Asn1FfiException | CryptoException e) {
      throw new IllegalStateException("Failed to protect response", e);
    }
  }

  @SuppressWarnings("java:S4790")
  private static byte[] deriveSecureMessagingMacKey(final byte[] sharedSecret)
      throws NoSuchAlgorithmException {
    // This KDF must stay byte-compatible with the existing virtual-card secure-messaging flow.
    // The previous implementation used EafiHashAlgorithm.SHA_1 over sharedSecret || 00000002 and
    // truncated the result to 16 bytes for the AES-CMAC key.
    return Arrays.copyOf(
        MessageDigest.getInstance("SHA-1")
            .digest(concatenate(sharedSecret, SECURE_MESSAGING_SUFFIX_MAC)),
        16);
  }

  private static void incrementCounter(byte[] counter) {
    for (int i = counter.length - 1; i >= 0; i--) {
      counter[i]++;
      if (counter[i] != 0) {
        return;
      }
    }
  }

  private static byte[] padIso(byte[] value) {
    final int paddedLength = ((value.length + 1 + 15) / 16) * 16;
    final byte[] padded = Arrays.copyOf(value, paddedLength);
    padded[value.length] = (byte) 0x80;
    return padded;
  }

  private static byte[] concatenate(byte[]... values) {
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
}
