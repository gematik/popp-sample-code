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

import de.gematik.poppcommons.api.exceptions.ImportDataException;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.Security;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring Component for verifying CMS signatures on byte arrays using a trusted KeyStore. */
@Component
@Slf4j
public class CmsSignatureVerifier {

  private final KeyStore trustStore;
  private final CMSSignedDataParserFactory cmsSignedDataParserFactory;
  private final TrustedCertificateFinder trustedCertificateFinder;
  private final SignerInfoVerifierBuilder signerInfoVerifierBuilder;

  public CmsSignatureVerifier(
      @Qualifier("hashKeyStore") final KeyStore trustStore,
      final CMSSignedDataParserFactory cmsSignedDataParserFactory,
      final TrustedCertificateFinder trustedCertificateFinder,
      final SignerInfoVerifierBuilder signerInfoVerifierBuilder) {
    this.trustStore = trustStore;
    this.cmsSignedDataParserFactory = cmsSignedDataParserFactory;
    this.trustedCertificateFinder = trustedCertificateFinder;
    this.signerInfoVerifierBuilder = signerInfoVerifierBuilder;
  }

  @PostConstruct
  public void registerProvider() {
    if (Security.getProvider("BC") == null) {
      Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }
  }

  /**
   * Verifies the CMS signature of the given byte array.
   *
   * @param cmsStream the byte array containing the CMS signed data
   * @param sessionId the session ID for error reporting
   * @return true if the signature is valid, false otherwise
   */
  public boolean isSignatureValid(final InputStream cmsStream, final String sessionId) {
    try {
      final var parser = cmsSignedDataParserFactory.createParser(cmsStream, sessionId);
      parser.getSignedContent().drain();
      final var signers = parser.getSignerInfos();
      for (final var si : signers.getSigners()) {
        final var cert = trustedCertificateFinder.findTrustedCertificate(trustStore, si.getSID());
        if (cert == null || !si.verify(signerInfoVerifierBuilder.build(cert))) {
          return false;
        }
      }
      return true;
    } catch (final Exception e) {
      throw new ImportDataException(
          sessionId, "CMS verification failed: " + e.getMessage(), "errorCode");
    }
  }
}
