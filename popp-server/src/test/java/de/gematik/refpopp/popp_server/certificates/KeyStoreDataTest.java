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

package de.gematik.refpopp.popp_server.certificates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeyStoreDataTest {

  @Test
  void withoutIssuerCopiesCertificateChain() {
    // given
    final var privateKey = mock(ECPrivateKey.class);
    final var certificate = mock(X509Certificate.class);
    final var certificateChain = new ArrayList<>(List.of(certificate));

    // when
    final var result = KeyStoreData.withoutIssuer(privateKey, certificateChain);
    certificateChain.clear();

    // then
    assertThat(result.privateKey()).isSameAs(privateKey);
    assertThat(result.certificate()).isSameAs(certificate);
    assertThat(result.certificateChain()).containsExactly(certificate);
    assertThat(result.issuerCertificate()).isEmpty();
  }

  @Test
  void withIssuerExposesIssuerCertificate() {
    // given
    final var privateKey = mock(ECPrivateKey.class);
    final var certificate = mock(X509Certificate.class);
    final var issuerCertificate = mock(X509Certificate.class);

    // when
    final var result = KeyStoreData.withIssuer(privateKey, List.of(certificate), issuerCertificate);

    // then
    assertThat(result.privateKey()).isSameAs(privateKey);
    assertThat(result.certificate()).isSameAs(certificate);
    assertThat(result.certificateChain()).containsExactly(certificate);
    assertThat(result.issuerCertificate()).contains(issuerCertificate);
  }

  @Test
  void certificateChainIsUnmodifiable() {
    // given
    final var privateKey = mock(ECPrivateKey.class);
    final var certificate = mock(X509Certificate.class);
    final var result = KeyStoreData.withoutIssuer(privateKey, List.of(certificate));

    // when / then
    assertThat(result.certificateChain()).isUnmodifiable();
  }

  @Test
  void equalsAndHashCodeUseAllKeyStoreDataFields() {
    // given
    final var privateKey = mock(ECPrivateKey.class);
    final var certificate = mock(X509Certificate.class);
    final var issuerCertificate = mock(X509Certificate.class);
    final var result = KeyStoreData.withIssuer(privateKey, List.of(certificate), issuerCertificate);
    final var equalResult =
        KeyStoreData.withIssuer(privateKey, List.of(certificate), issuerCertificate);
    final var resultWithDifferentIssuer =
        KeyStoreData.withIssuer(privateKey, List.of(certificate), mock(X509Certificate.class));
    final var resultWithDifferentPrivateKey =
        KeyStoreData.withIssuer(mock(ECPrivateKey.class), List.of(certificate), issuerCertificate);
    final var resultWithDifferentCertificate =
        KeyStoreData.withIssuer(
            privateKey, List.of(mock(X509Certificate.class)), issuerCertificate);

    // when / then
    assertEquals(equalResult, result);
    assertEquals(equalResult.hashCode(), result.hashCode());
    assertNotEquals(resultWithDifferentPrivateKey, result);
    assertNotEquals(resultWithDifferentCertificate, result);
    assertNotEquals(resultWithDifferentIssuer, result);
    assertNotNull(result);
  }

  @Test
  void factoryMethodsRejectNullPrivateKey() {
    // given
    final var certificate = mock(X509Certificate.class);
    final var certificateChain = List.of(certificate);

    // when / then
    assertThatThrownBy(() -> KeyStoreData.withoutIssuer(null, certificateChain))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("privateKey must not be null");
  }

  @Test
  void factoryMethodsRejectInvalidCertificateChains() {
    // given
    final var privateKey = mock(ECPrivateKey.class);
    final List<X509Certificate> emptyCertificateChain = List.of();

    // when / then
    assertThatThrownBy(() -> KeyStoreData.withoutIssuer(privateKey, emptyCertificateChain))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("certificateChain must not be empty");
    assertThatThrownBy(() -> KeyStoreData.withIssuer(privateKey, emptyCertificateChain, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("issuerCertificate must not be null");
  }
}
