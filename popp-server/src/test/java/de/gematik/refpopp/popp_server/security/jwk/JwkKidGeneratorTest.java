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

package de.gematik.refpopp.popp_server.security.jwk;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.refpopp.popp_server.configuration.ServiceConfiguration;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class JwkKidGeneratorTest {

  private static final String POPP_KEYSTORE_PATH =
      "certificates/signer/popp-token-Server-nist-komp61.jks";
  private static final String POPP_KEYSTORE_PASSWORD = "gematik";
  private static final String EXPECTED_KID = "4IVYHy721K0rnjZ8_9fnsKofs0eKhGOcqEDVo0RBZFQ";

  private JwkKidGenerator sut;

  @BeforeEach
  void setUp() {
    this.sut = new JwkKidGenerator();
  }

  @Test
  void generatesExpectedKidForConfiguredServerPublicKey() throws Exception {
    final var publicKey = loadPoppPublicKey();

    final var kid = this.sut.generate(publicKey);

    assertThat(kid).isEqualTo(EXPECTED_KID);
  }

  @Test
  void generatesSameKidForRepeatedCallsWithSamePublicKey() throws Exception {
    final var publicKey = loadPoppPublicKey();

    final var firstKid = this.sut.generate(publicKey);
    final var secondKid = this.sut.generate(publicKey);

    assertThat(firstKid).isEqualTo(secondKid);
  }

  @Test
  void generatesBase64UrlWithoutPaddingSha256Thumbprint() throws Exception {
    final var publicKey = loadPoppPublicKey();

    final var kid = this.sut.generate(publicKey);
    final byte[] decodedKid = Base64.getUrlDecoder().decode(kid);

    assertThat(kid).doesNotContain("=").matches("^[A-Za-z0-9_-]+$");
    assertThat(decodedKid).hasSize(32);
  }

  private ECPublicKey loadPoppPublicKey() throws Exception {
    final var configuration = new ServiceConfiguration();
    final var keyStoreLoader =
        configuration.poppKeyStoreLoader(
            new ClassPathResource(POPP_KEYSTORE_PATH), POPP_KEYSTORE_PASSWORD);
    final var keyStore = configuration.poppKeyStore(keyStoreLoader);

    final var alias = keyStore.aliases().nextElement();
    return (ECPublicKey) keyStore.getCertificate(alias).getPublicKey();
  }
}
