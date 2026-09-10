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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.refpopp.popp_server.certificates.CertificateProviderService;
import de.gematik.refpopp.popp_server.certificates.KeyStoreData;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectorTokenCreatorTest {

  @Test
  void createsConnectorToken() {
    final var certificateProviderService = mock(CertificateProviderService.class);
    final var tokenClaims = mock(ConnectorTokenClaims.class);
    final var tokenHeader = mock(ConnectorTokenHeader.class);
    final var jwtTokenBuilder = mock(JwtTokenBuilder.class);
    final var sut =
        new ConnectorTokenCreator(
            certificateProviderService, tokenClaims, tokenHeader, jwtTokenBuilder);
    final var scenarioMessage = mock(StandardScenarioMessage.class);
    final var privateKey = mock(ECPrivateKey.class);
    final var signerCertificate = mock(X509Certificate.class);
    final var issuerCertificate = mock(X509Certificate.class);
    final var keyStoreData =
        KeyStoreData.withIssuer(privateKey, List.of(signerCertificate), issuerCertificate);
    final var headers = new HashMap<String, Object>();
    final var claims = new HashMap<String, Object>();
    when(certificateProviderService.getKeyStoreDataConnector()).thenReturn(keyStoreData);
    when(tokenHeader.create(signerCertificate, "sessionId", issuerCertificate)).thenReturn(headers);
    when(tokenClaims.create(scenarioMessage)).thenReturn(claims);
    when(jwtTokenBuilder.buildJwtToken(headers, claims, privateKey)).thenReturn("jwtToken");

    final var result = sut.createConnectorToken(scenarioMessage, "sessionId");

    assertThat(result).isEqualTo("jwtToken");
    verify(tokenHeader).create(signerCertificate, "sessionId", issuerCertificate);
    verify(tokenClaims).create(scenarioMessage);
    verify(jwtTokenBuilder).buildJwtToken(headers, claims, privateKey);
  }
}
