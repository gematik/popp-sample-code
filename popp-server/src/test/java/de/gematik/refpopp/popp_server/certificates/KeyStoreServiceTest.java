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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.exceptions.KeyStoreException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;

class KeyStoreServiceTest {

  private KeyStoreService sut;

  @Mock private ClassPathResource keyStoreResourceMock;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private KeyStore keyStoreMock;

  @Mock private ECPrivateKey privateKeyMock;

  @Mock private X509Certificate certificateMock;

  @Mock private X509Certificate issuerCertificateMock;

  private AutoCloseable closeable;

  @BeforeEach
  void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
    sut = new KeyStoreService(keyStoreMock, keyStoreMock);
  }

  @AfterEach
  void tearDown() throws Exception {
    closeable.close();
  }

  @Test
  void getPoppKeyStoreDataSuccess() throws Exception {
    // given
    final var keyStorePassword = "password";
    final var mockInputStream = new ByteArrayInputStream("dummy-data".getBytes());
    when(keyStoreResourceMock.getInputStream()).thenReturn(mockInputStream);
    when(keyStoreResourceMock.getFilename()).thenReturn("test-keystore.p12");

    when(keyStoreMock.getKey("test-keystore", keyStorePassword.toCharArray()))
        .thenReturn(privateKeyMock);
    when(keyStoreMock.getCertificateChain("test-keystore"))
        .thenReturn(new X509Certificate[] {certificateMock, issuerCertificateMock});

    // when
    final var result = sut.getPoppKeyStoreData(keyStoreResourceMock, keyStorePassword);

    // then
    assertThat(result).isNotNull();
    assertThat(result.privateKey()).isSameAs(privateKeyMock);
    assertThat(result.certificate()).isSameAs(certificateMock);
    assertThat(result.certificateChain()).containsExactly(certificateMock, issuerCertificateMock);
    assertThat(result.issuerCertificate()).isEmpty();
    verify(keyStoreMock).getCertificateChain("test-keystore");
  }

  @Test
  void getPoppKeyStoreDataThrowsExceptionWhenPasswordIsNull() {
    // given
    final String keyStorePassword = null;
    final var keyStoreResource = new ClassPathResource("path");

    // when and then
    assertThatThrownBy(() -> sut.getPoppKeyStoreData(keyStoreResource, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("password is null");
    verifyNoInteractions(keyStoreMock);
  }

  @Test
  void getPoppKeyStoreDataThrowsKeyStoreExceptionWhenFileNameIsNull() throws IOException {
    // given
    final var keyStorePassword = "password";
    final var mockInputStream = new ByteArrayInputStream("dummy-data".getBytes());
    when(keyStoreResourceMock.getInputStream()).thenReturn(mockInputStream);
    when(keyStoreResourceMock.getFilename()).thenReturn(null);
    final var classPathResourceMock = mock(ClassPathResource.class);

    // when and then
    assertThatThrownBy(() -> sut.getPoppKeyStoreData(classPathResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("Failed to get filename from keystore");
    verifyNoInteractions(keyStoreMock);
  }

  @Test
  void getPoppKeyStoreDataThrowsExceptionWhenCertificateNotFound() throws Exception {
    // given
    final var keyStorePassword = "password";
    final var mockInputStream = new ByteArrayInputStream("dummy-data".getBytes());
    when(keyStoreResourceMock.getInputStream()).thenReturn(mockInputStream);
    when(keyStoreResourceMock.getFilename()).thenReturn("test-keystore.p12");
    when(keyStoreMock.getKey("test-keystore", keyStorePassword.toCharArray()))
        .thenReturn(privateKeyMock);
    when(keyStoreMock.getCertificateChain("test-keystore")).thenReturn(null);

    // when and then
    assertThatThrownBy(() -> sut.getPoppKeyStoreData(keyStoreResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("No certificate found under alias 'test-keystore'");
  }

  @Test
  void getPoppKeyStoreDataThrowsExceptionWhenCertificateChainIsEmpty() throws Exception {
    // given
    final var keyStorePassword = "password";
    when(keyStoreResourceMock.getFilename()).thenReturn("test-keystore.p12");
    when(keyStoreMock.getKey("test-keystore", keyStorePassword.toCharArray()))
        .thenReturn(privateKeyMock);
    when(keyStoreMock.getCertificateChain("test-keystore")).thenReturn(new X509Certificate[0]);

    // when and then
    assertThatThrownBy(() -> sut.getPoppKeyStoreData(keyStoreResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("No certificate found under alias 'test-keystore'");
  }

  @Test
  void getPoppKeyStoreDataThrowsExceptionWhenCertificateChainContainsNonX509Certificate()
      throws Exception {
    // given
    final var keyStorePassword = "password";
    final var nonX509Certificate = mock(java.security.cert.Certificate.class);
    when(keyStoreResourceMock.getFilename()).thenReturn("test-keystore.p12");
    when(keyStoreMock.getKey("test-keystore", keyStorePassword.toCharArray()))
        .thenReturn(privateKeyMock);
    when(keyStoreMock.getCertificateChain("test-keystore"))
        .thenReturn(new java.security.cert.Certificate[] {nonX509Certificate});

    // when and then
    assertThatThrownBy(() -> sut.getPoppKeyStoreData(keyStoreResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("contains a non-X.509 certificate");
  }

  @Test
  void getConnectorKeyStoreDataSuccess() throws Exception {
    // given
    final var keyStorePassword = "password";
    when(keyStoreResourceMock.getFilename()).thenReturn("connector-keystore.jks");

    when(keyStoreMock.getKey("connector-keystore", keyStorePassword.toCharArray()))
        .thenReturn(privateKeyMock);
    when(keyStoreMock.getCertificateChain("connector-keystore"))
        .thenReturn(new X509Certificate[] {certificateMock});
    when(keyStoreMock.getCertificate("issuer-ca")).thenReturn(issuerCertificateMock);

    // when
    final var result = sut.getConnectorKeyStoreData(keyStoreResourceMock, keyStorePassword);

    // then
    assertThat(result).isNotNull();
    assertThat(result.privateKey()).isSameAs(privateKeyMock);
    assertThat(result.certificate()).isSameAs(certificateMock);
    assertThat(result.certificateChain()).containsExactly(certificateMock);
    assertThat(result.issuerCertificate()).contains(issuerCertificateMock);
    verify(keyStoreMock).getCertificateChain("connector-keystore");
    verify(keyStoreMock).getCertificate("issuer-ca");
  }

  @Test
  void getConnectorKeyStoreDataLoadsIssuerCertificateFromConfiguredKeystore() {
    // given
    final var keyStoreResource =
        new ClassPathResource("certificates/signer/popp-Server-nist-komp61.jks");
    final var keyStorePassword = "gematik";
    final var keyStore = new KeyStoreLoader(keyStoreResource, keyStorePassword).load();
    sut = new KeyStoreService(keyStore, keyStore);

    // when
    final var result = sut.getConnectorKeyStoreData(keyStoreResource, keyStorePassword);

    // then
    assertThat(result.issuerCertificate()).isPresent();
  }

  @Test
  void getConnectorKeyStoreDataThrowsExceptionWhenIssuerCertificateIsNotFound() throws Exception {
    // given
    final var keyStorePassword = "password";
    when(keyStoreResourceMock.getFilename()).thenReturn("connector-keystore.jks");
    when(keyStoreMock.getKey("connector-keystore", keyStorePassword.toCharArray()))
        .thenReturn(privateKeyMock);
    when(keyStoreMock.getCertificateChain("connector-keystore"))
        .thenReturn(new X509Certificate[] {certificateMock});
    when(keyStoreMock.getCertificate("issuer-ca")).thenReturn(null);

    // when and then
    assertThatThrownBy(() -> sut.getConnectorKeyStoreData(keyStoreResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("No certificate found under alias 'issuer-ca'");
  }

  @Test
  void getConnectorKeyStoreDataThrowsExceptionWhenIssuerCertificateIsNotX509() throws Exception {
    // given
    final var keyStorePassword = "password";
    final var nonX509Certificate = mock(java.security.cert.Certificate.class);
    when(keyStoreResourceMock.getFilename()).thenReturn("connector-keystore.jks");
    when(keyStoreMock.getKey("connector-keystore", keyStorePassword.toCharArray()))
        .thenReturn(privateKeyMock);
    when(keyStoreMock.getCertificateChain("connector-keystore"))
        .thenReturn(new X509Certificate[] {certificateMock});
    when(keyStoreMock.getCertificate("issuer-ca")).thenReturn(nonX509Certificate);

    // when and then
    assertThatThrownBy(() -> sut.getConnectorKeyStoreData(keyStoreResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("Certificate under alias 'issuer-ca' is not an X.509 certificate");
  }

  @Test
  void getConnectorKeyStoreDataThrowsExceptionWhenLoadingIssuerCertificateFails() throws Exception {
    // given
    final var keyStorePassword = "password";
    when(keyStoreResourceMock.getFilename()).thenReturn("connector-keystore.jks");
    when(keyStoreMock.getKey("connector-keystore", keyStorePassword.toCharArray()))
        .thenReturn(privateKeyMock);
    when(keyStoreMock.getCertificateChain("connector-keystore"))
        .thenReturn(new X509Certificate[] {certificateMock});
    when(keyStoreMock.getCertificate("issuer-ca"))
        .thenThrow(new java.security.KeyStoreException("Issuer retrieval failed"));

    // when and then
    assertThatThrownBy(() -> sut.getConnectorKeyStoreData(keyStoreResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("Failed to load certificate under alias 'issuer-ca'")
        .hasMessageContaining("Issuer retrieval failed");
  }

  @Test
  void getConnectorKeyStoreDataThrowsExceptionWhenPasswordIsNull() {
    // given
    final String keyStorePassword = null;
    final var keyStoreResource = new ClassPathResource("path");

    // when and then
    assertThatThrownBy(() -> sut.getConnectorKeyStoreData(keyStoreResource, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("password is null");
    verifyNoInteractions(keyStoreMock);
  }

  @Test
  void getConnectorKeyStoreDataThrowsExceptionWhenCertificateNotFound() throws Exception {
    // given
    final var keyStorePassword = "password";
    when(keyStoreResourceMock.getFilename()).thenReturn("connector-keystore.jks");

    when(keyStoreMock.getKey("connector-keystore", keyStorePassword.toCharArray()))
        .thenReturn(privateKeyMock);
    when(keyStoreMock.getCertificateChain("connector-keystore")).thenReturn(null);

    // when and then
    assertThatThrownBy(() -> sut.getConnectorKeyStoreData(keyStoreResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("No certificate found under alias 'connector-keystore'");
  }

  @Test
  void getPoppKeyStoreDataThrowsExceptionWhenKeyNotFound() throws Exception {
    // given
    final var keyStorePassword = "password";
    when(keyStoreResourceMock.getFilename()).thenReturn("test-keystore.p12");

    when(keyStoreMock.getKey("test-keystore", keyStorePassword.toCharArray())).thenReturn(null);

    // when and then
    assertThatThrownBy(() -> sut.getPoppKeyStoreData(keyStoreResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("No key found under alias 'test-keystore'");
  }

  @Test
  void getConnectorKeyStoreDataThrowsExceptionWhenKeyNotFound() throws Exception {
    // given
    final var keyStorePassword = "password";
    when(keyStoreResourceMock.getFilename()).thenReturn("connector-keystore.jks");

    when(keyStoreMock.getKey("connector-keystore", keyStorePassword.toCharArray()))
        .thenReturn(null);

    // when and then
    assertThatThrownBy(() -> sut.getConnectorKeyStoreData(keyStoreResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("No key found under alias 'connector-keystore'");
  }

  @Test
  void getPoppKeyStoreDataThrowsExceptionWhenKeyStoreOperationFails() throws Exception {
    // given
    final var keyStorePassword = "password";
    when(keyStoreResourceMock.getFilename()).thenReturn("test-keystore.p12");

    when(keyStoreMock.getKey("test-keystore", keyStorePassword.toCharArray()))
        .thenThrow(new java.security.KeyStoreException("KeyStore operation failed"));

    // when and then
    assertThatThrownBy(() -> sut.getPoppKeyStoreData(keyStoreResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("Failed to load keystore data");
  }

  @Test
  void getConnectorKeyStoreDataThrowsExceptionWhenKeyStoreOperationFails() throws Exception {
    // given
    final var keyStorePassword = "password";
    when(keyStoreResourceMock.getFilename()).thenReturn("connector-keystore.jks");

    when(keyStoreMock.getKey("connector-keystore", keyStorePassword.toCharArray()))
        .thenThrow(new java.security.KeyStoreException("KeyStore operation failed"));

    // when and then
    assertThatThrownBy(() -> sut.getConnectorKeyStoreData(keyStoreResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("Failed to load keystore data");
  }

  @Test
  void getPoppKeyStoreDataHandlesFilenameWithMultipleDots() throws Exception {
    // given - filename with multiple dots
    final var keyStorePassword = "password";
    when(keyStoreResourceMock.getFilename()).thenReturn("my.test.keystore.p12");

    when(keyStoreMock.getKey("my.test.keystore", keyStorePassword.toCharArray()))
        .thenReturn(privateKeyMock);
    when(keyStoreMock.getCertificateChain("my.test.keystore"))
        .thenReturn(new X509Certificate[] {certificateMock});

    // when
    final var result = sut.getPoppKeyStoreData(keyStoreResourceMock, keyStorePassword);

    // then
    assertThat(result).isNotNull();
    assertThat(result.privateKey()).isSameAs(privateKeyMock);
    verify(keyStoreMock).getKey("my.test.keystore", keyStorePassword.toCharArray());
  }

  @Test
  void getConnectorKeyStoreDataHandlesFilenameWithMultipleDots() throws Exception {
    // given - filename with multiple dots
    final var keyStorePassword = "password";
    when(keyStoreResourceMock.getFilename()).thenReturn("connector.prod.keystore.jks");

    when(keyStoreMock.getKey("connector.prod.keystore", keyStorePassword.toCharArray()))
        .thenReturn(privateKeyMock);
    when(keyStoreMock.getCertificateChain("connector.prod.keystore"))
        .thenReturn(new X509Certificate[] {certificateMock});
    when(keyStoreMock.getCertificate("issuer-ca")).thenReturn(issuerCertificateMock);

    // when
    final var result = sut.getConnectorKeyStoreData(keyStoreResourceMock, keyStorePassword);

    // then
    assertThat(result).isNotNull();
    assertThat(result.certificate()).isSameAs(certificateMock);
    assertThat(result.issuerCertificate()).contains(issuerCertificateMock);
    verify(keyStoreMock).getKey("connector.prod.keystore", keyStorePassword.toCharArray());
  }

  @Test
  void getPoppKeyStoreDataThrowsExceptionWhenKeyStoreExceptionIsThrownFromGetCertificateChain()
      throws Exception {
    // given
    final var keyStorePassword = "password";
    when(keyStoreResourceMock.getFilename()).thenReturn("test-keystore.p12");

    when(keyStoreMock.getKey("test-keystore", keyStorePassword.toCharArray()))
        .thenReturn(privateKeyMock);
    when(keyStoreMock.getCertificateChain("test-keystore"))
        .thenThrow(new java.security.KeyStoreException("Certificate retrieval failed"));

    // when and then
    assertThatThrownBy(() -> sut.getPoppKeyStoreData(keyStoreResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("Failed to load keystore data");
  }

  @Test
  void getConnectorKeyStoreDataThrowsExceptionWhenKeyStoreExceptionIsThrownFromGetCertificateChain()
      throws Exception {
    // given
    final var keyStorePassword = "password";
    when(keyStoreResourceMock.getFilename()).thenReturn("connector-keystore.jks");

    when(keyStoreMock.getKey("connector-keystore", keyStorePassword.toCharArray()))
        .thenReturn(privateKeyMock);
    when(keyStoreMock.getCertificateChain("connector-keystore"))
        .thenThrow(new java.security.KeyStoreException("Certificate retrieval failed"));

    // when and then
    assertThatThrownBy(() -> sut.getConnectorKeyStoreData(keyStoreResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("Failed to load keystore data");
  }
}
