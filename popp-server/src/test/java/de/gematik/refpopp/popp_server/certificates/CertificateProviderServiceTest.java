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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.smartcards.g2icc.cvc.TrustCenter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

class CertificateProviderServiceTest {

  private CertificateProviderService sut;
  private X509CertificateParser x509CertificateParserMock;
  private CVCertificateParser cvCertificateParserMock;
  private PrivateKeyParser privateKeyParserMock;
  private KeyStoreService keyStoreServiceMock;
  private ResourceLoader resourceLoaderMock;

  @BeforeEach
  void setUp() {
    x509CertificateParserMock = mock(X509CertificateParser.class);
    cvCertificateParserMock = mock(CVCertificateParser.class);
    privateKeyParserMock = mock(PrivateKeyParser.class);
    keyStoreServiceMock = mock(KeyStoreService.class);
    resourceLoaderMock = mock(ResourceLoader.class);
    sut =
        new CertificateProviderService(
            x509CertificateParserMock,
            cvCertificateParserMock,
            privateKeyParserMock,
            keyStoreServiceMock,
            resourceLoaderMock);
  }

  @Test
  void loadCertificatesThrowsExceptionWhenCreatingTempFolder() throws IOException {
    // given
    ReflectionTestUtils.setField(sut, "identitiesLocation", "identitiesLocation");
    ReflectionTestUtils.setField(
        sut, "rootCertificateResource", new ClassPathResource("rootCertificatePath"));
    ReflectionTestUtils.setField(
        sut, "cvcSubCertificateResource", new ClassPathResource("cvcSubCertificatePath"));
    ReflectionTestUtils.setField(
        sut,
        "cvcEndEntityCertificateResource",
        new ClassPathResource("cvcEndEntityCertificatePath"));
    ReflectionTestUtils.setField(
        sut, "connectorKeyStoreResource", new ClassPathResource("connectorKeyStorePath"));
    ReflectionTestUtils.setField(sut, "connectorKeyStorePassword", "connectorKeyStorePassword");
    ReflectionTestUtils.setField(
        sut, "poppKeyStoreResource", new ClassPathResource("poppTokenKeyStorePath"));
    ReflectionTestUtils.setField(sut, "poppKeyStorePassword", "poppKeyStorePassword");
    final var classPathResourceMock = mock(ClassPathResource.class);
    when(resourceLoaderMock.getResource(anyString())).thenReturn(classPathResourceMock);
    final var urlMock = mock(URL.class);
    when(classPathResourceMock.getURL()).thenReturn(urlMock);
    when(urlMock.getProtocol()).thenReturn("classpath");
    final var fileStoreMock = mock(java.nio.file.FileStore.class);
    try (final var filesMock = mockStatic(Files.class)) {
      filesMock.when(() -> Files.getFileStore(any())).thenReturn(fileStoreMock);
      filesMock
          .when(() -> Files.createTempDirectory(any(), anyString()))
          .thenThrow(IOException.class);
      // when / then
      assertThatThrownBy(() -> sut.loadCertificates())
          .isInstanceOf(ScenarioException.class)
          .hasMessageContaining("Unable to load PKI_CVC");
    }
  }

  @Test
  void loadCertificatesSuccessfully() throws IOException {
    // given
    ReflectionTestUtils.setField(sut, "identitiesLocation", "identitiesLocation");
    ReflectionTestUtils.setField(
        sut, "rootCertificateResource", new ClassPathResource("rootCertificatePath"));
    ReflectionTestUtils.setField(
        sut, "cvcSubCertificateResource", new ClassPathResource("cvcSubCertificatePath"));
    ReflectionTestUtils.setField(
        sut,
        "cvcEndEntityCertificateResource",
        new ClassPathResource("cvcEndEntityCertificatePath"));
    ReflectionTestUtils.setField(
        sut, "connectorKeyStoreResource", new ClassPathResource("connectorKeyStorePath"));
    ReflectionTestUtils.setField(sut, "connectorKeyStorePassword", "connectorKeyStorePassword");
    ReflectionTestUtils.setField(
        sut, "poppKeyStoreResource", new ClassPathResource("poppTokenKeyStorePath"));
    ReflectionTestUtils.setField(sut, "poppKeyStorePassword", "poppKeyStorePassword");
    final var classPathResourceMock = mock(ClassPathResource.class);
    when(resourceLoaderMock.getResource(anyString())).thenReturn(classPathResourceMock);
    final var urlMock = mock(URL.class);
    when(classPathResourceMock.getURL()).thenReturn(urlMock);
    when(urlMock.getProtocol()).thenReturn("classpath");

    try (final var trustCenterMock = mockStatic(TrustCenter.class)) {
      trustCenterMock
          .when(() -> TrustCenter.initializeCache(any()))
          .thenAnswer(invocationOnMock -> null);

      // when
      sut.loadCertificates();

      // then
      trustCenterMock.verify(() -> TrustCenter.initializeCache(any()));
      verify(x509CertificateParserMock).parse(new ClassPathResource("rootCertificatePath"));
      verify(cvCertificateParserMock).parse(new ClassPathResource("cvcSubCertificatePath"));
      verify(cvCertificateParserMock).parse(new ClassPathResource("cvcEndEntityCertificatePath"));
      verify(privateKeyParserMock).parse(any());
      verify(keyStoreServiceMock)
          .getPoppKeyStoreData(any(ClassPathResource.class), eq("poppKeyStorePassword"));
      verify(keyStoreServiceMock)
          .getConnectorKeyStoreData(any(ClassPathResource.class), eq("connectorKeyStorePassword"));
    }
  }

  @Test
  void loadCertificatesResolvesIdentitiesPathFromFileSystem() {
    // given
    ReflectionTestUtils.setField(sut, "identitiesLocation", "identitiesLocation");
    ReflectionTestUtils.setField(
        sut, "rootCertificateResource", new ClassPathResource("rootCertificatePath"));
    ReflectionTestUtils.setField(
        sut, "cvcSubCertificateResource", new ClassPathResource("cvcSubCertificatePath"));
    ReflectionTestUtils.setField(
        sut,
        "cvcEndEntityCertificateResource",
        new ClassPathResource("cvcEndEntityCertificatePath"));
    ReflectionTestUtils.setField(
        sut, "connectorKeyStoreResource", new ClassPathResource("connectorKeyStorePath"));
    ReflectionTestUtils.setField(sut, "connectorKeyStorePassword", "connectorKeyStorePassword");
    ReflectionTestUtils.setField(
        sut, "poppKeyStoreResource", new ClassPathResource("poppTokenKeyStorePath"));
    ReflectionTestUtils.setField(sut, "poppKeyStorePassword", "poppKeyStorePassword");

    when(resourceLoaderMock.getResource(anyString()))
        .thenReturn(new FileSystemResource("identitiesLocation"));

    try (final var trustCenterMock = mockStatic(TrustCenter.class)) {
      trustCenterMock
          .when(() -> TrustCenter.initializeCache(any()))
          .thenAnswer(invocationOnMock -> null);

      // when
      sut.loadCertificates();

      // then
      trustCenterMock.verify(() -> TrustCenter.initializeCache(any()));
      verify(x509CertificateParserMock).parse(new ClassPathResource("rootCertificatePath"));
      verify(cvCertificateParserMock).parse(new ClassPathResource("cvcSubCertificatePath"));
      verify(cvCertificateParserMock).parse(new ClassPathResource("cvcEndEntityCertificatePath"));
      verify(privateKeyParserMock).parse(any());
      verify(keyStoreServiceMock)
          .getPoppKeyStoreData(any(ClassPathResource.class), eq("poppKeyStorePassword"));
      verify(keyStoreServiceMock)
          .getConnectorKeyStoreData(any(ClassPathResource.class), eq("connectorKeyStorePassword"));
    }
  }
}
