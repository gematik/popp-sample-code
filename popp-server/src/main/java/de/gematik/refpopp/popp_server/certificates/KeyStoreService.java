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
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KeyStoreService {

  private final KeyStore poppKeyStore;
  private final KeyStore connectorKeyStore;
  private final EcPrivateKeyFactory ecPrivateKeyFactory;

  KeyStoreService(
      @Qualifier("poppKeyStore") final KeyStore poppKeyStore,
      @Qualifier("connectorKeyStore") final KeyStore connectorKeyStore,
      final EcPrivateKeyFactory ecPrivateKeyFactory) {
    this.poppKeyStore = poppKeyStore;
    this.connectorKeyStore = connectorKeyStore;
    this.ecPrivateKeyFactory = ecPrivateKeyFactory;
  }

  KeyStoreData getPoppKeyStoreData(
      final ClassPathResource keyStoreResource, final String keyStorePassword) {
    return loadKeyStoreData(poppKeyStore, keyStoreResource, keyStorePassword);
  }

  KeyStoreData getConnectorKeyStoreData(
      final ClassPathResource keyStoreResource, final String keyStorePassword) {
    return loadKeyStoreData(connectorKeyStore, keyStoreResource, keyStorePassword);
  }

  private KeyStoreData loadKeyStoreData(
      final KeyStore keyStore,
      final ClassPathResource keyStoreResource,
      final String keyStorePassword) {
    log.info("| Loading keystore from path: {}", keyStoreResource.getPath());
    if (keyStorePassword == null) {
      throw new KeyStoreException("password is null", "errorCode");
    }

    try {
      final var filename = keyStoreResource.getFilename();
      if (filename == null) {
        throw new KeyStoreException("Failed to get filename from keystore", "errorCode");
      }
      final var commonKeyName = filename.substring(0, filename.lastIndexOf('.'));
      final var rawKey =
          (ECPrivateKey) keyStore.getKey(commonKeyName, keyStorePassword.toCharArray());
      if (rawKey == null) {
        throw new KeyStoreException(
            "No key found under alias '" + commonKeyName + "'", "errorCode");
      }
      final var insPrivateKey = ecPrivateKeyFactory.create(rawKey);

      final var certificate = (X509Certificate) keyStore.getCertificate(commonKeyName);
      if (certificate == null) {
        throw new KeyStoreException(
            "No certificate found under alias '" + commonKeyName + "'", "errorCode");
      }

      return new KeyStoreData(insPrivateKey, certificate);
    } catch (final KeyStoreException e) {
      throw e;
    } catch (final Exception e) {
      throw new KeyStoreException("Failed to load keystore data: " + e.getMessage(), "errorCode");
    }
  }
}
