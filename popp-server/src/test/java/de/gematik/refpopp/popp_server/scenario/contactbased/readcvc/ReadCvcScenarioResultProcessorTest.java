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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.openhealth.asn1.CvCertificate;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.certificates.CvCertificateSupport;
import de.gematik.refpopp.popp_server.scenario.common.cvc.CvcProcessor;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioId;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadCvcScenarioResultProcessorTest {

  private ReadCvcScenarioResultProcessor sut;
  private CvcProcessor cvcProcessorMock;
  private CvcChainBuilder cvcChainBuilderMock;
  private SessionAccessor sessionAccessorMock;

  @BeforeEach
  void setUp() {
    cvcProcessorMock = mock(CvcProcessor.class);
    cvcChainBuilderMock = mock(CvcChainBuilder.class);
    sessionAccessorMock = mock(SessionAccessor.class);
    sut =
        new ReadCvcScenarioResultProcessor(
            cvcProcessorMock, cvcChainBuilderMock, sessionAccessorMock);
  }

  @Test
  void processSuccess() {
    // given
    final var sessionId = "sessionId";
    final var scenarioResultStep =
        new ScenarioResult.ScenarioResultStep(
            StepId.READ_END_ENTITY_CV_CERTIFICATE, "9000", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(scenarioResultStep));
    final var endEntityCvc = createCvc();
    final var importableCvc = createCvc();
    final var cvcChain = List.of(importableCvc);
    when(cvcProcessorMock.createAndValidateCvc(
            sessionId, scenarioResult, StepId.READ_END_ENTITY_CV_CERTIFICATE))
        .thenReturn(endEntityCvc);
    when(cvcChainBuilderMock.build(sessionId, scenarioResult)).thenReturn(cvcChain);

    // when
    try (final var cvcSupportMock = mockStatic(CvCertificateSupport.class)) {
      cvcSupportMock
          .when(() -> CvCertificateSupport.carBytes(importableCvc))
          .thenReturn("import-car".getBytes());
      cvcSupportMock
          .when(() -> CvCertificateSupport.certificateValueField(importableCvc))
          .thenReturn("import-value".getBytes());
      sut.process(sessionId, scenarioResult);
    }

    // then
    verify(cvcProcessorMock)
        .createAndValidateCvc(sessionId, scenarioResult, StepId.READ_END_ENTITY_CV_CERTIFICATE);
    verify(cvcProcessorMock)
        .createAndValidateCvcCa(sessionId, scenarioResult, StepId.READ_SUB_CA_CV_CERTIFICATE);
    verify(cvcChainBuilderMock).build(sessionId, scenarioResult);
    verify(sessionAccessorMock)
        .storeAdditionalSteps(eq(sessionId), org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void processThrowsScenarioExceptionWhenCvcChainBuildFails() {
    final var sessionId = "sessionId";
    final var scenarioResultStep =
        new ScenarioResult.ScenarioResultStep(
            StepId.READ_END_ENTITY_CV_CERTIFICATE, "9000", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(scenarioResultStep));
    final var endEntityCvc = mock(CvCertificate.class);
    when(cvcProcessorMock.createAndValidateCvc(
            sessionId, scenarioResult, StepId.READ_END_ENTITY_CV_CERTIFICATE))
        .thenReturn(endEntityCvc);
    when(cvcChainBuilderMock.build(sessionId, scenarioResult))
        .thenThrow(new IllegalArgumentException("not a chain"));

    assertThatThrownBy(() -> sut.process(sessionId, scenarioResult))
        .isInstanceOf(ScenarioException.class)
        .hasMessageContaining("Failed to prepare trusted-channel CVC chain")
        .hasMessageContaining("not a chain");
  }

  @Test
  void processBuildsTrustedChannelStepsWhenNoImportableCvcsRemain() {
    final var sessionId = "sessionId";
    final var scenarioResultStep =
        new ScenarioResult.ScenarioResultStep(
            StepId.READ_END_ENTITY_CV_CERTIFICATE, "9000", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(scenarioResultStep));
    final var endEntityCvc = mock(CvCertificate.class);
    when(cvcProcessorMock.createAndValidateCvc(
            sessionId, scenarioResult, StepId.READ_END_ENTITY_CV_CERTIFICATE))
        .thenReturn(endEntityCvc);
    when(cvcChainBuilderMock.build(sessionId, scenarioResult)).thenReturn(List.of());

    try (final var ignored = mockStatic(CvCertificateSupport.class)) {
      sut.process(sessionId, scenarioResult);
    }

    final var stepsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
    verify(sessionAccessorMock).storeAdditionalSteps(eq(sessionId), stepsCaptor.capture());
    @SuppressWarnings("unchecked")
    final var customSteps = (List<StepDefinition>) stepsCaptor.getValue();
    assertEquals(1, customSteps.size());
    assertEquals("mutual-authentication-step-1", customSteps.getFirst().name());
    assertEquals(
        StepId.MUTUAL_AUTHENTICATION_STEP_1.expectedStatusWords(),
        customSteps.getFirst().expectedStatusWords());
    assertEquals(null, customSteps.getFirst().commandData());
  }

  @Test
  void processBuildsImportAndMutualAuthenticationSteps() {
    final var sessionId = "sessionId";
    final var scenarioResult =
        new ScenarioResult(
            "scenario",
            List.of(
                new ScenarioResult.ScenarioResultStep(
                    StepId.READ_END_ENTITY_CV_CERTIFICATE, "9000", "abcdef".getBytes())));
    final var endEntityCvc = createCvc();
    final var importableCvc1 = createCvc();
    final var importableCvc2 = createCvc();
    when(cvcProcessorMock.createAndValidateCvc(
            sessionId, scenarioResult, StepId.READ_END_ENTITY_CV_CERTIFICATE))
        .thenReturn(endEntityCvc);
    when(cvcChainBuilderMock.build(sessionId, scenarioResult))
        .thenReturn(List.of(importableCvc1, importableCvc2));

    try (final var cvcSupportMock = mockStatic(CvCertificateSupport.class)) {
      cvcSupportMock
          .when(() -> CvCertificateSupport.carBytes(importableCvc1))
          .thenReturn("car1".getBytes());
      cvcSupportMock
          .when(() -> CvCertificateSupport.carBytes(importableCvc2))
          .thenReturn("car2".getBytes());
      cvcSupportMock
          .when(() -> CvCertificateSupport.certificateValueField(importableCvc1))
          .thenReturn("value1".getBytes());
      cvcSupportMock
          .when(() -> CvCertificateSupport.certificateValueField(importableCvc2))
          .thenReturn("value2".getBytes());
      sut.process(sessionId, scenarioResult);
    }

    final var stepsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
    verify(sessionAccessorMock).storeAdditionalSteps(eq(sessionId), stepsCaptor.capture());
    @SuppressWarnings("unchecked")
    final var customSteps = (List<StepDefinition>) stepsCaptor.getValue();
    assertEquals(5, customSteps.size());
    assertEquals("mse-apdu", customSteps.get(0).name());
    assertEquals("car2", new String(customSteps.get(0).commandData()));
    assertEquals("pso-apdu", customSteps.get(1).name());
    assertEquals("value2", new String(customSteps.get(1).commandData()));
    assertEquals("mse-apdu", customSteps.get(2).name());
    assertEquals("car1", new String(customSteps.get(2).commandData()));
    assertEquals("pso-apdu", customSteps.get(3).name());
    assertEquals("value1", new String(customSteps.get(3).commandData()));
    assertEquals("mutual-authentication-step-1", customSteps.get(4).name());
  }

  @Test
  void getScenarioIdReturnsScenarioId() {
    // when
    final var scenarioId = sut.getScenarioId();

    // then
    assertEquals(ScenarioId.READ_CVC, scenarioId);
  }

  private CvCertificate createCvc() {
    return mock(CvCertificate.class);
  }
}
