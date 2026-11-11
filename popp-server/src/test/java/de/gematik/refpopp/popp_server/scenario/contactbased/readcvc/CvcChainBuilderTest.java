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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import de.gematik.openhealth.asn1.CvCertificate;
import de.gematik.refpopp.popp_server.certificates.CertificateProviderService;
import de.gematik.refpopp.popp_server.certificates.CvCertificateSupport;
import de.gematik.refpopp.popp_server.certificates.CvcDirectory;
import de.gematik.refpopp.popp_server.certificates.CvcFactory;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

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
    when(cvcDirectoryMock.findAllByChr("service-issuer")).thenReturn(List.of(serviceIssuerCvc));

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
          .when(() -> CvCertificateSupport.carBytes(serviceEndEntityCvc))
          .thenReturn("service-issuer".getBytes(StandardCharsets.ISO_8859_1));
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
    when(keyIdentifierExtractorMock.extract("result".getBytes()))
        .thenReturn(
            Set.of(HEX_FORMAT.formatHex("trusted-root".getBytes(StandardCharsets.ISO_8859_1))));
    when(certificateProviderServiceMock.getCvEndEntityCertificate())
        .thenReturn(serviceEndEntityCvc);
    when(cvcDirectoryMock.all())
        .thenReturn(List.of(serviceEndEntityCvc, serviceIssuerCvc, trustedRootCvc));
    when(cvcDirectoryMock.findAllByChr("service-issuer")).thenReturn(List.of(serviceIssuerCvc));
    when(cvcDirectoryMock.findAllByChr("trusted-root")).thenReturn(List.of(trustedRootCvc));

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
          .when(() -> CvCertificateSupport.carBytes(serviceEndEntityCvc))
          .thenReturn("service-issuer".getBytes(StandardCharsets.ISO_8859_1));
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
          .when(() -> CvCertificateSupport.carBytes(serviceIssuerCvc))
          .thenReturn("trusted-root".getBytes(StandardCharsets.ISO_8859_1));
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
    when(cvcDirectoryMock.findAllByChr("service-issuer")).thenReturn(List.of(serviceIssuerCvc));
    when(cvcDirectoryMock.findAllByChr("service-end")).thenReturn(List.of(serviceEndEntityCvc));

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
          .when(() -> CvCertificateSupport.carBytes(serviceEndEntityCvc))
          .thenReturn("service-issuer".getBytes(StandardCharsets.ISO_8859_1));
      cvcSupportMock
          .when(() -> CvCertificateSupport.chr(serviceIssuerCvc))
          .thenReturn("service-issuer");
      cvcSupportMock
          .when(() -> CvCertificateSupport.chrBytes(serviceIssuerCvc))
          .thenReturn("service-issuer".getBytes(StandardCharsets.ISO_8859_1));
      cvcSupportMock
          .when(() -> CvCertificateSupport.car(serviceIssuerCvc))
          .thenReturn("service-end");
      cvcSupportMock
          .when(() -> CvCertificateSupport.carBytes(serviceIssuerCvc))
          .thenReturn("service-end".getBytes(StandardCharsets.ISO_8859_1));

      assertThatThrownBy(() -> sut.build(sessionId, scenarioResult))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("No configured CVC chain reaches a public key known by the card");
    }
  }

  @Test
  void buildUsesAlternativeIssuerCertificateThatReachesCardKnownKey() {
    final var service = mock(CvCertificate.class);
    final var untrustedIssuer = mock(CvCertificate.class);
    final var trustedIssuer = mock(CvCertificate.class);
    final var untrustedRoot = mock(CvCertificate.class);
    when(certificateProviderServiceMock.getCvEndEntityCertificate()).thenReturn(service);
    when(keyIdentifierExtractorMock.extract("result".getBytes()))
        .thenReturn(
            Set.of(HEX_FORMAT.formatHex("known-root".getBytes(StandardCharsets.ISO_8859_1))));
    when(cvcDirectoryMock.findAllByChr("issuer"))
        .thenReturn(List.of(untrustedIssuer, trustedIssuer));
    when(cvcDirectoryMock.findAllByChr("unknown-root")).thenReturn(List.of(untrustedRoot));

    try (final var support = mockStatic(CvCertificateSupport.class)) {
      mockReferences(support, service, "service", "issuer");
      mockReferences(support, untrustedIssuer, "issuer", "unknown-root");
      mockReferences(support, trustedIssuer, "issuer", "known-root");
      mockReferences(support, untrustedRoot, "unknown-root", "unknown-root");

      assertThat(sut.build("sessionId", scenarioResult())).containsExactly(service, trustedIssuer);
    }
  }

  @Test
  void buildChoosesShortestAnchoredChainRegardlessOfIssuerOrder() {
    final var service = mock(CvCertificate.class);
    final var longerIssuer = mock(CvCertificate.class);
    final var shorterIssuer = mock(CvCertificate.class);
    final var intermediate = mock(CvCertificate.class);
    when(certificateProviderServiceMock.getCvEndEntityCertificate()).thenReturn(service);
    when(keyIdentifierExtractorMock.extract("result".getBytes()))
        .thenReturn(
            Set.of(HEX_FORMAT.formatHex("known-root".getBytes(StandardCharsets.ISO_8859_1))));
    when(cvcDirectoryMock.findAllByChr("issuer")).thenReturn(List.of(longerIssuer, shorterIssuer));
    when(cvcDirectoryMock.findAllByChr("intermediate")).thenReturn(List.of(intermediate));

    try (final var support = mockStatic(CvCertificateSupport.class)) {
      mockReferences(support, service, "service", "issuer");
      mockReferences(support, longerIssuer, "issuer", "intermediate");
      mockReferences(support, shorterIssuer, "issuer", "known-root");
      mockReferences(support, intermediate, "intermediate", "known-root");

      assertThat(sut.build("sessionId", scenarioResult())).containsExactly(service, shorterIssuer);
    }
  }

  @Test
  void buildRejectsMissingIssuerInsteadOfReturningPartialChain() {
    final var service = mock(CvCertificate.class);
    when(certificateProviderServiceMock.getCvEndEntityCertificate()).thenReturn(service);
    when(keyIdentifierExtractorMock.extract("result".getBytes())).thenReturn(Set.of());

    try (final var support = mockStatic(CvCertificateSupport.class)) {
      mockReferences(support, service, "service", "missing-issuer");

      assertThatThrownBy(() -> sut.build("sessionId", scenarioResult()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("No configured CVC chain reaches a public key known by the card");
    }
  }

  @Test
  void buildRejectsSelfSignedRootUnknownToCard() {
    final var service = mock(CvCertificate.class);
    final var root = mock(CvCertificate.class);
    when(certificateProviderServiceMock.getCvEndEntityCertificate()).thenReturn(service);
    when(keyIdentifierExtractorMock.extract("result".getBytes())).thenReturn(Set.of());
    when(cvcDirectoryMock.findAllByChr("unknown-root")).thenReturn(List.of(root));

    try (final var support = mockStatic(CvCertificateSupport.class)) {
      mockReferences(support, service, "service", "unknown-root");
      mockReferences(support, root, "unknown-root", "unknown-root");

      assertThatThrownBy(() -> sut.build("sessionId", scenarioResult()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("No configured CVC chain reaches a public key known by the card");
    }
  }

  @Test
  void buildFindsConfiguredChainToRootNineWithRealCvcResources() throws Exception {
    final var factory = new CvcFactory();
    final var issuersByChr = new LinkedHashMap<String, List<CvCertificate>>();
    final var resources =
        new PathMatchingResourcePatternResolver()
            .getResources("classpath:identities/PKI_CVC.G2/trusted/**/*.cvc");
    for (final var resource : resources) {
      try (final var stream = resource.getInputStream()) {
        final var cvc = factory.create(stream.readAllBytes());
        issuersByChr
            .computeIfAbsent(CvCertificateSupport.chr(cvc), ignored -> new ArrayList<>())
            .add(cvc);
      }
    }
    try (final var stream =
        new ClassPathResource("certificates/cvc/80276001011699902101-cvc-flag0.crt")
            .getInputStream()) {
      when(certificateProviderServiceMock.getCvEndEntityCertificate())
          .thenReturn(factory.create(stream.readAllBytes()));
    }
    when(cvcDirectoryMock.findAllByChr(anyString()))
        .thenAnswer(invocation -> issuersByChr.getOrDefault(invocation.getArgument(0), List.of()));
    final var rootNineReference = "4445475858890225";
    when(keyIdentifierExtractorMock.extract("result".getBytes()))
        .thenReturn(Set.of(rootNineReference, "0000000000000013"));

    final var chain = sut.build("sessionId", scenarioResult());

    assertThat(chain).hasSize(4);
    assertThat(CvCertificateSupport.carBytes(chain.getLast()))
        .isEqualTo(HEX_FORMAT.parseHex(rootNineReference));
  }

  private ScenarioResult scenarioResult() {
    return new ScenarioResult(
        "scenario",
        List.of(
            new ScenarioResultStep(
                StepId.RETRIEVE_PUBLIC_KEY_IDENTIFIERS, "9000", "result".getBytes())));
  }

  private void mockReferences(
      MockedStatic<CvCertificateSupport> support,
      CvCertificate certificate,
      String chr,
      String car) {
    support.when(() -> CvCertificateSupport.chr(certificate)).thenReturn(chr);
    support
        .when(() -> CvCertificateSupport.chrBytes(certificate))
        .thenReturn(chr.getBytes(StandardCharsets.ISO_8859_1));
    support.when(() -> CvCertificateSupport.car(certificate)).thenReturn(car);
    support
        .when(() -> CvCertificateSupport.carBytes(certificate))
        .thenReturn(car.getBytes(StandardCharsets.ISO_8859_1));
  }
}
