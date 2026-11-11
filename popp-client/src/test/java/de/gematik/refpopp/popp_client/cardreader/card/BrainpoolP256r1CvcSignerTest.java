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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.openhealth.crypto.BrainpoolP256r1KeyPair;
import de.gematik.openhealth.crypto.CryptoException;
import de.gematik.openhealth.crypto.Openhealth_cryptoKt;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.bouncycastle.asn1.teletrust.TeleTrusTNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BrainpoolP256r1CvcSignerTest {
  private static final BigInteger CURVE_ORDER =
      new BigInteger("A9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A7", 16);
  private static final byte[] PRIVATE_KEY_ONE = {0x01};
  private final CvcSigner signer = new BrainpoolP256r1CvcSigner();

  @ParameterizedTest
  @MethodSource("authenticationValues")
  void signsValuesWithoutHashingAndWithoutChangingInputs(final byte[] value) {
    final BigInteger privateScalar = CURVE_ORDER.subtract(BigInteger.ONE);
    final byte[] privateKey = privateScalar.toByteArray();
    final byte[] originalPrivateKey = privateKey.clone();
    final byte[] originalValue = value.clone();

    final byte[] signature = signer.sign(privateKey, value);

    assertThat(signature).hasSize(64);
    final var curve = TeleTrusTNamedCurves.getByName("brainpoolP256r1");
    final var domain =
        new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
    final var verifier = new ECDSASigner();
    verifier.init(false, new ECPublicKeyParameters(curve.getG().multiply(privateScalar), domain));
    assertThat(
            verifier.verifySignature(
                value,
                new BigInteger(1, Arrays.copyOfRange(signature, 0, 32)),
                new BigInteger(1, Arrays.copyOfRange(signature, 32, 64))))
        .isTrue();
    assertThat(privateKey).containsExactly(originalPrivateKey);
    assertThat(value).containsExactly(originalValue);
  }

  private static Stream<byte[]> authenticationValues() {
    return Stream.of(
        new byte[0],
        new byte[25],
        HexFormat.of().parseHex("000102030405060708090A0B0C0D0E0F101112131415161700"),
        HexFormat.of().parseHex("FF".repeat(32)),
        HexFormat.of().parseHex("FF".repeat(64)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "00",
        "0000000000000000000000000000000000000000000000000000000000000000",
        "A9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A7",
        "A9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A8",
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
      })
  void rejectsInvalidPrivateKeysBeforeCallingCryptoLibrary(final String privateKeyHex) {
    try (final var crypto = mockStatic(Openhealth_cryptoKt.class)) {
      final byte[] privateKey = HexFormat.of().parseHex(privateKeyHex);

      assertThatThrownBy(() -> signer.sign(privateKey, new byte[25]))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Invalid CVC private key configured for virtual card.");
      crypto.verifyNoInteractions();
    }
  }

  @ParameterizedTest
  @MethodSource("digestTruncationCases")
  void preservesDigestTruncationBasedOnIntegerBitLength(
      final byte[] value, final BigInteger expectedDigest) throws Exception {
    final var keyPair = keyPair(PRIVATE_KEY_ONE, BigInteger.ONE);
    try (final var crypto = mockStatic(Openhealth_cryptoKt.class)) {
      crypto.when(Openhealth_cryptoKt::generateBrainpoolP256r1KeyPair).thenReturn(keyPair);

      final byte[] signature = signer.sign(PRIVATE_KEY_ONE, value);

      final BigInteger expectedS = expectedDigest.add(BigInteger.ONE).mod(CURVE_ORDER);
      assertThat(HexFormat.of().withUpperCase().formatHex(signature))
          .isEqualTo(String.format("%064X%064X", BigInteger.ONE, expectedS));
      verify(keyPair).close();
    }
  }

  private static Stream<Arguments> digestTruncationCases() {
    return Stream.of(
        Arguments.of(new byte[0], BigInteger.ZERO),
        Arguments.of(new byte[] {0, 1}, BigInteger.ONE),
        Arguments.of(
            HexFormat.of().parseHex("80" + "00".repeat(31)), BigInteger.ONE.shiftLeft(255)),
        Arguments.of(
            HexFormat.of().parseHex("FF".repeat(64)),
            BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)),
        Arguments.of(
            HexFormat.of().parseHex("0001" + "00".repeat(32)), BigInteger.ONE.shiftLeft(255)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "01",
        "8000000000000000000000000000000000000000000000000000000000000000",
        "A9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A8"
      })
  void formatsUnsignedFixedLengthCoordinatesAndReducesRModuloCurveOrder(final String xCoordinateHex)
      throws Exception {
    final BigInteger x = new BigInteger(xCoordinateHex, 16);
    final var keyPair = keyPair(PRIVATE_KEY_ONE, x);
    try (final var crypto = mockStatic(Openhealth_cryptoKt.class)) {
      crypto.when(Openhealth_cryptoKt::generateBrainpoolP256r1KeyPair).thenReturn(keyPair);

      final byte[] signature = signer.sign(PRIVATE_KEY_ONE, new byte[] {0x01});

      final BigInteger r = x.mod(CURVE_ORDER);
      assertThat(signature).hasSize(64);
      assertThat(HexFormat.of().withUpperCase().formatHex(signature))
          .isEqualTo(String.format("%064X%064X", r, r.add(BigInteger.ONE)));
      verify(keyPair).close();
    }
  }

  @ParameterizedTest
  @MethodSource("invalidEphemeralValues")
  void retriesInvalidEphemeralScalarsAndZeroSignatureCoordinates(
      final byte[] ephemeralPrivateKey, final BigInteger x, final byte[] value) throws Exception {
    final var invalidKeyPair = keyPair(ephemeralPrivateKey, x);
    final var validKeyPair = keyPair(PRIVATE_KEY_ONE, BigInteger.TWO);
    try (final var crypto = mockStatic(Openhealth_cryptoKt.class)) {
      crypto
          .when(Openhealth_cryptoKt::generateBrainpoolP256r1KeyPair)
          .thenReturn(invalidKeyPair, validKeyPair);

      final byte[] signature = signer.sign(PRIVATE_KEY_ONE, value);

      final BigInteger s = new BigInteger(1, value).add(BigInteger.TWO).mod(CURVE_ORDER);
      assertThat(HexFormat.of().withUpperCase().formatHex(signature))
          .isEqualTo(String.format("%064X%064X", BigInteger.TWO, s));
      crypto.verify(Openhealth_cryptoKt::generateBrainpoolP256r1KeyPair, times(2));
      verify(invalidKeyPair).close();
      verify(validKeyPair).close();
    }
  }

  private static Stream<Arguments> invalidEphemeralValues() {
    return Stream.of(
        Arguments.of(new byte[0], BigInteger.ONE, PRIVATE_KEY_ONE),
        Arguments.of(new byte[] {0}, BigInteger.ONE, PRIVATE_KEY_ONE),
        Arguments.of(CURVE_ORDER.toByteArray(), BigInteger.ONE, PRIVATE_KEY_ONE),
        Arguments.of(
            CURVE_ORDER.add(BigInteger.ONE).toByteArray(), BigInteger.ONE, PRIVATE_KEY_ONE),
        Arguments.of(PRIVATE_KEY_ONE, BigInteger.ZERO, PRIVATE_KEY_ONE),
        Arguments.of(PRIVATE_KEY_ONE, CURVE_ORDER, PRIVATE_KEY_ONE),
        Arguments.of(
            PRIVATE_KEY_ONE, BigInteger.ONE, CURVE_ORDER.subtract(BigInteger.ONE).toByteArray()));
  }

  @Test
  void acceptsSignatureOnLastAllowedAttempt() throws Exception {
    final var invalidKeyPair = keyPair(new byte[] {0}, BigInteger.ONE);
    final var validKeyPair = keyPair(PRIVATE_KEY_ONE, BigInteger.ONE);
    final var attempts = new AtomicInteger();
    try (final var crypto = mockStatic(Openhealth_cryptoKt.class)) {
      crypto
          .when(Openhealth_cryptoKt::generateBrainpoolP256r1KeyPair)
          .thenAnswer(
              invocation -> attempts.incrementAndGet() == 64 ? validKeyPair : invalidKeyPair);

      final byte[] signature = signer.sign(PRIVATE_KEY_ONE, PRIVATE_KEY_ONE);

      assertThat(HexFormat.of().withUpperCase().formatHex(signature))
          .isEqualTo(String.format("%064X%064X", BigInteger.ONE, BigInteger.TWO));
      crypto.verify(Openhealth_cryptoKt::generateBrainpoolP256r1KeyPair, times(64));
      verify(invalidKeyPair, times(63)).close();
      verify(validKeyPair).close();
    }
  }

  @Test
  void failsAfterExactly64InvalidAttempts() throws Exception {
    final var invalidKeyPair = keyPair(new byte[] {0}, BigInteger.ONE);
    try (final var crypto = mockStatic(Openhealth_cryptoKt.class)) {
      crypto.when(Openhealth_cryptoKt::generateBrainpoolP256r1KeyPair).thenReturn(invalidKeyPair);

      assertThatThrownBy(() -> signer.sign(PRIVATE_KEY_ONE, PRIVATE_KEY_ONE))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Failed to create a valid CVC ECDSA signature.");
      crypto.verify(Openhealth_cryptoKt::generateBrainpoolP256r1KeyPair, times(64));
      verify(invalidKeyPair, times(64)).close();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void propagatesCryptoLibraryFailureWithoutRetrying(final boolean afterInvalidAttempt)
      throws Exception {
    final var failure = new CryptoException.Crypto("key generation failed");
    final var invalidKeyPair = keyPair(new byte[] {0}, BigInteger.ONE);
    try (final var crypto = mockStatic(Openhealth_cryptoKt.class)) {
      final var generation = crypto.when(Openhealth_cryptoKt::generateBrainpoolP256r1KeyPair);
      if (afterInvalidAttempt) {
        generation.thenReturn(invalidKeyPair).thenThrow(failure);
      } else {
        generation.thenThrow(failure);
      }

      assertThatThrownBy(() -> signer.sign(PRIVATE_KEY_ONE, PRIVATE_KEY_ONE))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Failed to sign CVC authentication challenge")
          .hasCause(failure);
      crypto.verify(
          Openhealth_cryptoKt::generateBrainpoolP256r1KeyPair, times(afterInvalidAttempt ? 2 : 1));
      verify(invalidKeyPair, times(afterInvalidAttempt ? 1 : 0)).close();
    }
  }

  private static BrainpoolP256r1KeyPair keyPair(
      final byte[] ephemeralPrivateKey, final BigInteger xCoordinate) {
    final var keyPair = mock(BrainpoolP256r1KeyPair.class);
    final byte[] publicKey =
        HexFormat.of().parseHex("04" + String.format("%064X", xCoordinate) + "00".repeat(32));
    when(keyPair.privateKey()).thenReturn(ephemeralPrivateKey);
    when(keyPair.publicKey()).thenReturn(publicKey);
    return keyPair;
  }
}
