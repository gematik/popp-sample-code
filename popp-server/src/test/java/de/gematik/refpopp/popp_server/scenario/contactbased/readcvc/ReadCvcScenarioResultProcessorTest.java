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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.scenario.common.cvc.CvcProcessor;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import de.gematik.smartcards.g2icc.cvc.Cvc;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReadCvcScenarioResultProcessorTest {

  private ReadCvcScenarioResultProcessor sut;
  private CvcProcessor cvcProcessorMock;
  private CvcChainBuilder cvcChainBuilderMock;
  private CustomApduStepDefinitionFactory customApduStepDefinitionFactoryMock;
  private SessionAccessor sessionAccessorMock;

  @BeforeEach
  void setUp() {
    cvcProcessorMock = mock(CvcProcessor.class);
    cvcChainBuilderMock = mock(CvcChainBuilder.class);
    customApduStepDefinitionFactoryMock = mock(CustomApduStepDefinitionFactory.class);
    sessionAccessorMock = mock(SessionAccessor.class);
    sut =
        new ReadCvcScenarioResultProcessor(
            cvcProcessorMock,
            cvcChainBuilderMock,
            customApduStepDefinitionFactoryMock,
            sessionAccessorMock);
    ReflectionTestUtils.setField(sut, "readCvcScenarioName", "readCvcScenarioName");
    ReflectionTestUtils.setField(
        sut, "readSubCaCvCertificateStepName", "readSubCaCvCertificateStepName");
    ReflectionTestUtils.setField(
        sut, "readEndEntityCvCertificateStepName", "readEndEntityCvCertificateStepName");
  }

  @Test
  void processSuccess() {
    // given
    final var sessionId = "sessionId";
    final var scenarioResultStep =
        new ScenarioResult.ScenarioResultStep("description", "9000", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(scenarioResultStep));
    final var endEntityCvc = mock(Cvc.class);
    when(cvcProcessorMock.createAndValidateCvc(
            sessionId, scenarioResult, "readEndEntityCvCertificateStepName"))
        .thenReturn(endEntityCvc);

    // when
    sut.process(sessionId, scenarioResult);

    // then
    verify(cvcProcessorMock)
        .createAndValidateCvc(sessionId, scenarioResult, "readEndEntityCvCertificateStepName");
    verify(cvcProcessorMock)
        .createAndValidateCvcCa(sessionId, scenarioResult, "readSubCaCvCertificateStepName");
    verify(cvcChainBuilderMock).build(sessionId, scenarioResult, endEntityCvc);
    verify(customApduStepDefinitionFactoryMock).create(any());
    verify(sessionAccessorMock).storeAdditionalSteps(eq(sessionId), any());
  }

  @Test
  void getScenarioNameReturnsScenarioName() {
    // when
    final var scenarioName = sut.getScenarioName();

    // then
    assertEquals("readCvcScenarioName", scenarioName);
  }
}
