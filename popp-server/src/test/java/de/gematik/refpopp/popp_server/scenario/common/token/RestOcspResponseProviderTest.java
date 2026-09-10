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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.pki.gemlibpki.commons.exception.GemPkiException;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class RestOcspResponseProviderTest {

  @Test
  void getResponseRetrievesAndEncodesBinaryOcspResponse() throws Exception {
    // given
    final var responderUrlExtractor = mock(OcspResponderUrlExtractor.class);
    final var ocspTransceiverClient = mock(OcspTransceiverClient.class);
    final var endEntityCertificate = mock(X509Certificate.class);
    final var issuerCertificate = mock(X509Certificate.class);
    final var request =
        OcspRequest.withIssuer("test-session", endEntityCertificate, issuerCertificate);
    final var ocspResponse = mock(OCSPResp.class);
    final var sut = createSut(responderUrlExtractor, ocspTransceiverClient);
    final var responderUrl = "http://ehca.gematik.de/ecc-ocsp";
    when(responderUrlExtractor.extract(endEntityCertificate)).thenReturn(Optional.of(responderUrl));
    when(ocspTransceiverClient.getOcspResponse(
            endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10))
        .thenReturn(Optional.of(ocspResponse));
    when(ocspTransceiverClient.getEncoded(ocspResponse)).thenReturn(new byte[] {1, 2, 3});

    // when
    final var response = sut.getResponse(request);

    // then
    assertThat(response).isEqualTo("AQID");
    verify(ocspTransceiverClient)
        .getOcspResponse(endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10);
  }

  @Test
  void getResponseReusesOcspResponseWithinSession() throws Exception {
    // given
    final var responderUrlExtractor = mock(OcspResponderUrlExtractor.class);
    final var ocspTransceiverClient = mock(OcspTransceiverClient.class);
    final var endEntityCertificate = mock(X509Certificate.class);
    final var issuerCertificate = mock(X509Certificate.class);
    final var request =
        OcspRequest.withIssuer("test-session", endEntityCertificate, issuerCertificate);
    final var ocspResponse = mock(OCSPResp.class);
    final var sut = createSut(responderUrlExtractor, ocspTransceiverClient);
    final var responderUrl = "http://ehca.gematik.de/ecc-ocsp";
    when(responderUrlExtractor.extract(endEntityCertificate)).thenReturn(Optional.of(responderUrl));
    when(ocspTransceiverClient.getOcspResponse(
            endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10))
        .thenReturn(Optional.of(ocspResponse));
    when(ocspTransceiverClient.getEncoded(ocspResponse)).thenReturn(new byte[] {1, 2, 3});

    // when
    final var firstResponse = sut.getResponse(request);
    final var secondResponse = sut.getResponse(request);

    // then
    assertThat(secondResponse).isEqualTo(firstResponse);
    verify(ocspTransceiverClient)
        .getOcspResponse(endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10);
  }

  @Test
  void getResponseDoesNotReuseOcspResponseAcrossSessions() throws Exception {
    // given
    final var responderUrlExtractor = mock(OcspResponderUrlExtractor.class);
    final var ocspTransceiverClient = mock(OcspTransceiverClient.class);
    final var endEntityCertificate = mock(X509Certificate.class);
    final var issuerCertificate = mock(X509Certificate.class);
    final var ocspResponse = mock(OCSPResp.class);
    final var sut = createSut(responderUrlExtractor, ocspTransceiverClient);
    final var responderUrl = "http://ehca.gematik.de/ecc-ocsp";
    when(responderUrlExtractor.extract(endEntityCertificate)).thenReturn(Optional.of(responderUrl));
    when(ocspTransceiverClient.getOcspResponse(
            endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10))
        .thenReturn(Optional.of(ocspResponse));
    when(ocspTransceiverClient.getEncoded(ocspResponse)).thenReturn(new byte[] {1, 2, 3});

    // when
    sut.getResponse(
        OcspRequest.withIssuer("first-session", endEntityCertificate, issuerCertificate));
    sut.getResponse(
        OcspRequest.withIssuer("second-session", endEntityCertificate, issuerCertificate));

    // then
    verify(ocspTransceiverClient, times(2))
        .getOcspResponse(endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10);
  }

  @Test
  void getResponseThrowsScenarioExceptionWhenIssuerCertificateIsMissing() {
    // given
    final var responderUrlExtractor = mock(OcspResponderUrlExtractor.class);
    final var ocspTransceiverClient = mock(OcspTransceiverClient.class);
    final var sut = createSut(responderUrlExtractor, ocspTransceiverClient);
    final var request = OcspRequest.withoutIssuer("test-session", mock(X509Certificate.class));

    // when & then
    final var exception = assertThrows(ScenarioException.class, () -> sut.getResponse(request));
    assertThat(exception.getMessage()).isEqualTo("Missing issuer certificate");
    verifyNoInteractions(responderUrlExtractor, ocspTransceiverClient);
  }

  @Test
  void getResponseThrowsScenarioExceptionWhenCertificateHasNoOcspResponderUrl() throws Exception {
    // given
    final var responderUrlExtractor = mock(OcspResponderUrlExtractor.class);
    final var ocspTransceiverClient = mock(OcspTransceiverClient.class);
    final var endEntityCertificate = mock(X509Certificate.class);
    final var request =
        OcspRequest.withIssuer("test-session", endEntityCertificate, mock(X509Certificate.class));
    final var sut = createSut(responderUrlExtractor, ocspTransceiverClient);
    when(responderUrlExtractor.extract(endEntityCertificate)).thenReturn(Optional.empty());

    // when & then
    final var exception = assertThrows(ScenarioException.class, () -> sut.getResponse(request));
    assertThat(exception.getMessage()).isEqualTo("Missing OCSP responder URL in certificate");
    verifyNoInteractions(ocspTransceiverClient);
  }

  @Test
  void getResponseThrowsScenarioExceptionWhenTransceiverReturnsNoResponse() throws Exception {
    // given
    final var responderUrlExtractor = mock(OcspResponderUrlExtractor.class);
    final var ocspTransceiverClient = mock(OcspTransceiverClient.class);
    final var endEntityCertificate = mock(X509Certificate.class);
    final var issuerCertificate = mock(X509Certificate.class);
    final var request =
        OcspRequest.withIssuer("test-session", endEntityCertificate, issuerCertificate);
    final var sut = createSut(responderUrlExtractor, ocspTransceiverClient);
    when(responderUrlExtractor.extract(endEntityCertificate))
        .thenReturn(Optional.of("http://ehca.gematik.de/ecc-ocsp"));
    when(ocspTransceiverClient.getOcspResponse(
            endEntityCertificate,
            issuerCertificate,
            "http://ehca.gematik.de/ecc-ocsp",
            "popp-server",
            10))
        .thenReturn(Optional.empty());

    // when & then
    final var exception = assertThrows(ScenarioException.class, () -> sut.getResponse(request));
    assertThat(exception.getMessage()).isEqualTo("Received empty OCSP response");
  }

  @Test
  void getResponseThrowsScenarioExceptionWhenResponseCannotBeEncoded() throws Exception {
    // given
    final var responderUrlExtractor = mock(OcspResponderUrlExtractor.class);
    final var ocspTransceiverClient = mock(OcspTransceiverClient.class);
    final var endEntityCertificate = mock(X509Certificate.class);
    final var issuerCertificate = mock(X509Certificate.class);
    final var ocspResponse = mock(OCSPResp.class);
    final var request =
        OcspRequest.withIssuer("test-session", endEntityCertificate, issuerCertificate);
    final var sut = createSut(responderUrlExtractor, ocspTransceiverClient);
    final var responderUrl = "http://ehca.gematik.de/ecc-ocsp";
    when(responderUrlExtractor.extract(endEntityCertificate)).thenReturn(Optional.of(responderUrl));
    when(ocspTransceiverClient.getOcspResponse(
            endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10))
        .thenReturn(Optional.of(ocspResponse));
    when(ocspTransceiverClient.getEncoded(ocspResponse)).thenThrow(new IOException("test"));

    // when & then
    final var exception = assertThrows(ScenarioException.class, () -> sut.getResponse(request));
    assertThat(exception.getMessage()).isEqualTo("Could not encode OCSP response");
  }

  @Test
  void getResponseThrowsScenarioExceptionWhenTransceiverFails() throws Exception {
    // given
    final var responderUrlExtractor = mock(OcspResponderUrlExtractor.class);
    final var ocspTransceiverClient = mock(OcspTransceiverClient.class);
    final var endEntityCertificate = mock(X509Certificate.class);
    final var issuerCertificate = mock(X509Certificate.class);
    final var fallbackProvider = mock(OcspResponseProvider.class);
    final var request =
        OcspRequest.withIssuer("test-session", endEntityCertificate, issuerCertificate);
    final var sut =
        new RestOcspResponseProvider(
            responderUrlExtractor, ocspTransceiverClient, new SessionContainer(), 10, null);
    final var responderUrl = "http://ehca.gematik.de/ecc-ocsp";
    when(responderUrlExtractor.extract(endEntityCertificate)).thenReturn(Optional.of(responderUrl));
    when(ocspTransceiverClient.getOcspResponse(
            endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10))
        .thenThrow(mock(GemPkiException.class));

    // when & then
    final var exception = assertThrows(ScenarioException.class, () -> sut.getResponse(request));
    assertThat(exception.getMessage()).isEqualTo("OCSP response provider is not reachable");
    verify(ocspTransceiverClient, times(2))
        .getOcspResponse(endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10);
    verifyNoInteractions(fallbackProvider);
  }

  @Test
  void getResponseRetriesOnceWhenFirstRequestFails() throws Exception {
    // given
    final var responderUrlExtractor = mock(OcspResponderUrlExtractor.class);
    final var ocspTransceiverClient = mock(OcspTransceiverClient.class);
    final var fallbackProvider = mock(OcspResponseProvider.class);
    final var fallbackProviderCreations = new AtomicInteger();
    final var endEntityCertificate = mock(X509Certificate.class);
    final var issuerCertificate = mock(X509Certificate.class);
    final var request =
        OcspRequest.withIssuer("test-session", endEntityCertificate, issuerCertificate);
    final var ocspResponse = mock(OCSPResp.class);
    final var sut =
        new RestOcspResponseProvider(
            responderUrlExtractor,
            ocspTransceiverClient,
            new SessionContainer(),
            10,
            () -> {
              fallbackProviderCreations.incrementAndGet();
              return fallbackProvider;
            });
    final var responderUrl = "http://ehca.gematik.de/ecc-ocsp";
    when(responderUrlExtractor.extract(endEntityCertificate)).thenReturn(Optional.of(responderUrl));
    when(ocspTransceiverClient.getOcspResponse(
            endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10))
        .thenThrow(mock(GemPkiException.class))
        .thenReturn(Optional.of(ocspResponse));
    when(ocspTransceiverClient.getEncoded(ocspResponse)).thenReturn(new byte[] {1, 2, 3});

    // when
    final var response = sut.getResponse(request);

    // then
    assertThat(response).isEqualTo("AQID");
    assertThat(fallbackProviderCreations).hasValue(0);
    verify(ocspTransceiverClient, times(2))
        .getOcspResponse(endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10);
    verifyNoInteractions(fallbackProvider);
  }

  @Test
  void getResponseUsesClasspathFallbackAfterTwoFailedRequestsWhenEnabled() throws Exception {
    // given
    final var responderUrlExtractor = mock(OcspResponderUrlExtractor.class);
    final var ocspTransceiverClient = mock(OcspTransceiverClient.class);
    final var fallbackProvider = mock(OcspResponseProvider.class);
    final var fallbackProviderCreations = new AtomicInteger();
    final var endEntityCertificate = mock(X509Certificate.class);
    final var issuerCertificate = mock(X509Certificate.class);
    final var request =
        OcspRequest.withIssuer("test-session", endEntityCertificate, issuerCertificate);
    final var sut =
        new RestOcspResponseProvider(
            responderUrlExtractor,
            ocspTransceiverClient,
            new SessionContainer(),
            10,
            () -> {
              fallbackProviderCreations.incrementAndGet();
              return fallbackProvider;
            });
    final var responderUrl = "http://ehca.gematik.de/ecc-ocsp";
    when(responderUrlExtractor.extract(endEntityCertificate)).thenReturn(Optional.of(responderUrl));
    when(ocspTransceiverClient.getOcspResponse(
            endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10))
        .thenThrow(mock(GemPkiException.class));
    when(fallbackProvider.getResponse(request)).thenReturn("fallback-response");

    // when
    final var response = sut.getResponse(request);

    // then
    assertThat(response).isEqualTo("fallback-response");
    assertThat(fallbackProviderCreations).hasValue(1);
    verify(ocspTransceiverClient, times(2))
        .getOcspResponse(endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10);
    verify(fallbackProvider).getResponse(request);
  }

  @Test
  void getResponseUsesConfiguredClasspathFallbackAfterTwoFailedRequests() throws Exception {
    // given
    final var responderUrlExtractor = mock(OcspResponderUrlExtractor.class);
    final var ocspTransceiverClient = mock(OcspTransceiverClient.class);
    final var endEntityCertificate = mock(X509Certificate.class);
    final var issuerCertificate = mock(X509Certificate.class);
    final var request =
        OcspRequest.withIssuer("test-session", endEntityCertificate, issuerCertificate);
    final var sut =
        new RestOcspResponseProvider(
            responderUrlExtractor,
            ocspTransceiverClient,
            new SessionContainer(),
            10,
            () -> new ClasspathOcspResponseProvider(new ClassPathResource("ocsp-response.txt")));
    final var responderUrl = "http://ehca.gematik.de/ecc-ocsp";
    when(responderUrlExtractor.extract(endEntityCertificate)).thenReturn(Optional.of(responderUrl));
    when(ocspTransceiverClient.getOcspResponse(
            endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10))
        .thenThrow(mock(GemPkiException.class));

    // when
    final var response = sut.getResponse(request);

    // then
    assertThat(response).isEqualTo("dmFsaWQ=");
    verify(ocspTransceiverClient, times(2))
        .getOcspResponse(endEntityCertificate, issuerCertificate, responderUrl, "popp-server", 10);
  }

  private RestOcspResponseProvider createSut(
      OcspResponderUrlExtractor responderUrlExtractor,
      OcspTransceiverClient ocspTransceiverClient) {
    return new RestOcspResponseProvider(
        responderUrlExtractor, ocspTransceiverClient, new SessionContainer(), 10, null);
  }
}
