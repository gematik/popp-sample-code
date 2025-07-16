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

package de.gematik.refpopp.popp_server.scenario.common.result;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import de.gematik.refpopp.popp_server.scenario.common.validator.StatusWordValidator;
import de.gematik.refpopp.popp_server.scenario.contactbased.readx509cert.ReadX509ScenarioResultProcessor;
import de.gematik.refpopp.popp_server.scenario.openegk.OpenEgkScenarioResultProcessor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioResultManagerTest {

  private ScenarioResultManager sut;
  private OpenEgkScenarioResultProcessor openEgkScenarioResultProcessorMock;
  private ReadX509ScenarioResultProcessor readX509ScenarioResultProcessorMock;
  private StatusWordValidator statusWordValidatorMock;
  private ScenarioResultFactory scenarioResultFactoryMock;

  @BeforeEach
  void setUp() {
    openEgkScenarioResultProcessorMock = mock(OpenEgkScenarioResultProcessor.class);
    readX509ScenarioResultProcessorMock = mock(ReadX509ScenarioResultProcessor.class);
    statusWordValidatorMock = mock(StatusWordValidator.class);
    scenarioResultFactoryMock = mock(ScenarioResultFactory.class);
    final List<ScenarioResultProcessor> scenarioResultProcessors =
        List.of(openEgkScenarioResultProcessorMock, readX509ScenarioResultProcessorMock);
    when(openEgkScenarioResultProcessorMock.getScenarioName()).thenReturn("OPEN_CONTACT_ICC");
    when(readX509ScenarioResultProcessorMock.getScenarioName()).thenReturn("READ_X509_CERTIFICATE");
    sut =
        new ScenarioResultManager(
            scenarioResultProcessors, statusWordValidatorMock, scenarioResultFactoryMock);
  }

  @Test
  void manageOpenContactIccScenario() {
    // given
    final var sessionId = "session1";
    final var scenario = new Scenario("OPEN_CONTACT_ICC", List.of());
    final var resultStep = new ScenarioResultStep("description", "9000", "abcdef".getBytes());
    final var resultStep2 = new ScenarioResultStep("description2", "6985", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));
    final var responses = List.of("9000", "6985");
    when(scenarioResultFactoryMock.create(scenario, responses)).thenReturn(scenarioResult);

    // when
    sut.manage(sessionId, scenario, responses);

    // then
    verify(scenarioResultFactoryMock).create(scenario, responses);
    verify(statusWordValidatorMock).validate(scenarioResult, scenario);
    verify(openEgkScenarioResultProcessorMock).process(sessionId, scenarioResult);
  }

  @Test
  void manageReadX509CertificateScenario() {
    // given
    final var sessionId = "session1";
    final var scenario = new Scenario("READ_X509_CERTIFICATE", List.of());
    final var resultStep = new ScenarioResultStep("description", "9000", "abcdef".getBytes());
    final var resultStep2 = new ScenarioResultStep("description2", "6985", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));
    final var responses = List.of("9000", "6985");
    when(scenarioResultFactoryMock.create(scenario, responses)).thenReturn(scenarioResult);

    // when
    sut.manage(sessionId, scenario, responses);

    // then
    verify(scenarioResultFactoryMock).create(scenario, responses);
    verify(statusWordValidatorMock).validate(scenarioResult, scenario);
    verify(readX509ScenarioResultProcessorMock).process(sessionId, scenarioResult);
  }
}
