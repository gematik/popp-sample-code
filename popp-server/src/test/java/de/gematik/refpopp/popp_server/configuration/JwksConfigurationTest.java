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

package de.gematik.refpopp.popp_server.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.refpopp.popp_server.certificates.KeyStoreLoader;
import de.gematik.refpopp.popp_server.controller.JwksController;
import java.security.KeyStore;
import org.jose4j.jwk.JsonWebKeySet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class JwksConfigurationTest {

  private JsonWebKeySet jsonWebKeySet;

  @BeforeEach
  void setUp() {
    final ServiceConfiguration sut = new ServiceConfiguration();
    final KeyStoreLoader loader =
        sut.poppKeyStoreLoader(
            new ClassPathResource("certificates/signer/popp-token-Server-nist-komp61.jks"),
            "gematik");
    final KeyStore keystore = sut.poppKeyStore(loader);

    final JwksConfiguration jwkConfiguration = new JwksConfiguration();
    this.jsonWebKeySet = jwkConfiguration.jwkSource(keystore);
    assertThat(this.jsonWebKeySet.getJsonWebKeys()).hasSize(1);
    assertThat(this.jsonWebKeySet.getJsonWebKeys().getFirst().getKeyId())
        .isEqualTo("staticPublicKeyId");
  }

  @Test
  void thatAValidJwkSetIsReturned() {
    final JwksController controller = new JwksController(this.jsonWebKeySet);
    final String jwks = controller.jwks();
    assertThat(jwks).contains("staticPublicKeyId");
  }
}
