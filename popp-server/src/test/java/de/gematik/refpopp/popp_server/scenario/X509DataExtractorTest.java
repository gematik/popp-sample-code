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

package de.gematik.refpopp.popp_server.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.exceptions.CertificateParserException;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509DataExtractor;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class X509DataExtractorTest {

  private X509DataExtractor sut;
  private X509Certificate x509CertificateMock;

  @BeforeEach
  void setUp() {
    x509CertificateMock = mock(X509Certificate.class);
    sut = new X509DataExtractor();
  }

  @Test
  void extractFromCertificate() {
    // given
    final var subjectNames =
        "CN=Dr. Kriemhild D. T. M.-E. NötherTEST-ONLY, T=Dr., GIVENNAME=Kriemhild Dörthe Tina"
            + " Marie-Ella, SURNAME=Nöther, OU=X110540580, OU=109500969, O=Test GKV-SVNOT-VALID,"
            + " C=DE";
    final var sessionId = "sessionId";
    final var x500PrincipalMock = mock(X500Principal.class);
    when(x509CertificateMock.getSubjectX500Principal()).thenReturn(x500PrincipalMock);
    when(x500PrincipalMock.toString()).thenReturn(subjectNames);
    when(x509CertificateMock.getIssuerX500Principal()).thenReturn(x500PrincipalMock);
    when(x509CertificateMock.getSerialNumber()).thenReturn(BigInteger.ONE);

    // when
    final var result = sut.extractFromCertificate(x509CertificateMock, sessionId);

    // then
    verify(x509CertificateMock).getSubjectX500Principal();
    verify(x509CertificateMock).getIssuerX500Principal();
    assertThat(result).isNotNull();
  }

  @Test
  void extractFromCertificateThrowsCertificateParserException() {
    // given
    final var sessionId = "sessionId";

    // when / then
    assertThrows(
        CertificateParserException.class, () -> sut.extractFromCertificate(null, sessionId));
  }
}
