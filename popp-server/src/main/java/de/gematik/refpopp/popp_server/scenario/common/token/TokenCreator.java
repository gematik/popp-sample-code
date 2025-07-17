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

import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.refpopp.popp_server.certificates.CertificateProviderService;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509Data;
import java.security.interfaces.ECPublicKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TokenCreator implements JwtTokenCreator {

  private final CertificateProviderService certificateProviderService;
  private final TokenClaims tokenClaims;
  private final TokenHeader tokenHeader;
  private final JwtTokenBuilder jwtTokenBuilder;

  TokenCreator(
      final CertificateProviderService certificateProviderService,
      final TokenClaims tokenClaims,
      final TokenHeader tokenHeader,
      final JwtTokenBuilder jwtTokenBuilder) {
    this.certificateProviderService = certificateProviderService;
    this.tokenClaims = tokenClaims;
    this.tokenHeader = tokenHeader;
    this.jwtTokenBuilder = jwtTokenBuilder;
  }

  @Override
  public String createPoppToken(final X509Data x509Data, final String sessionId) {
    final var keyStoreData = certificateProviderService.getKeyStoreDataPoppToken();
    final var privateKey = keyStoreData.privateKey();
    final var signerCertificate = keyStoreData.certificate();
    final var publicKey = (ECPublicKey) signerCertificate.getPublicKey();

    final var headers =
        tokenHeader.createHeader(signerCertificate, publicKey, sessionId, TokenType.POPP);
    final var claims = tokenClaims.createPoppClaims(x509Data, sessionId);

    return jwtTokenBuilder.buildJwtToken(headers, claims, privateKey);
  }

  @Override
  public String createConnectorToken(
      final StandardScenarioMessage scenarioMessage, final String sessionId) {
    final var keyStoreData = certificateProviderService.getKeyStoreDataConnector();
    final var privateKey = keyStoreData.privateKey();
    final var signerCertificate = keyStoreData.certificate();
    final var publicKey = (ECPublicKey) signerCertificate.getPublicKey();

    final var headers =
        tokenHeader.createHeader(signerCertificate, publicKey, sessionId, TokenType.CONNECTOR);
    final var claims = tokenClaims.createConnectorClaims(scenarioMessage);

    return jwtTokenBuilder.buildJwtToken(headers, claims, privateKey);
  }
}
