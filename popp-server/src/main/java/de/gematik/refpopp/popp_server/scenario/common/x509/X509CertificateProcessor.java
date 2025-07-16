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

import de.gematik.refpopp.popp_server.certificates.X509CertificateParser;
import org.springframework.stereotype.Component;

@Component
public class X509CertificateProcessor {

  private final X509CertificateParser x509CertificateParser;
  private final X509DataExtractor x509DataExtractor;

  public X509CertificateProcessor(
      final X509CertificateParser x509CertificateParser,
      final X509DataExtractor x509DataExtractor) {
    this.x509CertificateParser = x509CertificateParser;
    this.x509DataExtractor = x509DataExtractor;
  }

  public X509Data extractCertificateData(final String sessionId, final byte[] certificateData) {
    final var certificate = x509CertificateParser.parse(certificateData, sessionId);
    return x509DataExtractor.extractFromCertificate(certificate, sessionId);
  }
}
