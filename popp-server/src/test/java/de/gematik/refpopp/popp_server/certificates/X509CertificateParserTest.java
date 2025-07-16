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
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.gematik.poppcommons.api.exceptions.CertificateParserException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class X509CertificateParserTest {

  private X509CertificateParser x509CertificateParser;

  @BeforeEach
  void setUp() {
    x509CertificateParser = new X509CertificateParser();
  }

  @Test
  void parseShouldReturnX509Certificate() {
    // given
    final var certificateResource = new ClassPathResource("root.der");

    // when
    final var x509Certificate = x509CertificateParser.parse(certificateResource);

    // then
    assertThat(x509Certificate).isNotNull();
  }

  @Test
  void parseShouldThrowCertificateParserException() {
    // given
    final var certificateResource = new ClassPathResource("nonExistentCertificate.crt");

    // when / then
    assertThrows(
        CertificateParserException.class, () -> x509CertificateParser.parse(certificateResource));
  }

  @Test
  void parseShouldReturnX509CertificateFromByteArray() throws IOException {
    // given
    final var certificate = "root.der";
    final var resource = new ClassPathResource(certificate);
    final var contentAsByteArray = resource.getContentAsByteArray();

    // when
    final var x509Certificate = x509CertificateParser.parse(contentAsByteArray, "sessionId");

    // then
    assertThat(x509Certificate).isNotNull();
  }

  @Test
  void parseShouldThrowCertificateParserExceptionFromByteArray() {
    // given
    final var certificate = "nonExistentCertificate.crt";
    final var contentAsByteArray = certificate.getBytes();

    // when / then
    assertThrows(
        CertificateParserException.class,
        () -> x509CertificateParser.parse(contentAsByteArray, "sessionId"));
  }
}
