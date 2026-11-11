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

import de.gematik.openhealth.crypto.CryptoException;
import de.gematik.openhealth.crypto.Openhealth_cryptoKt;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class BrainpoolP256r1CvcSigner implements CvcSigner {
  private static final BigInteger BRAINPOOL_P256R1_ORDER =
      new BigInteger("A9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A7", 16);
  private static final int BRAINPOOL_P256R1_COORDINATE_LENGTH = 32;
  private static final int MAX_ECDSA_SIGNING_ATTEMPTS = 64;

  @Override
  public byte[] sign(final byte[] privateKeyBytes, final byte[] value) {
    final BigInteger privateKey = toPositiveInteger(privateKeyBytes);
    if (!isValidScalar(privateKey)) {
      throw new IllegalStateException("Invalid CVC private key configured for virtual card.");
    }

    final BigInteger z = truncateDigest(value);
    for (int attempt = 0; attempt < MAX_ECDSA_SIGNING_ATTEMPTS; attempt++) {
      try (final var keyPair = Openhealth_cryptoKt.generateBrainpoolP256r1KeyPair()) {
        final var signature =
            tryCreateSignature(privateKey, z, keyPair.privateKey(), keyPair.publicKey());
        if (signature.isPresent()) {
          return signature.get();
        }
      } catch (final CryptoException e) {
        throw new IllegalStateException("Failed to sign CVC authentication challenge", e);
      }
    }
    throw new IllegalStateException("Failed to create a valid CVC ECDSA signature.");
  }

  private Optional<byte[]> tryCreateSignature(
      final BigInteger privateKey,
      final BigInteger z,
      final byte[] ephemeralPrivateKey,
      final byte[] ephemeralPublicKey) {
    final BigInteger k = toPositiveInteger(ephemeralPrivateKey);
    if (!isValidScalar(k)) {
      return Optional.empty();
    }

    final BigInteger r = extractSignatureR(ephemeralPublicKey);
    if (r.signum() == 0) {
      return Optional.empty();
    }

    final BigInteger s =
        k.modInverse(BRAINPOOL_P256R1_ORDER)
            .multiply(z.add(r.multiply(privateKey)))
            .mod(BRAINPOOL_P256R1_ORDER);
    if (s.signum() == 0) {
      return Optional.empty();
    }
    return Optional.of(
        VirtualCardPureHelper.concatenate(
            VirtualCardPureHelper.toFixedLength(r, BRAINPOOL_P256R1_COORDINATE_LENGTH),
            VirtualCardPureHelper.toFixedLength(s, BRAINPOOL_P256R1_COORDINATE_LENGTH)));
  }

  private static boolean isValidScalar(final BigInteger value) {
    return value.signum() != 0 && value.compareTo(BRAINPOOL_P256R1_ORDER) < 0;
  }

  private static BigInteger extractSignatureR(final byte[] publicKey) {
    return toPositiveInteger(
            Arrays.copyOfRange(publicKey, 1, 1 + BRAINPOOL_P256R1_COORDINATE_LENGTH))
        .mod(BRAINPOOL_P256R1_ORDER);
  }

  private static BigInteger truncateDigest(final byte[] value) {
    var digest = toPositiveInteger(value);
    final int excessBits = digest.bitLength() - BRAINPOOL_P256R1_ORDER.bitLength();
    if (excessBits > 0) {
      digest = digest.shiftRight(excessBits);
    }
    return digest;
  }

  private static BigInteger toPositiveInteger(final byte[] value) {
    return new BigInteger(1, value);
  }
}
