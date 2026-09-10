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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.certificates.CertificateProviderService;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509Data;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PoppTokenCreatorTest {

  private PoppTokenCreator sut;
  private CertificateProviderService certificateProviderServiceMock;
  private PoppTokenClaims tokenClaimsMock;
  private PoppTokenHeader tokenHeaderMock;
  private JwtTokenBuilder jwtTokenBuilderMock;

  @BeforeEach
  void setUp() {
    certificateProviderServiceMock = mock(CertificateProviderService.class, RETURNS_DEEP_STUBS);
    tokenClaimsMock = mock(PoppTokenClaims.class);
    tokenHeaderMock = mock(PoppTokenHeader.class);
    jwtTokenBuilderMock = mock(JwtTokenBuilder.class);
    sut =
        new PoppTokenCreator(
            certificateProviderServiceMock, tokenClaimsMock, tokenHeaderMock, jwtTokenBuilderMock);
  }

  @Test
  void createPoppToken() {
    // given
    final X509Data x509DataMock = mock(X509Data.class);
    final var sessionId = "sessionId";
    final var privateKeyMock = mock(ECPrivateKey.class);
    final var certificateMock = mock(X509Certificate.class);
    final var publicKeyMock = mock(ECPublicKey.class);
    when(certificateProviderServiceMock.getKeyStoreDataPoppToken().certificate())
        .thenReturn(certificateMock);
    when(certificateMock.getPublicKey()).thenReturn(publicKeyMock);
    when(certificateProviderServiceMock.getKeyStoreDataPoppToken().privateKey())
        .thenReturn(privateKeyMock);
    final var map = new HashMap<String, Object>();
    when(tokenClaimsMock.createPoppClaims(x509DataMock, sessionId)).thenReturn(map);
    when(tokenHeaderMock.createPoppHeader(publicKeyMock, sessionId)).thenReturn(map);
    when(jwtTokenBuilderMock.buildJwtToken(map, map, privateKeyMock)).thenReturn("jwtToken");

    // when
    final var result = sut.createPoppToken(x509DataMock, sessionId);

    // then
    assertThat(result).isEqualTo("jwtToken");
    verify(certificateProviderServiceMock, times(3)).getKeyStoreDataPoppToken();
    verify(tokenClaimsMock).createPoppClaims(x509DataMock, sessionId);
    verify(tokenHeaderMock).createPoppHeader(publicKeyMock, sessionId);
    verify(jwtTokenBuilderMock).buildJwtToken(map, map, privateKeyMock);
  }
}
