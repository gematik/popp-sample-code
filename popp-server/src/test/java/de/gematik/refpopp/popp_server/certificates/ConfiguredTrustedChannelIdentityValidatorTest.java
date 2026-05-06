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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import de.gematik.openhealth.asn1.CvCertificate;
import de.gematik.openhealth.crypto.CryptoException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ConfiguredTrustedChannelIdentityValidatorTest {

  private ConfiguredTrustedChannelIdentityValidator sut;
  private CvCertificateParser cvCertificateParser;

  @BeforeEach
  void setUp() {
    sut = new ConfiguredTrustedChannelIdentityValidator(new CvcChainValidator());
    cvCertificateParser = new CvCertificateParser(new CvcFactory());
  }

  @Test
  void validateAcceptsConfiguredTrustedChannelIdentity() throws IOException {
    final var subCaCertificate =
        cvCertificateParser.parse(new ClassPathResource("certificates/cvc/DEGXX120223.crt"));
    final var endEntityCertificate =
        cvCertificateParser.parse(
            new ClassPathResource("certificates/cvc/80276001011699902101-cvc-flag0.crt"));
    final var privateKey =
        new ClassPathResource(
                "identities/PoPP26-Server/80276001011699902101-cvc-flag0/80276001011699902101-cvc-flag0.prv")
            .getInputStream()
            .readAllBytes();

    sut.validate(subCaCertificate, endEntityCertificate, privateKey);
  }

  @Test
  void validateRejectsMissingPrivateKey() {
    assertThatThrownBy(
            () -> sut.validate(mock(CvCertificate.class), mock(CvCertificate.class), new byte[0]))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Configured service private key is not encoded as PKCS#8");
  }

  @Test
  void validateRejectsNullPrivateKey() {
    assertThatThrownBy(
            () -> sut.validate(mock(CvCertificate.class), mock(CvCertificate.class), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Configured service private key is not encoded as PKCS#8");
  }

  @Test
  void validateWrapsChainValidationFailure() throws CryptoException {
    final var cvcChainValidator = mock(CvcChainValidator.class);
    sut = new ConfiguredTrustedChannelIdentityValidator(cvcChainValidator);
    final var subCaCertificate = mock(CvCertificate.class);
    final var endEntityCertificate = mock(CvCertificate.class);
    doThrow(new CryptoException.Crypto("invalid chain"))
        .when(cvcChainValidator)
        .validate(endEntityCertificate, subCaCertificate);

    assertThatThrownBy(() -> sut.validate(subCaCertificate, endEntityCertificate, new byte[] {1}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed to validate configured trusted-channel identity");
  }
}
