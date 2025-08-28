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

import de.gematik.refpopp.popp_server.scenario.common.cvc.CvcProcessor;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultProcessor;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReadCvcScenarioResultProcessor implements ScenarioResultProcessor {

  private final CvcProcessor cvcProcessor;
  private final CvcChainBuilder cvcChainBuilder;
  private final CustomApduStepDefinitionFactory customApduStepDefinitionFactory;
  private final SessionAccessor sessionAccessor;

  @Value("${scenario-names.sce-read-cvc}")
  private String readCvcScenarioName;

  @Value("${step-names.read-sub-ca-cv-certificate}")
  private String readSubCaCvCertificateStepName;

  @Value("${step-names.read-end-entity-cv-certificate}")
  private String readEndEntityCvCertificateStepName;

  public ReadCvcScenarioResultProcessor(
      final CvcProcessor cvcProcessor,
      final CvcChainBuilder cvcChainBuilder,
      final CustomApduStepDefinitionFactory customApduStepDefinitionFactory,
      final SessionAccessor sessionAccessor) {
    this.cvcProcessor = cvcProcessor;
    this.cvcChainBuilder = cvcChainBuilder;
    this.customApduStepDefinitionFactory = customApduStepDefinitionFactory;
    this.sessionAccessor = sessionAccessor;
  }

  @Override
  public void process(final String sessionId, final ScenarioResult scenarioResult) {
    checkEhcG21Cards(sessionId, scenarioResult);
  }

  @Override
  public String getScenarioName() {
    return readCvcScenarioName;
  }

  private void checkEhcG21Cards(final String sessionId, final ScenarioResult scenarioResult) {
    final var endEntityCvc =
        cvcProcessor.createAndValidateCvc(
            sessionId, scenarioResult, readEndEntityCvCertificateStepName);
    cvcProcessor.createAndValidateCvcCa(sessionId, scenarioResult, readSubCaCvCertificateStepName);
    final var cvcList = cvcChainBuilder.build(sessionId, scenarioResult, endEntityCvc);
    final var customStates = customApduStepDefinitionFactory.create(cvcList);
    sessionAccessor.storeAdditionalSteps(sessionId, customStates);
  }
}
