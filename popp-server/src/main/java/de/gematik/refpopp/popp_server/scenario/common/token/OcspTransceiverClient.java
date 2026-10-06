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

import de.gematik.pki.gemlibpki.commons.exception.GemPkiException;
import de.gematik.pki.gemlibpki.commons.ocsp.OcspTransceiver;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.springframework.stereotype.Component;

/** Retrieves OCSP responses through the Gematik PKI OCSP transceiver. */
@Component
final class OcspTransceiverClient {

  /**
   * Requests the OCSP response for an end-entity certificate from a responder.
   *
   * @param endEntityCertificate the certificate whose status is requested
   * @param issuerCertificate the issuer certificate of the end-entity certificate
   * @param responderUrl the URL of the OCSP responder
   * @param productType the product type sent with the request
   * @param timeoutSeconds the request timeout in seconds
   * @return an optional containing the OCSP response
   * @throws GemPkiException if the request cannot be processed
   */
  Optional<OCSPResp> getOcspResponse(
      final X509Certificate endEntityCertificate,
      final X509Certificate issuerCertificate,
      final String responderUrl,
      final String productType,
      final int timeoutSeconds)
      throws GemPkiException {
    return OcspTransceiver.builder()
        .productType(productType)
        .x509EeCert(endEntityCertificate)
        .x509IssuerCert(issuerCertificate)
        .ssp(responderUrl)
        .ocspTimeoutSeconds(timeoutSeconds)
        .build()
        .getOcspResponse();
  }

  /**
   * Returns the DER-encoded representation of an OCSP response.
   *
   * @param response the OCSP response to encode
   * @return the encoded OCSP response
   * @throws IOException if the response cannot be encoded
   */
  byte[] getEncoded(final OCSPResp response) throws IOException {
    return response.getEncoded();
  }
}
