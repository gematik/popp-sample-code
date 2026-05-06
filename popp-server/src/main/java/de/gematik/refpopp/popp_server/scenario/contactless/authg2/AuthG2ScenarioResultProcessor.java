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
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultProcessor;
import de.gematik.refpopp.popp_server.scenario.common.token.JwtTokenCreator;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509CertificateProcessor;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509Data;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuthG2ScenarioResultProcessor implements ScenarioResultProcessor {

  private final CvcProcessor cvcProcessor;
  private final ScenarioResultFinder scenarioResultFinder;
  private final X509CertificateProcessor x509CertificateProcessor;
  private final JwtTokenCreator tokenCreator;
  private final SessionAccessor sessionAccessor;
  private final EgkHashValidationService egkHashValidationService;
  private final CvcSignatureVerifier signatureVerifier;

  public AuthG2ScenarioResultProcessor(
      final CvcProcessor cvcProcessor,
      final ScenarioResultFinder scenarioResultFinder,
      final X509CertificateProcessor x509CertificateProcessor,
      final JwtTokenCreator tokenCreator,
      final SessionAccessor sessionAccessor,
      final EgkHashValidationService egkHashValidationService,
      final CvcSignatureVerifier signatureVerifier) {
    this.cvcProcessor = cvcProcessor;
    this.scenarioResultFinder = scenarioResultFinder;
    this.x509CertificateProcessor = x509CertificateProcessor;
    this.tokenCreator = tokenCreator;
    this.sessionAccessor = sessionAccessor;
    this.egkHashValidationService = egkHashValidationService;
    this.signatureVerifier = signatureVerifier;
  }

  @Override
  public ScenarioId getScenarioId() {
    return ScenarioId.AUTH_G2;
  }

  @Override
  public void process(final String sessionId, final ScenarioResult scenarioResult) {
    final var endEntityCvc =
        cvcProcessor.createAndValidateCvc(
            sessionId, scenarioResult, StepId.READ_END_ENTITY_CV_CERTIFICATE);
    cvcProcessor.createAndValidateCvcCa(
        sessionId, scenarioResult, StepId.READ_SUB_CA_CV_CERTIFICATE);
    verifySignatureOfNonce(sessionId, scenarioResult, endEntityCvc);
    final var x509Data = extractDataFromX509(sessionId, scenarioResult);
    checkCertificatePair(sessionId);
    final var poppToken = tokenCreator.createPoppToken(x509Data, sessionId);
    sessionAccessor.storeJwtToken(sessionId, poppToken);
    log.info("| {} Generated PoPP-Token for the client: {}", sessionId, poppToken);
  }

  private X509Data extractDataFromX509(
      final String sessionId, final ScenarioResult scenarioResult) {
    final var x509ResultStep =
        scenarioResultFinder.find(
            sessionId, scenarioResult.scenarioResultSteps(), StepId.READ_X509);
    sessionAccessor.storeAut(sessionId, x509ResultStep.data());
    return x509CertificateProcessor.extractCertificateData(sessionId, x509ResultStep.data());
  }

  private void checkCertificatePair(final String sessionId) {
    final var cvc = sessionAccessor.getCvc(sessionId);
    final var aut = sessionAccessor.getAut(sessionId);
    final var checkResult =
        egkHashValidationService.validateAndProcess(
            cvc, aut, CommunicationMode.CONTACTLESS, sessionId);
    if (checkResult == CheckResult.MISMATCH || checkResult == CheckResult.BLOCKED) {
      throw new ScenarioException(sessionId, "InvalidCertificatePairContactless", "errorCode");
    } else if (checkResult == CheckResult.UNKNOWN) {
      throw new ScenarioException(sessionId, "UnknownCertificates", "errorCode");
    }
  }

  private void verifySignatureOfNonce(
      final String sessionId,
      final ScenarioResult scenarioResult,
      final CvCertificate endEntityCvc) {
    final var signature =
        scenarioResultFinder
            .find(sessionId, scenarioResult.scenarioResultSteps(), StepId.INTERNAL_AUTHENTICATION)
            .data();
    final var nonce = sessionAccessor.getNonce(sessionId);
    final var verified = verify(sessionId, endEntityCvc, nonce, signature);

    if (!verified) {
      throw new ScenarioException(sessionId, "Signature of nonce is not valid", "errorCode");
    }
  }

  private boolean verify(
      final String sessionId,
      final CvCertificate certificate,
      final byte[] nonce,
      final byte[] signature) {
    final var tau = new byte[nonce.length + 1];
    System.arraycopy(nonce, 0, tau, 0, nonce.length);
    try {
      return signatureVerifier.verifyCvcEcdsaValueSignature(certificate, tau, signature);
    } catch (final IllegalStateException e) {
      throw new ScenarioException(
          sessionId, "Failed to verify nonce signature: " + e.getMessage(), "errorCode");
    }
  }
}
