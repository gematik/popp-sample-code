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

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PublicKey;
import java.util.Iterator;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class JwksConfiguration {

  @Bean(name = "publicJwk")
  public JsonWebKeySet jwkSource(@Qualifier("poppKeyStore") KeyStore keystore) {
    try {
      String firstAlias = keystore.aliases().nextElement();
      Iterator<String> iterator = keystore.aliases().asIterator();
      int i = 0;
      while (iterator.hasNext()) {
        i++;
        iterator.next();
      }
      log.info("Found {} aliases in keystore", i);
      log.info("Using public key from keystore: {}", firstAlias);
      final PublicKey publicKey = keystore.getCertificate(firstAlias).getPublicKey();
      log.info("Public key: {}", publicKey);
      final JsonWebKey jsonWebKey = JsonWebKey.Factory.newJwk(publicKey);
      jsonWebKey.setKeyId("staticPublicKeyId");
      return new JsonWebKeySet(jsonWebKey);
    } catch (final GeneralSecurityException | JoseException e) {
      throw new IllegalStateException("Failed to initialize keystore", e);
    }
  }
}
