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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.hashdb.EgkHashValidationService;
import de.gematik.refpopp.popp_server.model.CheckResult;
import de.gematik.refpopp.popp_server.scenario.common.cvc.CvcProcessor;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.refpopp.popp_server.scenario.common.token.JwtTokenCreator;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509CertificateProcessor;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import de.gematik.smartcards.crypto.EcPublicKeyImpl;
import de.gematik.smartcards.g2icc.cvc.CertificateDate;
import de.gematik.smartcards.g2icc.cvc.Cvc;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.test.util.ReflectionTestUtils;

class AuthG2ScenarioResultProcessorTest {

  private CvcProcessor cvcProcessorMock;
  private ScenarioResultFinder scenarioResultFinderMock;
  private X509CertificateProcessor x509CertificateProcessorMock;
  private JwtTokenCreator tokenCreatorMock;
  private SessionAccessor sessionAccessorMock;
  private AuthG2ScenarioResultProcessor sut;
  private EgkHashValidationService egkHashValidationServiceMock;

  @BeforeEach
  void setUp() {
    cvcProcessorMock = mock(CvcProcessor.class);
    scenarioResultFinderMock = mock(ScenarioResultFinder.class);
    x509CertificateProcessorMock = mock(X509CertificateProcessor.class);
    tokenCreatorMock = mock(JwtTokenCreator.class);
    sessionAccessorMock = mock(SessionAccessor.class);
    egkHashValidationServiceMock = mock(EgkHashValidationService.class);
    sut =
        new AuthG2ScenarioResultProcessor(
            cvcProcessorMock,
            scenarioResultFinderMock,
            x509CertificateProcessorMock,
            tokenCreatorMock,
            sessionAccessorMock,
            egkHashValidationServiceMock);
    ReflectionTestUtils.setField(sut, "authG2ScenarioName", "authG2ScenarioName");
    ReflectionTestUtils.setField(
        sut, "readEndEntityCvCertificateStepName", "readEndEntityCvCertificateStepName");
    ReflectionTestUtils.setField(
        sut, "readSubCaCvCertificateStepName", "readSubCaCvCertificateStepName");
    ReflectionTestUtils.setField(sut, "readX509StepName", "readX509StepName");
    ReflectionTestUtils.setField(
        sut, "internalAuthenticationStepName", "internalAuthenticationStepName");
  }

  @Test
  void getScenarioName() {
    assertThat(sut.getScenarioName()).isEqualTo("authG2ScenarioName");
  }

  @Test
  void processStoresJwtToken() {
    // given
    final var sessionId = "sessionId";
    final var scenarioResult = createScenarioResult();
    final var cvcMock = mock(Cvc.class);
    final var publicKeyMock = mock(EcPublicKeyImpl.class);
    when(cvcMock.getPublicKey()).thenReturn(publicKeyMock);
    when(publicKeyMock.verifyEcdsa((BigInteger) any(), (byte[]) any())).thenReturn(true);
    when(cvcProcessorMock.createAndValidateCvcCa(
            sessionId, scenarioResult, "readSubCaCvCertificateStepName"))
        .thenReturn(cvcMock);
    when(cvcProcessorMock.createAndValidateCvc(
            sessionId, scenarioResult, "readEndEntityCvCertificateStepName"))
        .thenReturn(cvcMock);
    final var certificateDate = mock(CertificateDate.class);
    when(cvcMock.getCed()).thenReturn(certificateDate);
    when(sessionAccessorMock.getNonce(sessionId)).thenReturn("nonce".getBytes());
    when(certificateDate.getDate()).thenReturn(LocalDate.now());
    when(scenarioResultFinderMock.find(anyString(), any(), anyString()))
        .thenReturn(scenarioResult.scenarioResultSteps().get(2));
    when(tokenCreatorMock.createPoppToken(any(), any())).thenReturn("poppToken");
    when(sessionAccessorMock.getCvc(sessionId)).thenReturn("cvc".getBytes());
    when(sessionAccessorMock.getAut(sessionId)).thenReturn("aut".getBytes());

    // when
    sut.process(sessionId, scenarioResult);

    // then
    verify(tokenCreatorMock).createPoppToken(any(), eq(sessionId));
    verify(egkHashValidationServiceMock)
        .validateAndProcess(
            "cvc".getBytes(), "aut".getBytes(), CommunicationMode.CONTACTLESS, sessionId);
    verify(x509CertificateProcessorMock).extractCertificateData(sessionId, "data".getBytes());
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
    when(scenarioResultFinderMock.find(anyString(), any(), anyString()))
        .thenReturn(scenarioResult.scenarioResultSteps().get(2));
    when(sessionAccessorMock.getNonce(sessionId)).thenReturn("nonce".getBytes());
    final var cvcMock = mock(Cvc.class);
    final var publicKeyMock = mock(EcPublicKeyImpl.class);
    when(cvcMock.getPublicKey()).thenReturn(publicKeyMock);
    when(publicKeyMock.verifyEcdsa((BigInteger) any(), (byte[]) any())).thenReturn(true);
    when(cvcProcessorMock.createAndValidateCvc(
            sessionId, scenarioResult, "readEndEntityCvCertificateStepName"))
        .thenReturn(cvcMock);
    when(egkHashValidationServiceMock.validateAndProcess(any(), any(), any(), any()))
        .thenReturn(checkResult);

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
    when(scenarioResultFinderMock.find(anyString(), any(), anyString()))
        .thenReturn(scenarioResult.scenarioResultSteps().get(2));
    when(sessionAccessorMock.getNonce(sessionId)).thenReturn("nonce".getBytes());
    final var cvcMock = mock(Cvc.class);
    final var publicKeyMock = mock(EcPublicKeyImpl.class);
    when(cvcMock.getPublicKey()).thenReturn(publicKeyMock);
    when(publicKeyMock.verifyEcdsa((BigInteger) any(), (byte[]) any())).thenReturn(true);
    when(cvcProcessorMock.createAndValidateCvc(
            sessionId, scenarioResult, "readEndEntityCvCertificateStepName"))
        .thenReturn(cvcMock);
    when(egkHashValidationServiceMock.validateAndProcess(any(), any(), any(), any()))
        .thenReturn(CheckResult.UNKNOWN);

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
    final var cvcMock = mock(Cvc.class);
    final var publicKeyMock = mock(EcPublicKeyImpl.class);
    when(cvcMock.getPublicKey()).thenReturn(publicKeyMock);
    when(publicKeyMock.verifyEcdsa((BigInteger) any(), (byte[]) any())).thenReturn(false);
    when(cvcProcessorMock.createAndValidateCvcCa(
            sessionId, scenarioResult, "readSubCaCvCertificateStepName"))
        .thenReturn(cvcMock);
    when(cvcProcessorMock.createAndValidateCvc(
            sessionId, scenarioResult, "readEndEntityCvCertificateStepName"))
        .thenReturn(cvcMock);
    final var certificateDate = mock(CertificateDate.class);
    when(cvcMock.getCed()).thenReturn(certificateDate);
    when(certificateDate.getDate()).thenReturn(LocalDate.now());
    when(scenarioResultFinderMock.find(anyString(), any(), anyString()))
        .thenReturn(scenarioResult.scenarioResultSteps().get(2));
    when(sessionAccessorMock.getNonce(sessionId)).thenReturn("nonce".getBytes());

    // when
    assertThatThrownBy(() -> sut.process(sessionId, scenarioResult))
        .isInstanceOf(ScenarioException.class)
        .hasMessage("Signature of nonce is not valid");
    verify(tokenCreatorMock, never()).createPoppToken(any(), any());
    verify(sessionAccessorMock, never()).storeNonce(any(), any());
  }

  private static ScenarioResult createScenarioResult() {
    final var scenarioResultStep1 =
        new ScenarioResultStep("readSubCaCvCertificateStepName", "9000", "data".getBytes());
    final var scenarioResultStep2 =
        new ScenarioResultStep("readEndEntityCvCertificateStepName", "9000", "data".getBytes());
    final var scenarioResultStep3 =
        new ScenarioResultStep("internalAuthenticationStepName", "9000", "data".getBytes());
    return new ScenarioResult(
        "scenarioName", List.of(scenarioResultStep1, scenarioResultStep2, scenarioResultStep3));
  }
}
