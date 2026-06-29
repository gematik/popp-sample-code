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

import de.gematik.openhealth.asn1.CvCertificate;
import de.gematik.poppcommons.api.enums.BdeErrorCode;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.certificates.CvCertificateSupport;
import de.gematik.refpopp.popp_server.scenario.common.cvc.CvcProcessor;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioId;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultProcessor;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReadCvcScenarioResultProcessor implements ScenarioResultProcessor {

  private final CvcProcessor cvcProcessor;
  private final CvcChainBuilder cvcChainBuilder;
  private final SessionAccessor sessionAccessor;

  public ReadCvcScenarioResultProcessor(
      final CvcProcessor cvcProcessor,
      final CvcChainBuilder cvcChainBuilder,
      final SessionAccessor sessionAccessor) {
    this.cvcProcessor = cvcProcessor;
    this.cvcChainBuilder = cvcChainBuilder;
    this.sessionAccessor = sessionAccessor;
  }

  @Override
  public void process(final String sessionId, final ScenarioResult scenarioResult) {
    checkEhcG21Cards(sessionId, scenarioResult);
  }

  @Override
  public ScenarioId getScenarioId() {
    return ScenarioId.READ_CVC;
  }

  private void checkEhcG21Cards(final String sessionId, final ScenarioResult scenarioResult) {
    cvcProcessor.createAndValidateCvc(
        sessionId, scenarioResult, StepId.READ_END_ENTITY_CV_CERTIFICATE);
    cvcProcessor.createAndValidateCvcCa(
        sessionId, scenarioResult, StepId.READ_SUB_CA_CV_CERTIFICATE);
    final var importableCvcs = buildTrustedChannelCvcChain(sessionId, scenarioResult);
    final var trustedChannelSteps = createTrustedChannelSteps(sessionId, importableCvcs);
    sessionAccessor.storeAdditionalSteps(sessionId, trustedChannelSteps);
  }

  private List<CvCertificate> buildTrustedChannelCvcChain(
      final String sessionId, final ScenarioResult scenarioResult) {
    try {
      return cvcChainBuilder.build(sessionId, scenarioResult);
    } catch (final RuntimeException e) {
      throw new ScenarioException(
          sessionId,
          "Failed to prepare trusted-channel CVC chain: " + exceptionMessage(e),
          BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR,
          e);
    }
  }

  private List<
          de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios
              .StepDefinition>
      createTrustedChannelSteps(final String sessionId, final List<CvCertificate> cvcList) {
    try {
      return buildTrustedChannelSteps(cvcList);
    } catch (final RuntimeException e) {
      throw new ScenarioException(
          sessionId,
          "Failed to prepare trusted-channel steps: " + exceptionMessage(e),
          BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR,
          e);
    }
  }

  private List<StepDefinition> buildTrustedChannelSteps(final List<CvCertificate> cvcList) {
    final var scenarioSteps = new ArrayList<StepDefinition>();
    addImportCvcSteps(scenarioSteps, cvcList);
    addMutualAuthenticationStep(scenarioSteps);
    return scenarioSteps;
  }

  private void addImportCvcSteps(
      final List<StepDefinition> scenarioSteps, final List<CvCertificate> cvcList) {
    for (int i = cvcList.size(); i-- > 0; ) {
      final var cvc = cvcList.get(i);
      scenarioSteps.add(createManageSecEnvStep(cvc));
      scenarioSteps.add(createPsoStep(cvc));
    }
  }

  private void addMutualAuthenticationStep(final List<StepDefinition> scenarioSteps) {
    scenarioSteps.add(createMutualAuthenticationStep1());
  }

  private StepDefinition createManageSecEnvStep(final CvCertificate cvc) {
    return new StepDefinition(StepId.MSE_APDU, CvCertificateSupport.carBytes(cvc));
  }

  private StepDefinition createPsoStep(final CvCertificate cvc) {
    return new StepDefinition(StepId.PSO_APDU, CvCertificateSupport.certificateValueField(cvc));
  }

  private StepDefinition createMutualAuthenticationStep1() {
    return new StepDefinition(StepId.MUTUAL_AUTHENTICATION_STEP_1);
  }

  private String exceptionMessage(final RuntimeException e) {
    final var message = e.getMessage();
    return (message == null || message.isBlank()) ? e.getClass().getSimpleName() : message;
  }
}
