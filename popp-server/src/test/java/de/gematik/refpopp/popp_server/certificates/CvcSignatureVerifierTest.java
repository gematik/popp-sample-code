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

package de.gematik.refpopp.popp_server.certificates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import de.gematik.openhealth.asn1.CvCertificate;
import de.gematik.openhealth.crypto.CryptoException;
import de.gematik.openhealth.crypto.Openhealth_cryptoKt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CvcSignatureVerifierTest {

  private CvcSignatureVerifier sut;
  private CvCertificateParser parser;

  @BeforeEach
  void setUp() {
    sut = new CvcSignatureVerifier();
    parser = new CvCertificateParser(new CvcFactory());
  }

  @Test
  void verifyCvcEcdsaValueSignatureReturnsFalseForInvalidSignature() {
    final var certificate =
        parser.parse(new ClassPathResource("certificates/cvc/80276001011699902101-cvc-flag0.crt"));

    final var result =
        sut.verifyCvcEcdsaValueSignature(certificate, "nonce".getBytes(), new byte[64]);

    assertThat(result).isFalse();
  }

  @Test
  void verifyCvcEcdsaValueSignatureWrapsCryptoException() {
    final var certificate = org.mockito.Mockito.mock(CvCertificate.class);
    final var token = "nonce".getBytes();
    final var signature = "signature".getBytes();
    try (final var cryptoMock = mockStatic(Openhealth_cryptoKt.class)) {
      cryptoMock
          .when(
              () ->
                  Openhealth_cryptoKt.verifyCvcEcdsaValueSignature(
                      any(CvCertificate.class), any(byte[].class), any(byte[].class)))
          .thenThrow(new CryptoException.Crypto("invalid signature"));

      assertThatThrownBy(() -> verifyCvcEcdsaValueSignature(certificate, token, signature))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("CVC ECDSA verification failed");
    }
  }

  private void verifyCvcEcdsaValueSignature(
      final CvCertificate certificate, final byte[] token, final byte[] signature) {
    sut.verifyCvcEcdsaValueSignature(certificate, token, signature);
  }
}
