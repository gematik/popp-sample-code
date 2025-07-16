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

package de.gematik.refpopp.popp_server.hashdb;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cms.SignerId;
import org.springframework.stereotype.Component;

/** Component to find a trusted X509Certificate in a KeyStore based on a SignerId. */
@Slf4j
@Component
public class TrustedCertificateFinder {
  X509Certificate findTrustedCertificate(final KeyStore keyStore, final SignerId sid) {
    try {
      final var issuerName = sid.getIssuer();
      final var serial = sid.getSerialNumber();

      final Enumeration<String> aliases = keyStore.aliases();
      while (aliases.hasMoreElements()) {
        final var alias = aliases.nextElement();
        final var cert = keyStore.getCertificate(alias);
        if (cert instanceof final X509Certificate xcert) {
          final X500Name certIssuer =
              X500Name.getInstance(xcert.getIssuerX500Principal().getEncoded());
          if (certIssuer.equals(issuerName) && xcert.getSerialNumber().equals(serial)) {
            return xcert;
          }
        }
      }
    } catch (final Exception e) {
      log.error("Error finding trusted certificate: {}", e.getMessage());
    }
    return null;
  }
}
