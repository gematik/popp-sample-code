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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

class CvCertificateParserTest {

  private CvCertificateParser sut;

  @TempDir private Path tempDir;

  @BeforeEach
  void setUp() {
    final var cvcFactory = new CvcFactory();
    sut = new CvCertificateParser(cvcFactory);
  }

  @Test
  void parseEndEntityCertificateShouldReturnCertificate() {
    // given
    final var certificateResource = new ClassPathResource("cvcEndEntity.crt");

    // when
    final var result = sut.parse(certificateResource);

    // then
    assertThat(result).isNotNull();
  }

  @Test
  void parseSubCertificateShouldReturnCertificate() {
    // given
    final var certificateResource = new ClassPathResource("cvcSub.crt");

    // when
    final var result = sut.parse(certificateResource);

    // then
    assertThat(result).isNotNull();
  }

  @Test
  void parseShouldThrowCertificateParserException() {
    // given
    final var certificateResource = new ClassPathResource("nonExistentCertificate.crt");

    // when / then
    assertThrows(CertificateParserException.class, () -> sut.parse(certificateResource));
  }

  @Test
  void parsePathShouldReturnCertificate() throws IOException {
    final var certificatePath = tempDir.resolve("certificate.cvc");
    Files.copy(new ClassPathResource("cvcEndEntity.crt").getInputStream(), certificatePath);

    final var result = sut.parse(certificatePath);

    assertThat(result).isNotNull();
  }

  @Test
  void parsePathShouldThrowCertificateParserException() {
    assertThrows(CertificateParserException.class, () -> sut.parse(tempDir.resolve("missing.cvc")));
  }
}
