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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

class CmsSignatureVerifierTest {

  private CmsSignatureVerifier sut;
  private Resource keystoreLocationMock;
  private CMSSignedDataParserFactory parserFactoryMock;
  private KeyStore keyStoreMock;
  private TrustedCertificateFinder trustedCertificateFinderMock;
  private SignerInfoVerifierBuilder signerInfoVerifierBuilderMock;

  @BeforeEach
  void setUp() throws KeyStoreException {
    keystoreLocationMock = mock(Resource.class);
    parserFactoryMock = mock(CMSSignedDataParserFactory.class);
    keyStoreMock = mock(KeyStore.class);
    final Enumeration aliasesMock = mock(Enumeration.class);
    when(aliasesMock.hasMoreElements()).thenReturn(true, true, false);
    when(aliasesMock.nextElement()).thenReturn("alias1", "alias2");
    when(keyStoreMock.aliases()).thenReturn(aliasesMock);
    trustedCertificateFinderMock = mock(TrustedCertificateFinder.class);
    signerInfoVerifierBuilderMock = mock(SignerInfoVerifierBuilder.class);
    sut =
        new CmsSignatureVerifier(
            keyStoreMock,
            parserFactoryMock,
            trustedCertificateFinderMock,
            signerInfoVerifierBuilderMock);
  }

  @Test
  void shouldReturnTrueWhenSignatureIsValid() throws Exception {
    final InputStream cmsStream = mock(InputStream.class);
    final var parserMock = mock(CMSSignedDataParser.class, RETURNS_DEEP_STUBS);
    final var signerInfoMock = mock(SignerInformation.class);
    final Enumeration aliasesMock = mock(Enumeration.class);

    when(keystoreLocationMock.getInputStream()).thenReturn(mock(InputStream.class));
    when(keyStoreMock.aliases()).thenReturn(aliasesMock);
    when(aliasesMock.hasMoreElements()).thenReturn(true, true, false);
    when(aliasesMock.nextElement()).thenReturn("alias1", "alias2");
    when(keyStoreMock.aliases()).thenReturn(aliasesMock);
    when(keyStoreMock.getCertificate("alias1")).thenReturn(mock(X509Certificate.class));
    when(keyStoreMock.getCertificate("alias2")).thenReturn(mock(X509Certificate.class));
    when(parserFactoryMock.createParser(cmsStream, "testSession")).thenReturn(parserMock);
    final Set<SignerInformation> signers = new HashSet();
    signers.add(signerInfoMock);
    when(parserMock.getSignerInfos().getSigners()).thenReturn(signers);
    when(signerInfoMock.verify(any())).thenReturn(true);
    when(signerInfoMock.getSID()).thenReturn(mock(SignerId.class));
    when(signerInfoMock.verify(any())).thenReturn(true);
    when(trustedCertificateFinderMock.findTrustedCertificate(any(), any()))
        .thenReturn(mock(X509Certificate.class));

    final boolean result = sut.isSignatureValid(cmsStream, "testSession");

    assertThat(result).isTrue();
    verify(parserMock).getSignedContent();
    verify(signerInfoMock).verify(any());
  }

  @Test
  void shouldReturnFalseWhenNoTrustedCertificateFound() throws Exception {
    final InputStream cmsStream = mock(InputStream.class);
    final var parserMock = mock(CMSSignedDataParser.class, RETURNS_DEEP_STUBS);
    final var signerInfoMock = mock(SignerInformation.class);
    final Enumeration aliasesMock = mock(Enumeration.class);

    when(keystoreLocationMock.getInputStream()).thenReturn(mock(InputStream.class));
    when(keyStoreMock.aliases()).thenReturn(aliasesMock);
    when(aliasesMock.hasMoreElements()).thenReturn(true, true, false);
    when(aliasesMock.nextElement()).thenReturn("alias1", "alias2");
    when(keyStoreMock.aliases()).thenReturn(aliasesMock);
    when(keyStoreMock.getCertificate("alias1")).thenReturn(mock(X509Certificate.class));
    when(keyStoreMock.getCertificate("alias2")).thenReturn(mock(X509Certificate.class));
    when(parserFactoryMock.createParser(cmsStream, "testSession")).thenReturn(parserMock);
    when(parserMock.getSignerInfos().getSigners()).thenReturn(Set.of(signerInfoMock));
    when(signerInfoMock.getSID()).thenReturn(mock(SignerId.class));
    when(signerInfoMock.verify(any())).thenReturn(true);
    when(keyStoreMock.getCertificate(anyString())).thenReturn(mock(X509Certificate.class));

    final boolean result = sut.isSignatureValid(cmsStream, "testSession");

    assertThat(result).isFalse();
    verify(parserMock).getSignedContent();
  }

  @Test
  void shouldReturnFalseWhenSignatureIsInvalid() throws Exception {
    final InputStream cmsStream = mock(InputStream.class);
    final var parserMock = mock(CMSSignedDataParser.class, RETURNS_DEEP_STUBS);
    final var signerInfoMock = mock(SignerInformation.class);

    when(signerInfoMock.verify(any())).thenReturn(false);
    when(parserFactoryMock.createParser(cmsStream, "testSession")).thenReturn(parserMock);
    when(parserMock.getSignerInfos().getSigners()).thenReturn(Set.of(signerInfoMock));
    when(signerInfoMock.getSID()).thenReturn(mock(SignerId.class));

    final boolean result = sut.isSignatureValid(cmsStream, "testSession");

    assertThat(result).isFalse();
    verify(parserMock).getSignedContent();
  }

  @Test
  void shouldThrowImportDataExceptionWhenErrorOccurs() {
    final InputStream cmsStream = mock(InputStream.class);

    when(parserFactoryMock.createParser(cmsStream, "testSession"))
        .thenThrow(RuntimeException.class);

    assertThatThrownBy(() -> sut.isSignatureValid(cmsStream, "testSession"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("CMS verification failed: ");
  }
}
