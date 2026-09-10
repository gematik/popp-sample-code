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

package de.gematik.refpopp.popp_server.scenario.common.token;

import de.gematik.poppcommons.api.enums.BdeErrorCode;
import de.gematik.poppcommons.api.exceptions.CertificateParserException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class ConnectorTokenHeader {

  @Value("${jwt-token.connector.typ:JWT}")
  private String connectorTokenType;

  private final OcspResponseProvider ocspResponseProvider;

  ConnectorTokenHeader(final OcspResponseProvider ocspResponseProvider) {
    this.ocspResponseProvider = ocspResponseProvider;
  }

  Map<String, Object> create(
      final X509Certificate signerCertificate,
      final String sessionId,
      final X509Certificate issuerCertificate) {
    return Map.of(
        "typ",
        connectorTokenType,
        "x5c",
        encodeCertificate(signerCertificate, sessionId),
        "stpl",
        ocspResponseProvider.getResponse(
            OcspRequest.withIssuer(sessionId, signerCertificate, issuerCertificate)));
  }

  private String encodeCertificate(final X509Certificate certificate, final String sessionId) {
    try {
      return Base64.getEncoder().encodeToString(certificate.getEncoded());
    } catch (final CertificateEncodingException e) {
      throw new CertificateParserException(
          sessionId, "Could not encode certificate", BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR, e);
    }
  }
}
