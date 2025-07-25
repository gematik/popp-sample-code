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

package de.gematik.refpopp.popp_server.scenario.common.x509;

import de.gematik.poppcommons.api.exceptions.CertificateParserException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public final class X509DataExtractor {

  public X509Data extractFromCertificate(
      final X509Certificate certificate, final String sessionId) {
    if (certificate == null) {
      throw new CertificateParserException(sessionId, "Certificate is null", "errorCode");
    }
    final var issuerNames = certificate.getIssuerX500Principal().toString();
    final var subjectNames = certificate.getSubjectX500Principal().toString();
    final var validity = certificate.getNotBefore() + " - " + certificate.getNotAfter();
    final var subjectAttributes = parseDistinguishedName(subjectNames);

    final var subject =
        new X509Data.Subject(
            subjectAttributes.get("CN"),
            subjectAttributes.get("T"),
            subjectAttributes.get("GIVENNAME"),
            subjectAttributes.get("SURNAME"),
            subjectAttributes.get("OU"),
            subjectAttributes.get("OU2"),
            subjectAttributes.get("O"),
            subjectAttributes.get("C"));

    return new X509Data(
        String.valueOf(certificate.getVersion()),
        certificate.getSerialNumber().toString(),
        certificate.getSignature(),
        issuerNames,
        validity,
        certificate.getSigAlgOID(),
        subject);
  }

  private static Map<String, String> parseDistinguishedName(final String dn) {
    final Map<String, String> attributes = new HashMap<>();
    final String[] parts = dn.split(",\\s*");
    for (final String part : parts) {
      final String[] keyValue = part.split("=");
      if (keyValue.length == 2) {
        if (keyValue[0].equals("OU") && attributes.containsKey(keyValue[0])) {
          attributes.put("OU2", keyValue[1]);
        } else {
          attributes.put(keyValue[0], keyValue[1]);
        }
      }
    }
    return attributes;
  }
}
