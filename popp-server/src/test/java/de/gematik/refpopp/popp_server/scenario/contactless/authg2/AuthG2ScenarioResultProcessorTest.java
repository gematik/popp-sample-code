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

package de.gematik.refpopp.popp_server.scenario.contactless.authg2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.openhealth.asn1.CvCertificate;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.certificates.CvcSignatureVerifier;
import de.gematik.refpopp.popp_server.hashdb.EgkHashValidationService;
import de.gematik.refpopp.popp_server.model.CheckResult;
import de.gematik.refpopp.popp_server.scenario.common.cvc.CvcProcessor;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioId;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.refpopp.popp_server.scenario.common.token.JwtTokenCreator;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509CertificateProcessor;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509Data;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

class AuthG2ScenarioResultProcessorTest {

  private CvcProcessor cvcProcessorMock;
  private X509CertificateProcessor x509CertificateProcessorMock;
  private JwtTokenCreator tokenCreatorMock;
  private SessionAccessor sessionAccessorMock;
  private AuthG2ScenarioResultProcessor sut;
  private EgkHashValidationService egkHashValidationServiceMock;
  private CvcSignatureVerifier signatureVerifierMock;

  @BeforeEach
  void setUp() {
    cvcProcessorMock = mock(CvcProcessor.class);
    x509CertificateProcessorMock = mock(X509CertificateProcessor.class);
    tokenCreatorMock = mock(JwtTokenCreator.class);
    sessionAccessorMock = mock(SessionAccessor.class);
    egkHashValidationServiceMock = mock(EgkHashValidationService.class);
    signatureVerifierMock = mock(CvcSignatureVerifier.class);
    sut =
        new AuthG2ScenarioResultProcessor(
            cvcProcessorMock,
            new ScenarioResultFinder(),
            x509CertificateProcessorMock,
            tokenCreatorMock,
            sessionAccessorMock,
            egkHashValidationServiceMock,
            signatureVerifierMock);
  }

  @Test
  void getScenarioId() {
    assertThat(sut.getScenarioId()).isEqualTo(ScenarioId.AUTH_G2);
  }

  @Test
  void processStoresJwtToken() {
    // given
    final var sessionId = "sessionId";
    final var scenarioResult = createScenarioResult();
    final var cvcMock = mock(CvCertificate.class);
    final var x509DataMock = mock(X509Data.class);
    when(cvcProcessorMock.createAndValidateCvcCa(
            sessionId, scenarioResult, StepId.READ_SUB_CA_CV_CERTIFICATE))
        .thenReturn(cvcMock);
    when(cvcProcessorMock.createAndValidateCvc(
            sessionId, scenarioResult, StepId.READ_END_ENTITY_CV_CERTIFICATE))
        .thenReturn(cvcMock);
    when(sessionAccessorMock.getNonce(sessionId)).thenReturn("nonce".getBytes());
    when(x509CertificateProcessorMock.extractCertificateData(sessionId, "x509".getBytes()))
        .thenReturn(x509DataMock);
    when(tokenCreatorMock.createPoppToken(x509DataMock, sessionId)).thenReturn("poppToken");
    when(sessionAccessorMock.getCvc(sessionId)).thenReturn("cvc".getBytes());
    when(sessionAccessorMock.getAut(sessionId)).thenReturn("aut".getBytes());
    when(egkHashValidationServiceMock.validateAndProcess(any(), any(), any(), any()))
        .thenReturn(CheckResult.MATCH);

    when(signatureVerifierMock.verifyCvcEcdsaValueSignature(
            any(CvCertificate.class), any(byte[].class), any(byte[].class)))
        .thenReturn(true);

    // when
    sut.process(sessionId, scenarioResult);

    // then
    verify(tokenCreatorMock).createPoppToken(x509DataMock, sessionId);
    verify(egkHashValidationServiceMock)
        .validateAndProcess(
            "cvc".getBytes(), "aut".getBytes(), CommunicationMode.CONTACTLESS, sessionId);
    verify(x509CertificateProcessorMock).extractCertificateData(sessionId, "x509".getBytes());
    verify(sessionAccessorMock).storeJwtToken(sessionId, "poppToken");
  }

  @ParameterizedTest(name = "{index} => checkResult={0}")
  @org.junit.jupiter.params.provider.EnumSource(
      value = CheckResult.class,
      names = {"BLOCKED", "MISMATCH"})
  void processThrowsExceptionWhenCertificatePairIsBlockedOrMismatched(
      final CheckResult checkResult) {
    // given
    final var sessionId = "sessionId";
    final var scenarioResult = createScenarioResult();
    when(sessionAccessorMock.getNonce(sessionId)).thenReturn("nonce".getBytes());
    final var cvcMock = mock(CvCertificate.class);
    when(cvcProcessorMock.createAndValidateCvc(
            sessionId, scenarioResult, StepId.READ_END_ENTITY_CV_CERTIFICATE))
        .thenReturn(cvcMock);
    when(x509CertificateProcessorMock.extractCertificateData(sessionId, "x509".getBytes()))
        .thenReturn(mock(X509Data.class));
    when(egkHashValidationServiceMock.validateAndProcess(any(), any(), any(), any()))
        .thenReturn(checkResult);

    when(signatureVerifierMock.verifyCvcEcdsaValueSignature(
            any(CvCertificate.class), any(byte[].class), any(byte[].class)))
        .thenReturn(true);

    // when
    assertThatThrownBy(() -> sut.process(sessionId, scenarioResult))
        .isInstanceOf(ScenarioException.class)
        .hasMessage("InvalidCertificatePairContactless");
  }

  @Test
  void processThrowsExceptionWhenCertificatePairIsUnknown() {
    // given
    final var sessionId = "sessionId";
    final var scenarioResult = createScenarioResult();
    when(sessionAccessorMock.getNonce(sessionId)).thenReturn("nonce".getBytes());
    final var cvcMock = mock(CvCertificate.class);
    when(cvcProcessorMock.createAndValidateCvc(
            sessionId, scenarioResult, StepId.READ_END_ENTITY_CV_CERTIFICATE))
        .thenReturn(cvcMock);
    when(x509CertificateProcessorMock.extractCertificateData(sessionId, "x509".getBytes()))
        .thenReturn(mock(X509Data.class));
    when(egkHashValidationServiceMock.validateAndProcess(any(), any(), any(), any()))
        .thenReturn(CheckResult.UNKNOWN);

    when(signatureVerifierMock.verifyCvcEcdsaValueSignature(
            any(CvCertificate.class), any(byte[].class), any(byte[].class)))
        .thenReturn(true);

    // when
    assertThatThrownBy(() -> sut.process(sessionId, scenarioResult))
        .isInstanceOf(ScenarioException.class)
        .hasMessage("UnknownCertificates");
  }

  @Test
  void processThrowsExceptionWhenNonceSignatureIsInvalid() {
    // given
    final var sessionId = "sessionId";
    final var scenarioResult = createScenarioResult();
    final var cvcMock = mock(CvCertificate.class);
    when(cvcProcessorMock.createAndValidateCvcCa(
            sessionId, scenarioResult, StepId.READ_SUB_CA_CV_CERTIFICATE))
        .thenReturn(cvcMock);
    when(cvcProcessorMock.createAndValidateCvc(
            sessionId, scenarioResult, StepId.READ_END_ENTITY_CV_CERTIFICATE))
        .thenReturn(cvcMock);
    when(sessionAccessorMock.getNonce(sessionId)).thenReturn("nonce".getBytes());

    when(signatureVerifierMock.verifyCvcEcdsaValueSignature(
            any(CvCertificate.class), any(byte[].class), any(byte[].class)))
        .thenReturn(false);

    // when
    assertThatThrownBy(() -> sut.process(sessionId, scenarioResult))
        .isInstanceOf(ScenarioException.class)
        .hasMessage("Signature of nonce is not valid");
    verify(tokenCreatorMock, never()).createPoppToken(any(), any());
    verify(sessionAccessorMock, never()).storeNonce(any(), any());
  }

  @Test
  void processThrowsScenarioExceptionWhenNonceSignatureVerificationFails() {
    // given
    final var sessionId = "sessionId";
    final var scenarioResult = createScenarioResult();
    final var cvcMock = mock(CvCertificate.class);
    when(cvcProcessorMock.createAndValidateCvc(
            sessionId, scenarioResult, StepId.READ_END_ENTITY_CV_CERTIFICATE))
        .thenReturn(cvcMock);
    when(sessionAccessorMock.getNonce(sessionId)).thenReturn("nonce".getBytes());
    when(signatureVerifierMock.verifyCvcEcdsaValueSignature(
            any(CvCertificate.class), any(byte[].class), any(byte[].class)))
        .thenThrow(new IllegalStateException("native verification failed"));

    // when / then
    assertThatThrownBy(() -> sut.process(sessionId, scenarioResult))
        .isInstanceOf(ScenarioException.class)
        .hasMessage("Failed to verify nonce signature: native verification failed");
  }

  private static ScenarioResult createScenarioResult() {
    final var scenarioResultStep1 =
        new ScenarioResultStep(StepId.READ_SUB_CA_CV_CERTIFICATE, "9000", "data".getBytes());
    final var scenarioResultStep2 =
        new ScenarioResultStep(StepId.READ_END_ENTITY_CV_CERTIFICATE, "9000", "data".getBytes());
    final var scenarioResultStep3 =
        new ScenarioResultStep(StepId.INTERNAL_AUTHENTICATION, "9000", "data".getBytes());
    final var scenarioResultStep4 =
        new ScenarioResultStep(StepId.READ_X509, "9000", "x509".getBytes());
    return new ScenarioResult(
        "scenarioName",
        List.of(
            scenarioResultStep1, scenarioResultStep2, scenarioResultStep3, scenarioResultStep4));
  }
}
