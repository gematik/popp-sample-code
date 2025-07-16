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

import de.gematik.poppcommons.api.exceptions.KeyStoreException;
import java.security.KeyStore;
import org.springframework.core.io.Resource;

public class KeyStoreLoader {
  private final Resource truststoreLocation;
  private final String truststorePassword;

  public KeyStoreLoader(final Resource truststoreLocation, final String truststorePassword) {
    this.truststoreLocation = truststoreLocation;
    this.truststorePassword = truststorePassword;
  }

  public KeyStore load() throws KeyStoreException {
    try (final var is = truststoreLocation.getInputStream()) {
      final var truststore = KeyStore.getInstance(KeyStore.getDefaultType());
      truststore.load(is, truststorePassword.toCharArray());
      return truststore;
    } catch (final Exception e) {
      throw new KeyStoreException("Could not load key store " + e.getMessage(), "errorCode");
    }
  }
}
