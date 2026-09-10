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
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.refpopp.popp_server.certificates.CertificateProviderService;
import org.springframework.stereotype.Component;

@Component
public class ConnectorTokenCreator {

  private final CertificateProviderService certificateProviderService;
  private final ConnectorTokenClaims tokenClaims;
  private final ConnectorTokenHeader tokenHeader;
  private final JwtTokenBuilder jwtTokenBuilder;

  ConnectorTokenCreator(
      final CertificateProviderService certificateProviderService,
      final ConnectorTokenClaims tokenClaims,
      final ConnectorTokenHeader tokenHeader,
      final JwtTokenBuilder jwtTokenBuilder) {
    this.certificateProviderService = certificateProviderService;
    this.tokenClaims = tokenClaims;
    this.tokenHeader = tokenHeader;
    this.jwtTokenBuilder = jwtTokenBuilder;
  }

  public String createConnectorToken(
      final StandardScenarioMessage scenarioMessage, final String sessionId) {
    final var keyStoreData = certificateProviderService.getKeyStoreDataConnector();
    final var issuerCertificate =
        keyStoreData
            .issuerCertificate()
            .orElseThrow(
                () ->
                    new ScenarioException(
                        sessionId,
                        "Missing issuer certificate",
                        BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR));
    final var headers =
        tokenHeader.create(signerCertificate(keyStoreData), sessionId, issuerCertificate);
    final var claims = tokenClaims.create(scenarioMessage);

    return jwtTokenBuilder.buildJwtToken(headers, claims, keyStoreData.privateKey());
  }

  private java.security.cert.X509Certificate signerCertificate(
      final de.gematik.refpopp.popp_server.certificates.KeyStoreData keyStoreData) {
    return keyStoreData.certificate();
  }
}
