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

package de.gematik.refpopp.popp_client.configuration;

import java.security.KeyStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class PoppTokenVerificationConfiguration {
  private final Resource location;
  private final String password;

  public PoppTokenVerificationConfiguration(
      @Value("${federation.keystore.location}") Resource location,
      @Value("${federation.keystore.password}") String password) {
    this.location = location;
    this.password = password;
  }

  @Bean
  public KeyStore keyStore() {
    try (var is = location.getInputStream()) {
      final var keyStore = KeyStore.getInstance("jks");
      keyStore.load(is, password.toCharArray());
      return keyStore;
    } catch (Exception e) {
      throw new IllegalStateException("Could not load key store", e);
    }
  }
}
