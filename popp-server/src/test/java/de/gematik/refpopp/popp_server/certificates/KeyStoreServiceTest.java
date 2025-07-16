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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;

class KeyStoreServiceTest {

  @InjectMocks private KeyStoreService sut;

  @Mock private ClassPathResource keyStoreResourceMock;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private KeyStore keyStoreMock;

  @Mock private ECPrivateKey privateKeyMock;

  @Mock private X509Certificate certificateMock;

  @Mock private EcPrivateKeyFactory ecPrivateKeyFactoryMock;

  private AutoCloseable closeable;

  @BeforeEach
  void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
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
    when(keyStoreMock.getCertificate("test-keystore")).thenReturn(certificateMock);

    // when
    final var result = sut.getPoppKeyStoreData(keyStoreResourceMock, keyStorePassword);

    // then
    assertThat(result).isNotNull();
    verify(ecPrivateKeyFactoryMock).create(privateKeyMock);
    verify(keyStoreMock).getCertificate("test-keystore");
    verify(ecPrivateKeyFactoryMock).create(privateKeyMock);
  }

  @Test
  void getPoppKeyStoreDataThrowsExceptionWhenPasswordIsNull() {
    // given
    final String keyStorePath = "path";
    final String keyStorePassword = null;

    // when and then
    assertThatThrownBy(
            () -> sut.getPoppKeyStoreData(new ClassPathResource(keyStorePath), keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("password is null");
    verifyNoInteractions(ecPrivateKeyFactoryMock, keyStoreMock);
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
    verifyNoInteractions(ecPrivateKeyFactoryMock);
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
    when(keyStoreMock.getCertificate("test-keystore")).thenReturn(null);

    // when and then
    assertThatThrownBy(() -> sut.getPoppKeyStoreData(keyStoreResourceMock, keyStorePassword))
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("No certificate found under alias 'test-keystore'");
  }
}
