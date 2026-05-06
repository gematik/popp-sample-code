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

package de.gematik.refpopp.popp_server.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.openhealth.asn1.CvCertificate;
import de.gematik.openhealth.crypto.CryptoException;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.certificates.CertificateProviderService;
import de.gematik.refpopp.popp_server.certificates.CvCertificateSupport;
import de.gematik.refpopp.popp_server.certificates.CvcChainValidator;
import de.gematik.refpopp.popp_server.certificates.CvcFactory;
import de.gematik.refpopp.popp_server.scenario.common.cvc.CvcProcessor;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CvcProcessorTest {

  private CvcProcessor sut;
  private CvcFactory cvcFactoryMock;
  private SessionAccessor sessionAccessorMock;
  private CertificateProviderService certificateProviderServiceMock;
  private CvcChainValidator cvcChainValidatorMock;

  @BeforeEach
  void setUp() {
    cvcFactoryMock = mock(CvcFactory.class);
    final var scenarioResultFinder = new ScenarioResultFinder();
    sessionAccessorMock = mock(SessionAccessor.class);
    certificateProviderServiceMock = mock(CertificateProviderService.class);
    cvcChainValidatorMock = mock(CvcChainValidator.class);
    sut =
        new CvcProcessor(
            cvcFactoryMock,
            scenarioResultFinder,
            sessionAccessorMock,
            certificateProviderServiceMock,
            cvcChainValidatorMock);
  }

  @Test
  void createAndValidateCvcSuccess() throws CryptoException {
    // given
    final var sessionId = "sessionId";
    final var scenarioResultStep =
        new ScenarioResultStep(StepId.READ_END_ENTITY_CV_CERTIFICATE, "9000", "data".getBytes());
    final var scenarioResult = new ScenarioResult("firstResult", List.of(scenarioResultStep));
    final var cvcMock = mock(CvCertificate.class);
    final var issuerMock = mock(CvCertificate.class);
    when(cvcFactoryMock.create(any())).thenReturn(cvcMock);
    when(certificateProviderServiceMock.findIdentityCvcByChr("issuer")).thenReturn(issuerMock);

    try (final var cvcSupportMock = mockStatic(CvCertificateSupport.class)) {
      cvcSupportMock.when(() -> CvCertificateSupport.car(cvcMock)).thenReturn("issuer");

      // when
      final var cvc =
          sut.createAndValidateCvc(
              sessionId, scenarioResult, StepId.READ_END_ENTITY_CV_CERTIFICATE);

      // then
      assertThat(cvc).isNotNull();
      verify(cvcFactoryMock).create("data".getBytes());
      verify(sessionAccessorMock).storeCvc(sessionId, "data".getBytes());
      verify(cvcChainValidatorMock).validate(cvcMock, issuerMock);
    }
  }

  @Test
  void createAndValidateCvcCaSuccess() throws CryptoException {
    // given
    final var sessionId = "sessionId";
    final var scenarioResultStep =
        new ScenarioResultStep(StepId.READ_SUB_CA_CV_CERTIFICATE, "9000", "data".getBytes());
    final var scenarioResult = new ScenarioResult("firstResult", List.of(scenarioResultStep));
    final var cvcMock = mock(CvCertificate.class);
    final var issuerMock = mock(CvCertificate.class);
    when(cvcFactoryMock.create(any())).thenReturn(cvcMock);
    when(certificateProviderServiceMock.findIdentityCvcByChr("issuer")).thenReturn(issuerMock);

    try (final var cvcSupportMock = mockStatic(CvCertificateSupport.class)) {
      cvcSupportMock.when(() -> CvCertificateSupport.car(cvcMock)).thenReturn("issuer");

      // when
      final var cvc =
          sut.createAndValidateCvcCa(sessionId, scenarioResult, StepId.READ_SUB_CA_CV_CERTIFICATE);

      // then
      assertThat(cvc).isNotNull();
      verify(cvcFactoryMock).create("data".getBytes());
      verify(sessionAccessorMock).storeCvcCA(sessionId, "data".getBytes());
      verify(cvcChainValidatorMock).validate(cvcMock, issuerMock);
    }
  }

  @Test
  void createAndValidateCvcFailedWhenChainValidationRejectsChain() throws CryptoException {
    // given
    final var sessionId = "sessionId";
    final var scenarioResultStep =
        new ScenarioResultStep(StepId.READ_END_ENTITY_CV_CERTIFICATE, "9000", "data".getBytes());
    final var scenarioResult = new ScenarioResult("firstResult", List.of(scenarioResultStep));
    final var cvcMock = mock(CvCertificate.class);
    final var issuerMock = mock(CvCertificate.class);
    when(cvcFactoryMock.create(any())).thenReturn(cvcMock);
    when(certificateProviderServiceMock.findIdentityCvcByChr("issuer")).thenReturn(issuerMock);

    try (final var cvcSupportMock = mockStatic(CvCertificateSupport.class)) {
      cvcSupportMock.when(() -> CvCertificateSupport.car(cvcMock)).thenReturn("issuer");
      org.mockito.Mockito.doThrow(new CryptoException.Crypto("signature invalid"))
          .when(cvcChainValidatorMock)
          .validate(cvcMock, issuerMock);

      // when
      assertThatThrownBy(
              () ->
                  sut.createAndValidateCvc(
                      sessionId, scenarioResult, StepId.READ_END_ENTITY_CV_CERTIFICATE))
          .isInstanceOf(ScenarioException.class)
          .hasMessageContaining("Failed to validate CVC chain");
    }

    // then
    verify(cvcFactoryMock).create("data".getBytes());
  }
}
