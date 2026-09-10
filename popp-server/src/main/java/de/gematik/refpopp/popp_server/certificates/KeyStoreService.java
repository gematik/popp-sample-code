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

import de.gematik.poppcommons.api.enums.BdeErrorCode;
import de.gematik.poppcommons.api.exceptions.KeyStoreException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.util.ArrayList;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KeyStoreService {

  private static final String ISSUER_CA_ALIAS = "issuer-ca";

  private final KeyStore poppKeyStore;
  private final KeyStore connectorKeyStore;

  KeyStoreService(
      @Qualifier("poppKeyStore") final KeyStore poppKeyStore,
      @Qualifier("connectorKeyStore") final KeyStore connectorKeyStore) {
    this.poppKeyStore = poppKeyStore;
    this.connectorKeyStore = connectorKeyStore;
  }

  KeyStoreData getPoppKeyStoreData(
      final ClassPathResource keyStoreResource, final String keyStorePassword) {
    return loadKeyStoreData(poppKeyStore, keyStoreResource, keyStorePassword);
  }

  KeyStoreData getConnectorKeyStoreData(
      final ClassPathResource keyStoreResource, final String keyStorePassword) {
    return loadKeyStoreData(
        connectorKeyStore, keyStoreResource, keyStorePassword, Optional.of(ISSUER_CA_ALIAS));
  }

  private KeyStoreData loadKeyStoreData(
      final KeyStore keyStore,
      final ClassPathResource keyStoreResource,
      final String keyStorePassword) {
    return loadKeyStoreData(keyStore, keyStoreResource, keyStorePassword, Optional.empty());
  }

  private KeyStoreData loadKeyStoreData(
      final KeyStore keyStore,
      final ClassPathResource keyStoreResource,
      final String keyStorePassword,
      final Optional<String> issuerCertificateAlias) {
    log.info("| Loading keystore from path: {}", keyStoreResource.getPath());
    if (keyStorePassword == null) {
      throw new KeyStoreException("password is null", BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR);
    }

    try {
      final var filename = keyStoreResource.getFilename();
      if (filename == null) {
        throw new KeyStoreException(
            "Failed to get filename from keystore", BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR);
      }
      final var commonKeyName = filename.substring(0, filename.lastIndexOf('.'));
      final var rawKey =
          (ECPrivateKey) keyStore.getKey(commonKeyName, keyStorePassword.toCharArray());
      if (rawKey == null) {
        throw new KeyStoreException(
            "No key found under alias '" + commonKeyName + "'",
            BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR);
      }

      final var certificateChain = keyStore.getCertificateChain(commonKeyName);
      if (certificateChain == null || certificateChain.length == 0) {
        throw new KeyStoreException(
            "No certificate found under alias '" + commonKeyName + "'",
            BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR);
      }
      final var x509CertificateChain = new ArrayList<X509Certificate>(certificateChain.length);
      for (java.security.cert.Certificate value : certificateChain) {
        if (!(value instanceof X509Certificate certificate)) {
          throw new KeyStoreException(
              "Certificate chain under alias '"
                  + commonKeyName
                  + "' contains a non-X.509 certificate",
              BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR);
        }
        x509CertificateChain.add(certificate);
      }

      final var issuerCertificate =
          issuerCertificateAlias.map(alias -> loadX509Certificate(keyStore, alias));
      return issuerCertificate
          .map(issuer -> KeyStoreData.withIssuer(rawKey, x509CertificateChain, issuer))
          .orElseGet(() -> KeyStoreData.withoutIssuer(rawKey, x509CertificateChain));
    } catch (final KeyStoreException e) {
      throw e;
    } catch (final Exception e) {
      throw new KeyStoreException(
          "Failed to load keystore data: " + e.getMessage(),
          BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR);
    }
  }

  private X509Certificate loadX509Certificate(final KeyStore keyStore, final String alias) {
    try {
      final var certificate = keyStore.getCertificate(alias);
      if (certificate == null) {
        throw new KeyStoreException(
            "No certificate found under alias '" + alias + "'",
            BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR);
      }
      if (!(certificate instanceof X509Certificate x509Certificate)) {
        throw new KeyStoreException(
            "Certificate under alias '" + alias + "' is not an X.509 certificate",
            BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR);
      }
      return x509Certificate;
    } catch (final KeyStoreException e) {
      throw e;
    } catch (final Exception e) {
      throw new KeyStoreException(
          "Failed to load certificate under alias '" + alias + "': " + e.getMessage(),
          BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR);
    }
  }
}
