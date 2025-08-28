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

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.hashdb.EgkHashValidationService;
import de.gematik.refpopp.popp_server.model.CheckResult;
import de.gematik.refpopp.popp_server.scenario.common.cvc.CvcProcessor;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultProcessor;
import de.gematik.refpopp.popp_server.scenario.common.token.JwtTokenCreator;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509CertificateProcessor;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509Data;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import de.gematik.smartcards.crypto.EcPublicKeyImpl;
import de.gematik.smartcards.g2icc.cvc.Cvc;
import java.math.BigInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

  @Value("${scenario-names.auth-g2}")
  private String authG2ScenarioName;

  @Value("${step-names.read-end-entity-cv-certificate}")
  private String readEndEntityCvCertificateStepName;

  @Value("${step-names.read-sub-ca-cv-certificate}")
  private String readSubCaCvCertificateStepName;

  @Value("${step-names.read-x509}")
  private String readX509StepName;

  @Value("${step-names.internal-authentication}")
  private String internalAuthenticationStepName;

  public AuthG2ScenarioResultProcessor(
      final CvcProcessor cvcProcessor,
      final ScenarioResultFinder scenarioResultFinder,
      final X509CertificateProcessor x509CertificateProcessor,
      final JwtTokenCreator tokenCreator,
      final SessionAccessor sessionAccessor,
      final EgkHashValidationService egkHashValidationService) {
    this.cvcProcessor = cvcProcessor;
    this.scenarioResultFinder = scenarioResultFinder;

    this.x509CertificateProcessor = x509CertificateProcessor;
    this.tokenCreator = tokenCreator;
    this.sessionAccessor = sessionAccessor;
    this.egkHashValidationService = egkHashValidationService;
  }

  @Override
  public String getScenarioName() {
    return authG2ScenarioName;
  }

  @Override
  public void process(final String sessionId, final ScenarioResult scenarioResult) {
    final var endEntityCvc =
        cvcProcessor.createAndValidateCvc(
            sessionId, scenarioResult, readEndEntityCvCertificateStepName);
    cvcProcessor.createAndValidateCvcCa(sessionId, scenarioResult, readSubCaCvCertificateStepName);
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
            sessionId, scenarioResult.scenarioResultSteps(), readX509StepName);
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
      final String sessionId, final ScenarioResult scenarioResult, final Cvc endEntityCvc) {
    final var signature =
        scenarioResultFinder
            .find(sessionId, scenarioResult.scenarioResultSteps(), internalAuthenticationStepName)
            .data();
    final var nonce = sessionAccessor.getNonce(sessionId);
    final var verified = verify(endEntityCvc.getPublicKey(), nonce, signature);

    if (!verified) {
      throw new ScenarioException(sessionId, "Signature of nonce is not valid", "errorCode");
    }
  }

  private boolean verify(
      final EcPublicKeyImpl publicKey, final byte[] nonce, final byte[] signature) {
    final var algId = "00";
    final var tau = new BigInteger(1, nonce).shiftLeft(8).add(new BigInteger(algId, 16));
    return publicKey.verifyEcdsa(tau, signature);
  }
}
