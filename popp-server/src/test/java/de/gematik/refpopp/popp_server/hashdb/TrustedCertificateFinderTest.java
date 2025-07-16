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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Enumeration;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cms.SignerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrustedCertificateFinderTest {

  private TrustedCertificateFinder sut;
  private KeyStore mockKeyStore;
  private SignerId mockSignerId;

  @BeforeEach
  void setUp() {
    sut = new TrustedCertificateFinder();
    mockKeyStore = mock(KeyStore.class);
    mockSignerId = mock(SignerId.class);
  }

  @Test
  void findTrustedCertificateWithMatchingIssuerThenReturnsCertificate() throws Exception {
    // given
    final var issuerName = new X500Name("CN=Test");
    final BigInteger serial = BigInteger.valueOf(123);
    when(mockSignerId.getIssuer()).thenReturn(issuerName);
    when(mockSignerId.getSerialNumber()).thenReturn(serial);

    final var cert = mock(X509Certificate.class);
    when(cert.getIssuerX500Principal()).thenReturn(new X500Principal("CN=Test"));
    when(cert.getSerialNumber()).thenReturn(serial);

    final Enumeration<String> aliases =
        Collections.enumeration(Collections.singletonList("alias1"));
    when(mockKeyStore.aliases()).thenReturn(aliases);
    when(mockKeyStore.getCertificate("alias1")).thenReturn(cert);

    // when
    final X509Certificate result = sut.findTrustedCertificate(mockKeyStore, mockSignerId);

    // then
    assertThat(result).isSameAs(cert);
  }

  @Test
  void findTrustedCertificateWithEmptyAliasesThenReturnsNull() throws Exception {
    // given
    when(mockKeyStore.aliases()).thenReturn(Collections.emptyEnumeration());

    // when
    final X509Certificate result = sut.findTrustedCertificate(mockKeyStore, mockSignerId);

    // then
    assertThat(result).isNull();
  }

  @Test
  void findTrustedCertificateWithGivenKeyStoreThrowsReturnsNull() throws Exception {
    // given
    when(mockKeyStore.aliases()).thenThrow(new RuntimeException("KS error"));

    // when
    final X509Certificate result = sut.findTrustedCertificate(mockKeyStore, mockSignerId);

    // then
    assertThat(result).isNull();
  }
}
