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

package de.gematik.refpopp.popp_server.scenario.contactbased.readx509cert;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.hashdb.EgkHashValidationService;
import de.gematik.refpopp.popp_server.model.CheckResult;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.refpopp.popp_server.scenario.common.token.TokenCreator;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509CertificateProcessor;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509Data;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.test.util.ReflectionTestUtils;

class ReadX509ScenarioResultProcessorTest {

  private ReadX509ScenarioResultProcessor sut;
  private ScenarioResultFinder scenarioResultFinderMock;
  private X509CertificateProcessor x509CertificateProcessorMock;
  private TokenCreator tokenCreatorMock;
  private SessionAccessor sessionAccessorMock;
  private EgkHashValidationService egkHashValidationServiceMock;

  @BeforeEach
  void setUp() {
    scenarioResultFinderMock = mock(ScenarioResultFinder.class);
    x509CertificateProcessorMock = mock(X509CertificateProcessor.class);
    tokenCreatorMock = mock(TokenCreator.class);
    sessionAccessorMock = mock(SessionAccessor.class);
    egkHashValidationServiceMock = mock(EgkHashValidationService.class);
    sut =
        new ReadX509ScenarioResultProcessor(
            scenarioResultFinderMock,
            x509CertificateProcessorMock,
            tokenCreatorMock,
            sessionAccessorMock,
            egkHashValidationServiceMock);
    ReflectionTestUtils.setField(sut, "contentOfEfCChAutE256", "read-ef-c-ch-aut-e256");
  }

  @Test
  void processSuccessfully() {
    // given
    final var sessionId = "sessionId";
    final var resultStep = new ScenarioResultStep("description", "9000", "abcdef".getBytes());
    final var resultStep2 = new ScenarioResultStep("description2", "6985", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));

    when(scenarioResultFinderMock.find(anyString(), anyList(), anyString()))
        .thenReturn(resultStep2);
    final var x509DataMock = mock(X509Data.class);
    when(x509CertificateProcessorMock.extractCertificateData(anyString(), any()))
        .thenReturn(x509DataMock);
    when(sessionAccessorMock.getCvc(sessionId)).thenReturn(resultStep2.data());
    when(egkHashValidationServiceMock.validateAndProcess(any(), any(), any(), anyString()))
        .thenReturn(CheckResult.UNKNOWN);
    final var jwtToken = "base64UrlEncodedJwtToken";
    when(tokenCreatorMock.createPoppToken(x509DataMock, sessionId)).thenReturn(jwtToken);

    // when
    sut.process(sessionId, scenarioResult);

    // then
    verify(scenarioResultFinderMock)
        .find(sessionId, scenarioResult.scenarioResultSteps(), "read-ef-c-ch-aut-e256");
    verify(x509CertificateProcessorMock).extractCertificateData(sessionId, resultStep2.data());
    verify(egkHashValidationServiceMock)
        .validateAndProcess(
            resultStep.data(), resultStep2.data(), CommunicationMode.CONTACT, sessionId);
    verify(tokenCreatorMock).createPoppToken(x509DataMock, sessionId);
    verify(sessionAccessorMock).storeJwtToken(sessionId, jwtToken);
  }

  @ParameterizedTest(name = "{index} => checkResult={0}")
  @org.junit.jupiter.params.provider.EnumSource(
      value = CheckResult.class,
      names = {"BLOCKED", "MISMATCH"})
  void processFailsWhenCertificatesAreBlockedOrMismatched(final CheckResult checkResult) {
    // given
    final var sessionId = "sessionId";
    final var resultStep = new ScenarioResultStep("description", "9000", "abcdef".getBytes());
    final var resultStep2 = new ScenarioResultStep("description2", "6985", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));

    when(scenarioResultFinderMock.find(anyString(), anyList(), anyString()))
        .thenReturn(resultStep2);
    when(sessionAccessorMock.getCvc(sessionId)).thenReturn(resultStep2.data());
    when(egkHashValidationServiceMock.validateAndProcess(any(), any(), any(), anyString()))
        .thenReturn(checkResult);

    // when / then
    assertThatThrownBy(() -> sut.process(sessionId, scenarioResult))
        .isInstanceOf(ScenarioException.class)
        .hasMessageContaining("InvalidCertificatePairT1");
  }
}
