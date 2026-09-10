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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
    assertThat(data.rcaCsKeyIdentifier()).isEqualTo("4445475858870222");
    assertThat(data.rcaAdminCmsCsKeyIdentifier()).isEqualTo("0000000000000013");
  }

  @Test
  void loadParsesSubCaCvCertificateFromPrimaryObjectId() throws Exception {
    final VirtualCardImageLoader loader = new VirtualCardImageLoader();
    final Path imageFile = Files.createTempFile("virtual-card-loader", ".xml");
    try {
      Files.writeString(
          imageFile,
          """
          <card>
            <child id="EF.C.CA.CS.E256">
              <attributes><attribute id="body">7F21</attribute></attributes>
            </child>
            <child id="PuK.RCA.CS.E256">
              <attributes><attribute id="keyIdentifier">4445475858870222</attribute></attributes>
            </child>
            <child id="PuK.RCA.ADMINCMS.CS.E256">
              <attributes><attribute id="keyIdentifier">0000000000000013</attribute></attributes>
            </child>
          </card>
          """);

      final VirtualCardImageData data = loader.load(imageFile.toString());

      assertThat(data.subCaCvCertificate()).isEqualTo("7F21");
    } finally {
      Files.deleteIfExists(imageFile);
    }
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
      assertThat(fromFile.rcaCsKeyIdentifier()).isEqualTo(fromClasspath.rcaCsKeyIdentifier());
      assertThat(fromFile.rcaAdminCmsCsKeyIdentifier())
          .isEqualTo(fromClasspath.rcaAdminCmsCsKeyIdentifier());
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

  @Test
  void loadRejectsMissingRcaCsKeyIdentifier() throws Exception {
    final VirtualCardImageLoader loader = new VirtualCardImageLoader();
    final Path imageFile = Files.createTempFile("virtual-card-loader", ".xml");
    try {
      Files.writeString(imageFile, "<card />");

      assertThatThrownBy(() -> loader.load(imageFile.toString()))
          .isInstanceOf(java.io.IOException.class)
          .hasMessageContaining("PuK.RCA.CS.E256");
    } finally {
      Files.deleteIfExists(imageFile);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidRcaCsKeyIdentifierCases")
  void loadRejectsInvalidRcaCsKeyIdentifier(
      String testCase,
      String rcaCsKeyIdentifier,
      String rcaAdminCmsCsKeyIdentifier,
      String expectedMessage)
      throws Exception {
    final VirtualCardImageLoader loader = new VirtualCardImageLoader();
    final Path imageFile = Files.createTempFile("virtual-card-loader", ".xml");
    try {
      Files.writeString(imageFile, cardImage(rcaCsKeyIdentifier, rcaAdminCmsCsKeyIdentifier));

      assertThatThrownBy(() -> loader.load(imageFile.toString()))
          .isInstanceOf(java.io.IOException.class)
          .hasMessageContaining(expectedMessage);
    } finally {
      Files.deleteIfExists(imageFile);
    }
  }

  private static Stream<Arguments> invalidRcaCsKeyIdentifierCases() {
    return Stream.of(
        Arguments.of(
            "invalid RCA CS key identifier",
            "not-a-key-identifier",
            "0000000000000013",
            "PuK.RCA.CS.E256"),
        Arguments.of(
            "missing RCA AdminCMS CS key identifier",
            "4445475858870222",
            null,
            "PuK.RCA.ADMINCMS.CS.E256"),
        Arguments.of(
            "invalid RCA AdminCMS CS key identifier",
            "4445475858870222",
            "not-a-key-identifier",
            "PuK.RCA.ADMINCMS.CS.E256"));
  }

  private static String cardImage(String rcaCsKeyIdentifier, String rcaAdminCmsCsKeyIdentifier) {
    final String adminCmsChild =
        rcaAdminCmsCsKeyIdentifier == null
            ? ""
            : """
            <child id="PuK.RCA.ADMINCMS.CS.E256">
              <attributes><attribute id="keyIdentifier">%s</attribute></attributes>
            </child>
            """
                .formatted(rcaAdminCmsCsKeyIdentifier);
    return """
    <card>
      <child id="PuK.RCA.CS.E256">
        <attributes><attribute id="keyIdentifier">%s</attribute></attributes>
      </child>
      %s
    </card>
    """
        .formatted(rcaCsKeyIdentifier, adminCmsChild);
  }
}
