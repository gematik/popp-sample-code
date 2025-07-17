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

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.hashdb.EgkHashValidationService;
import de.gematik.refpopp.popp_server.model.CheckResult;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultProcessor;
import de.gematik.refpopp.popp_server.scenario.common.token.JwtTokenCreator;
import de.gematik.refpopp.popp_server.scenario.common.x509.X509CertificateProcessor;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReadX509ScenarioResultProcessor implements ScenarioResultProcessor {

  private final ScenarioResultFinder scenarioResultFinder;
  private final X509CertificateProcessor x509CertificateProcessor;
  private final JwtTokenCreator tokenCreator;
  private final SessionAccessor sessionAccessor;
  private final EgkHashValidationService egkHashValidationService;

  @Value("${scenario-names.sce-read-x509}")
  private String readX509ScenarioName;

  @Value("${step-names.read-ef-c-ch-aut-e256}")
  private String contentOfEfCChAutE256;

  public ReadX509ScenarioResultProcessor(
      final ScenarioResultFinder scenarioResultFinder,
      final X509CertificateProcessor x509CertificateProcessor,
      final JwtTokenCreator tokenCreator,
      final SessionAccessor sessionAccessor,
      final EgkHashValidationService egkHashValidationService) {
    this.scenarioResultFinder = scenarioResultFinder;
    this.x509CertificateProcessor = x509CertificateProcessor;
    this.tokenCreator = tokenCreator;
    this.sessionAccessor = sessionAccessor;
    this.egkHashValidationService = egkHashValidationService;
  }

  @Override
  public void process(final String sessionId, final ScenarioResult scenarioResult) {
    final var aut =
        scenarioResultFinder
            .find(sessionId, scenarioResult.scenarioResultSteps(), contentOfEfCChAutE256)
            .data();
    final byte[] cvc = sessionAccessor.getCvc(sessionId);
    checkCertificatePair(sessionId, cvc, aut);
    final var x509Data = x509CertificateProcessor.extractCertificateData(sessionId, aut);
    final var poppToken = tokenCreator.createPoppToken(x509Data, sessionId);
    sessionAccessor.storeJwtToken(sessionId, poppToken);
    log.info("| {} Generated PoPP-Token for the client: {}", sessionId, poppToken);
  }

  @Override
  public String getScenarioName() {
    return readX509ScenarioName;
  }

  private void checkCertificatePair(final String sessionId, final byte[] cvc, final byte[] aut) {
    final var checkResult =
        egkHashValidationService.validateAndProcess(cvc, aut, CommunicationMode.CONTACT, sessionId);
    if (checkResult == CheckResult.BLOCKED || checkResult == CheckResult.MISMATCH) {
      throw new ScenarioException(sessionId, "InvalidCertificatePairT1", "errorCode");
    }
  }
}
