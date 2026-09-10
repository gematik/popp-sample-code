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

import de.gematik.refpopp.popp_server.certificates.KeyStoreLoader;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ClassPathResource;

class OcspResponderUrlExtractorTest {

  private final OcspResponderUrlExtractor sut = new OcspResponderUrlExtractor();

  @Test
  void extractReadsOcspResponderUrlFromAuthorityInformationAccess() throws Exception {
    // given
    final var keyStoreResource =
        new ClassPathResource("certificates/signer/popp-Server-nist-komp61.jks");
    final var keyStore = new KeyStoreLoader(keyStoreResource, "gematik").load();
    final var certificate = (X509Certificate) keyStore.getCertificate("popp-server-nist-komp61");

    // when
    final var responderUrl = sut.extract(certificate);

    // then
    assertThat(responderUrl).contains("http://ehca.gematik.de/ecc-ocsp");
  }

  @Test
  void extractReturnsEmptyWhenAuthorityInformationAccessIsMissing() throws Exception {
    // given
    final var certificate = Mockito.mock(X509Certificate.class);

    // when
    final var responderUrl = sut.extract(certificate);

    // then
    assertThat(responderUrl).isEmpty();
  }
}
