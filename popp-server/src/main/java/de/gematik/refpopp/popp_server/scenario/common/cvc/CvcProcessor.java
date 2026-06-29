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

package de.gematik.refpopp.popp_server.scenario.common.cvc;

import de.gematik.openhealth.asn1.CvCertificate;
import de.gematik.openhealth.crypto.CryptoException;
import de.gematik.poppcommons.api.enums.BdeErrorCode;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.certificates.CertificateProviderService;
import de.gematik.refpopp.popp_server.certificates.CvCertificateSupport;
import de.gematik.refpopp.popp_server.certificates.CvcChainValidator;
import de.gematik.refpopp.popp_server.certificates.CvcFactory;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import org.springframework.stereotype.Component;

@Component
public class CvcProcessor {

  private final CvcFactory cvcFactory;
  private final ScenarioResultFinder scenarioResultFinder;
  private final SessionAccessor sessionAccessor;
  private final CertificateProviderService certificateProviderService;
  private final CvcChainValidator cvcChainValidator;

  public CvcProcessor(
      final CvcFactory cvcFactory,
      final ScenarioResultFinder scenarioResultFinder,
      final SessionAccessor sessionAccessor,
      final CertificateProviderService certificateProviderService,
      final CvcChainValidator cvcChainValidator) {
    this.cvcFactory = cvcFactory;
    this.scenarioResultFinder = scenarioResultFinder;
    this.sessionAccessor = sessionAccessor;
    this.certificateProviderService = certificateProviderService;
    this.cvcChainValidator = cvcChainValidator;
  }

  public CvCertificate createAndValidateCvc(
      final String sessionId, final ScenarioResult scenarioResult, final StepId stepId) {
    final var cvc = createCvc(sessionId, scenarioResult, stepId);
    validateCvc(sessionId, cvc);
    return cvc;
  }

  public CvCertificate createAndValidateCvcCa(
      final String sessionId, final ScenarioResult scenarioResult, final StepId stepId) {
    final var cvc = createCvcCa(sessionId, scenarioResult, stepId);
    validateCvc(sessionId, cvc);
    return cvc;
  }

  private CvCertificate createCvc(
      final String sessionId, final ScenarioResult scenarioResult, final StepId stepId) {
    final var result =
        scenarioResultFinder.find(sessionId, scenarioResult.scenarioResultSteps(), stepId);
    sessionAccessor.storeCvc(sessionId, result.data());

    return cvcFactory.create(result.data());
  }

  private CvCertificate createCvcCa(
      final String sessionId, final ScenarioResult scenarioResult, final StepId stepId) {
    final var result =
        scenarioResultFinder.find(sessionId, scenarioResult.scenarioResultSteps(), stepId);
    sessionAccessor.storeCvcCA(sessionId, result.data());

    return cvcFactory.create(result.data());
  }

  private void validateCvc(final String sessionId, final CvCertificate cvc) {
    final var issuer =
        certificateProviderService.findIdentityCvcByChr(CvCertificateSupport.car(cvc));
    try {
      cvcChainValidator.validate(cvc, issuer);
    } catch (final CryptoException e) {
      throw new ScenarioException(
          sessionId,
          "Failed to validate CVC chain: " + e.getMessage(),
          BdeErrorCode.INVALID_END_ENTITY_CVC);
    }
  }
}
