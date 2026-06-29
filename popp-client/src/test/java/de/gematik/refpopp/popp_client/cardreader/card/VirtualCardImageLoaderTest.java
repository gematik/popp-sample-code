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

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;

class VirtualCardImageLoaderTest {

  private static final String CARD_IMAGE = "IMG_eGK_G21_TU_root6 1.xml";

  @Test
  void loadFromClasspathParsesExpectedFields() throws Exception {
    final VirtualCardImageLoader loader = new VirtualCardImageLoader();

    final VirtualCardImageData data = loader.load(CARD_IMAGE);

    assertThat(data.cvCertificate()).isNotBlank().startsWith("7F21");
    assertThat(data.authCertificate()).isNotBlank().startsWith("3082");
    assertThat(data.subCaCvCertificate()).isNotBlank().startsWith("7F21");
    assertThat(data.version2()).isNotBlank().startsWith("ef2b");
    assertThat(data.egkAuthCvcPrivateKey()).hasSize(32);
  }

  @Test
  void loadFromAbsolutePathMatchesClasspathData() throws Exception {
    final VirtualCardImageLoader loader = new VirtualCardImageLoader();

    final Path tempFile = Files.createTempFile("virtual-card-loader", ".xml");
    try {
      try (InputStream is = getClass().getClassLoader().getResourceAsStream(CARD_IMAGE)) {
        assertThat(is).isNotNull();
        Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
      }

      final VirtualCardImageData fromClasspath = loader.load(CARD_IMAGE);
      final VirtualCardImageData fromFile = loader.load(tempFile.toString());

      assertThat(fromFile.cvCertificate()).isEqualTo(fromClasspath.cvCertificate());
      assertThat(fromFile.authCertificate()).isEqualTo(fromClasspath.authCertificate());
      assertThat(fromFile.subCaCvCertificate()).isEqualTo(fromClasspath.subCaCvCertificate());
      assertThat(fromFile.version2()).isEqualTo(fromClasspath.version2());
      assertThat(fromFile.egkAuthCvcPrivateKey())
          .containsExactly(fromClasspath.egkAuthCvcPrivateKey());
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  void loadThrowsFileNotFoundForMissingImage() {
    final VirtualCardImageLoader loader = new VirtualCardImageLoader();

    assertThatThrownBy(() -> loader.load("missing-virtual-card.xml"))
        .isInstanceOf(FileNotFoundException.class)
        .hasMessageContaining("missing-virtual-card.xml");
  }
}
