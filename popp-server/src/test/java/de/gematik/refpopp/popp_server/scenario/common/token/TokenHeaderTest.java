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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.exceptions.CertificateParserException;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

class TokenHeaderTest {

  private TokenHeader sut;

  @Mock private X509Certificate mockCertificate;

  @Mock private ECPublicKey mockPublicKey;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ECPoint mockEcPoint;

  private AutoCloseable autoCloseable;

  @BeforeEach
  void setUp() {
    autoCloseable = MockitoAnnotations.openMocks(this);
    sut = new TokenHeader();
    ReflectionTestUtils.setField(sut, "poppTokenType", "vnd.telematik.popp+jwt");
    ReflectionTestUtils.setField(sut, "connectorTokenType", "JWT");
    ReflectionTestUtils.setField(sut, "ocspResponsePath", "ocsp-response.txt");

    when(mockPublicKey.getW()).thenReturn(mockEcPoint);
  }

  @AfterEach
  void tearDown() throws Exception {
    autoCloseable.close();
  }

  @Test
  void createHeaderForPoppToken() throws CertificateEncodingException {
    // given
    final byte[] dummyCertBytes = "dummy-cert".getBytes();
    when(mockCertificate.getEncoded()).thenReturn(dummyCertBytes);
    final byte[] xBytes = "dummy-x".getBytes();
    final byte[] yBytes = "dummy-y".getBytes();
    when(mockEcPoint.getAffineX().toByteArray()).thenReturn(xBytes);
    when(mockEcPoint.getAffineY().toByteArray()).thenReturn(yBytes);

    final var sessionId = "test-session";

    // when
    final Map<String, Object> header =
        sut.createHeader(mockCertificate, mockPublicKey, sessionId, TokenType.POPP);

    // then
    assertThat(header)
        .isNotNull()
        .hasSize(2)
        .containsEntry("typ", "vnd.telematik.popp+jwt")
        .containsKey("kid");
  }

  @Test
  void createHeaderForConnectorToken() throws CertificateEncodingException {
    // given
    final byte[] dummyCertBytes = "dummy-cert".getBytes();
    when(mockCertificate.getEncoded()).thenReturn(dummyCertBytes);
    final var sessionId = "test-session";
    final byte[] xBytes = "dummy-x".getBytes();
    final byte[] yBytes = "dummy-y".getBytes();
    when(mockEcPoint.getAffineX().toByteArray()).thenReturn(xBytes);
    when(mockEcPoint.getAffineY().toByteArray()).thenReturn(yBytes);

    // when
    final Map<String, Object> header =
        sut.createHeader(mockCertificate, mockPublicKey, sessionId, TokenType.CONNECTOR);

    // then
    assertThat(header)
        .isNotNull()
        .hasSize(3)
        .containsEntry("typ", "JWT")
        .containsEntry("stpl", "dmFsaWQ=")
        .containsKey("x5c");
  }

  @Test
  void createHeaderForConnectorTokenThrowsExceptionWhenOcspResponseCannotBeRead()
      throws CertificateEncodingException {
    // given
    ReflectionTestUtils.setField(sut, "ocspResponsePath", "blub");
    final byte[] dummyCertBytes = "dummy-cert".getBytes();
    when(mockCertificate.getEncoded()).thenReturn(dummyCertBytes);
    final var sessionId = "test-session";
    final byte[] xBytes = "dummy-x".getBytes();
    final byte[] yBytes = "dummy-y".getBytes();
    when(mockEcPoint.getAffineX().toByteArray()).thenReturn(xBytes);
    when(mockEcPoint.getAffineY().toByteArray()).thenReturn(yBytes);

    // when & then
    final var exception =
        assertThrows(
            ScenarioException.class,
            () -> sut.createHeader(mockCertificate, mockPublicKey, sessionId, TokenType.CONNECTOR));
    assertThat(exception.getMessage()).isEqualTo("Could not read OCSP response");
  }

  @Test
  void createHeaderForConnectorTokenThrowsCertificateEncodingException()
      throws CertificateEncodingException {
    // given
    when(mockCertificate.getEncoded()).thenThrow(new CertificateEncodingException());
    final var sessionId = "test-session";
    final byte[] xBytes = "dummy-x".getBytes();
    final byte[] yBytes = "dummy-y".getBytes();
    when(mockEcPoint.getAffineX().toByteArray()).thenReturn(xBytes);
    when(mockEcPoint.getAffineY().toByteArray()).thenReturn(yBytes);

    // when & then
    final var exception =
        assertThrows(
            CertificateParserException.class,
            () -> sut.createHeader(mockCertificate, mockPublicKey, sessionId, TokenType.CONNECTOR));
    assertThat(exception.getMessage()).isEqualTo("Could not encode certificate");
  }
}
