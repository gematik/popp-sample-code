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
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.exceptions.KeyStoreException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.Resource;

class KeyStoreLoaderTest {

  private static final String PASSWORD = "testpassword";

  @Mock private Resource truststoreLocationMock;

  private AutoCloseable closeable;
  private KeyStoreLoader sut;

  @BeforeEach
  void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
    sut = new KeyStoreLoader(truststoreLocationMock, PASSWORD);
  }

  @AfterEach
  void tearDown() throws Exception {
    closeable.close();
  }

  @Test
  void loadReturnsKeyStoreWhenResourceAndPasswordAreValid() throws Exception {
    // given
    final var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, PASSWORD.toCharArray());
    final var outputStream = new ByteArrayOutputStream();
    keyStore.store(outputStream, PASSWORD.toCharArray());
    when(truststoreLocationMock.getInputStream())
        .thenReturn(new ByteArrayInputStream(outputStream.toByteArray()));

    // when
    final var result = sut.load();

    // then
    assertThat(result).isNotNull();
  }

  @Test
  void loadThrowsKeyStoreExceptionWhenInputStreamThrowsIOException() throws Exception {
    // given
    when(truststoreLocationMock.getInputStream()).thenThrow(new IOException("file not found"));

    // when and then
    assertThatThrownBy(() -> sut.load())
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("Could not load key store");
  }

  @Test
  void loadThrowsKeyStoreExceptionWhenKeyStoreDataIsInvalid() throws Exception {
    // given
    when(truststoreLocationMock.getInputStream())
        .thenReturn(new ByteArrayInputStream("not-a-keystore".getBytes()));

    // when and then
    assertThatThrownBy(() -> sut.load())
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("Could not load key store");
  }

  @Test
  void loadThrowsKeyStoreExceptionWhenPasswordIsWrong() throws Exception {
    // given
    final var correctPassword = "correctpassword";
    final var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, correctPassword.toCharArray());
    final var outputStream = new ByteArrayOutputStream();
    keyStore.store(outputStream, correctPassword.toCharArray());
    final var sutWithWrongPassword = new KeyStoreLoader(truststoreLocationMock, "wrongpassword");
    when(truststoreLocationMock.getInputStream())
        .thenReturn(new ByteArrayInputStream(outputStream.toByteArray()));

    // when and then
    assertThatThrownBy(sutWithWrongPassword::load)
        .isInstanceOf(KeyStoreException.class)
        .hasMessageContaining("Could not load key store");
  }
}
