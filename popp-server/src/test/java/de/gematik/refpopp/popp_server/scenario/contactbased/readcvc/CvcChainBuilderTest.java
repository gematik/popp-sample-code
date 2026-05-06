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

package de.gematik.refpopp.popp_server.scenario.contactbased.readcvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import de.gematik.openhealth.asn1.CvCertificate;
import de.gematik.refpopp.popp_server.certificates.CertificateProviderService;
import de.gematik.refpopp.popp_server.certificates.CvCertificateSupport;
import de.gematik.refpopp.popp_server.certificates.CvcDirectory;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CvcChainBuilderTest {

  private static final HexFormat HEX_FORMAT = HexFormat.of();

  private CvcChainBuilder sut;
  private CertificateProviderService certificateProviderServiceMock;
  private KeyIdentifierExtractor keyIdentifierExtractorMock;
  private CvcDirectory cvcDirectoryMock;

  @BeforeEach
  void setUp() {
    certificateProviderServiceMock = mock(CertificateProviderService.class);
    keyIdentifierExtractorMock = mock(KeyIdentifierExtractor.class);
    cvcDirectoryMock = mock(CvcDirectory.class);
    when(certificateProviderServiceMock.getCvcDirectory()).thenReturn(cvcDirectoryMock);
    sut =
        new CvcChainBuilder(
            certificateProviderServiceMock, new ScenarioResultFinder(), keyIdentifierExtractorMock);
  }

  @Test
  void buildCvcChainReturnsEmptyListWhenConfiguredServerCertificateIsKnownByCard() {
    final var sessionId = "sessionId";
    final var resultStep =
        new ScenarioResultStep(StepId.RETRIEVE_PUBLIC_KEY_IDENTIFIERS, "9000", "result".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep));
    final var serviceEndEntityCvc = mock(CvCertificate.class);
    final var trustedIssuerCvc = mock(CvCertificate.class);
    when(keyIdentifierExtractorMock.extract("result".getBytes()))
        .thenReturn(Set.of("736572766963652d656e64"));
    when(certificateProviderServiceMock.getCvEndEntityCertificate())
        .thenReturn(serviceEndEntityCvc);
    when(cvcDirectoryMock.all()).thenReturn(List.of(serviceEndEntityCvc, trustedIssuerCvc));

    try (final var cvcSupportMock = mockStatic(CvCertificateSupport.class)) {
      cvcSupportMock
          .when(() -> CvCertificateSupport.chr(serviceEndEntityCvc))
          .thenReturn("service-end");
      cvcSupportMock
          .when(() -> CvCertificateSupport.chrBytes(serviceEndEntityCvc))
          .thenReturn("service-end".getBytes(StandardCharsets.ISO_8859_1));

      final var result = sut.build(sessionId, scenarioResult);

      assertThat(result).isEmpty();
    }
  }

  @Test
  void buildCvcChainReturnsEmptyListWhenCardReportsConfiguredServerCertificateAsHex() {
    final var sessionId = "sessionId";
    final var resultStep =
        new ScenarioResultStep(StepId.RETRIEVE_PUBLIC_KEY_IDENTIFIERS, "9000", "result".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep));
    final var serviceEndEntityCvc = mock(CvCertificate.class);
    final var serviceChr = HEX_FORMAT.parseHex("000a80276001011699902101");
    when(keyIdentifierExtractorMock.extract("result".getBytes()))
        .thenReturn(Set.of("000a80276001011699902101"));
    when(certificateProviderServiceMock.getCvEndEntityCertificate())
        .thenReturn(serviceEndEntityCvc);

    try (final var cvcSupportMock = mockStatic(CvCertificateSupport.class)) {
      cvcSupportMock
          .when(() -> CvCertificateSupport.chr(serviceEndEntityCvc))
          .thenReturn(new String(serviceChr, StandardCharsets.ISO_8859_1));
      cvcSupportMock
          .when(() -> CvCertificateSupport.chrBytes(serviceEndEntityCvc))
          .thenReturn(serviceChr);

      final var result = sut.build(sessionId, scenarioResult);

      assertThat(result).isEmpty();
    }
  }

  @Test
  void buildTrimsConfiguredIssuerWhenItIsAlreadyKnownByCard() {
    final var sessionId = "sessionId";
    final var resultStep =
        new ScenarioResultStep(StepId.RETRIEVE_PUBLIC_KEY_IDENTIFIERS, "9000", "result".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep));
    final var serviceEndEntityCvc = mock(CvCertificate.class);
    final var serviceIssuerCvc = mock(CvCertificate.class);
    final var trustedRootCvc = mock(CvCertificate.class);
    when(keyIdentifierExtractorMock.extract("result".getBytes()))
        .thenReturn(Set.of("736572766963652d697373756572"));
    when(certificateProviderServiceMock.getCvEndEntityCertificate())
        .thenReturn(serviceEndEntityCvc);
    when(cvcDirectoryMock.all())
        .thenReturn(List.of(serviceEndEntityCvc, serviceIssuerCvc, trustedRootCvc));
    when(cvcDirectoryMock.findByChr("service-issuer")).thenReturn(Optional.of(serviceIssuerCvc));

    try (final var cvcSupportMock = mockStatic(CvCertificateSupport.class)) {
      cvcSupportMock
          .when(() -> CvCertificateSupport.chr(serviceEndEntityCvc))
          .thenReturn("service-end");
      cvcSupportMock
          .when(() -> CvCertificateSupport.chrBytes(serviceEndEntityCvc))
          .thenReturn("service-end".getBytes(StandardCharsets.ISO_8859_1));
      cvcSupportMock
          .when(() -> CvCertificateSupport.car(serviceEndEntityCvc))
          .thenReturn("service-issuer");
      cvcSupportMock
          .when(() -> CvCertificateSupport.chr(serviceIssuerCvc))
          .thenReturn("service-issuer");
      cvcSupportMock
          .when(() -> CvCertificateSupport.chrBytes(serviceIssuerCvc))
          .thenReturn("service-issuer".getBytes(StandardCharsets.ISO_8859_1));

      final var result = sut.build(sessionId, scenarioResult);

      assertThat(result).containsExactly(serviceEndEntityCvc);
    }
  }

  @Test
  void buildReturnsConfiguredServiceCvcChain() {
    final var sessionId = "sessionId";
    final var resultStep =
        new ScenarioResultStep(StepId.RETRIEVE_PUBLIC_KEY_IDENTIFIERS, "9000", "result".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep));
    final var serviceEndEntityCvc = mock(CvCertificate.class);
    final var serviceIssuerCvc = mock(CvCertificate.class);
    final var trustedRootCvc = mock(CvCertificate.class);
    when(keyIdentifierExtractorMock.extract("result".getBytes())).thenReturn(Set.of());
    when(certificateProviderServiceMock.getCvEndEntityCertificate())
        .thenReturn(serviceEndEntityCvc);
    when(cvcDirectoryMock.all())
        .thenReturn(List.of(serviceEndEntityCvc, serviceIssuerCvc, trustedRootCvc));
    when(cvcDirectoryMock.findByChr("service-issuer")).thenReturn(Optional.of(serviceIssuerCvc));
    when(cvcDirectoryMock.findByChr("trusted-root")).thenReturn(Optional.of(trustedRootCvc));

    try (final var cvcSupportMock = mockStatic(CvCertificateSupport.class)) {
      cvcSupportMock
          .when(() -> CvCertificateSupport.chr(serviceEndEntityCvc))
          .thenReturn("service-end");
      cvcSupportMock
          .when(() -> CvCertificateSupport.chrBytes(serviceEndEntityCvc))
          .thenReturn("service-end".getBytes(StandardCharsets.ISO_8859_1));
      cvcSupportMock
          .when(() -> CvCertificateSupport.car(serviceEndEntityCvc))
          .thenReturn("service-issuer");
      cvcSupportMock
          .when(() -> CvCertificateSupport.chr(serviceIssuerCvc))
          .thenReturn("service-issuer");
      cvcSupportMock
          .when(() -> CvCertificateSupport.chrBytes(serviceIssuerCvc))
          .thenReturn("service-issuer".getBytes(StandardCharsets.ISO_8859_1));
      cvcSupportMock
          .when(() -> CvCertificateSupport.car(serviceIssuerCvc))
          .thenReturn("trusted-root");
      cvcSupportMock
          .when(() -> CvCertificateSupport.chr(trustedRootCvc))
          .thenReturn("trusted-root");
      cvcSupportMock
          .when(() -> CvCertificateSupport.chrBytes(trustedRootCvc))
          .thenReturn("trusted-root".getBytes(StandardCharsets.ISO_8859_1));
      cvcSupportMock
          .when(() -> CvCertificateSupport.car(trustedRootCvc))
          .thenReturn("trusted-root");

      final var result = sut.build(sessionId, scenarioResult);

      assertThat(result).containsExactly(serviceEndEntityCvc, serviceIssuerCvc);
    }
  }

  @Test
  void buildThrowsWhenConfiguredChainContainsLoop() {
    final var sessionId = "sessionId";
    final var resultStep =
        new ScenarioResultStep(StepId.RETRIEVE_PUBLIC_KEY_IDENTIFIERS, "9000", "result".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep));
    final var serviceEndEntityCvc = mock(CvCertificate.class);
    final var serviceIssuerCvc = mock(CvCertificate.class);
    when(keyIdentifierExtractorMock.extract("result".getBytes())).thenReturn(Set.of());
    when(certificateProviderServiceMock.getCvEndEntityCertificate())
        .thenReturn(serviceEndEntityCvc);
    when(cvcDirectoryMock.findByChr("service-issuer")).thenReturn(Optional.of(serviceIssuerCvc));
    when(cvcDirectoryMock.findByChr("service-end")).thenReturn(Optional.of(serviceEndEntityCvc));

    try (final var cvcSupportMock = mockStatic(CvCertificateSupport.class)) {
      cvcSupportMock
          .when(() -> CvCertificateSupport.chr(serviceEndEntityCvc))
          .thenReturn("service-end");
      cvcSupportMock
          .when(() -> CvCertificateSupport.chrBytes(serviceEndEntityCvc))
          .thenReturn("service-end".getBytes(StandardCharsets.ISO_8859_1));
      cvcSupportMock
          .when(() -> CvCertificateSupport.car(serviceEndEntityCvc))
          .thenReturn("service-issuer");
      cvcSupportMock
          .when(() -> CvCertificateSupport.chr(serviceIssuerCvc))
          .thenReturn("service-issuer");
      cvcSupportMock
          .when(() -> CvCertificateSupport.chrBytes(serviceIssuerCvc))
          .thenReturn("service-issuer".getBytes(StandardCharsets.ISO_8859_1));
      cvcSupportMock
          .when(() -> CvCertificateSupport.car(serviceIssuerCvc))
          .thenReturn("service-end");

      assertThatThrownBy(() -> sut.build(sessionId, scenarioResult))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Loop detected in configured CVC chain at CHR service-end");
    }
  }
}
