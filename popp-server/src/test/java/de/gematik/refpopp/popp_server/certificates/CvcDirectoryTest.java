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

import de.gematik.poppcommons.api.exceptions.CertificateParserException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

class CvcDirectoryTest {

  private final CvCertificateParser parser = new CvCertificateParser(new CvcFactory());

  @TempDir private Path tempDir;

  @Test
  void loadIndexesTrustedCvcFilesByChr() throws IOException {
    final var trustedPath = tempDir.resolve("PKI_CVC.G2").resolve("trusted").resolve("nested");
    Files.createDirectories(trustedPath);
    Files.copy(
        new ClassPathResource("certificates/cvc/DEGXX120223.crt").getInputStream(),
        trustedPath.resolve("sub-ca.cvc"));
    Files.copy(
        new ClassPathResource("certificates/cvc/80276001011699902101-cvc-flag0.crt")
            .getInputStream(),
        trustedPath.resolve("end-entity.cvc"));
    Files.writeString(trustedPath.resolve("ignored.txt"), "not a certificate");
    final var endEntityChr =
        CvCertificateSupport.chr(
            parser.parse(
                new ClassPathResource("certificates/cvc/80276001011699902101-cvc-flag0.crt")));
    final var subCaChr =
        CvCertificateSupport.chr(
            parser.parse(new ClassPathResource("certificates/cvc/DEGXX120223.crt")));

    final var directory = CvcDirectory.load(tempDir, parser);

    assertThat(directory.all()).hasSize(2);
    assertThat(directory.findByChr(subCaChr)).isPresent();
    assertThat(directory.findByChr(endEntityChr)).isPresent();
    assertThat(directory.findAllByChr("missing")).isEmpty();
  }

  @Test
  void loadThrowsCertificateParserExceptionWhenTrustedDirectoryIsMissing() {
    assertThatThrownBy(() -> CvcDirectory.load(tempDir, parser))
        .isInstanceOf(CertificateParserException.class)
        .hasMessageContaining("Failed to load trusted CVC directory");
  }
}
