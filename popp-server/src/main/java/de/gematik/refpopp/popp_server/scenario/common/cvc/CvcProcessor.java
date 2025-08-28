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

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.certificates.CvcFactory;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import de.gematik.smartcards.g2icc.cvc.Cvc;
import de.gematik.smartcards.g2icc.cvc.Cvc.SignatureStatus;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class CvcProcessor {

  private final CvcFactory cvcFactory;
  private final ScenarioResultFinder scenarioResultFinder;
  private final SessionAccessor sessionAccessor;

  public CvcProcessor(
      final CvcFactory cvcFactory,
      final ScenarioResultFinder scenarioResultFinder,
      final SessionAccessor sessionAccessor) {
    this.cvcFactory = cvcFactory;
    this.scenarioResultFinder = scenarioResultFinder;
    this.sessionAccessor = sessionAccessor;
  }

  public Cvc createAndValidateCvc(
      final String sessionId, final ScenarioResult scenarioResult, final String stepName) {
    final var cvc = createCvc(sessionId, scenarioResult, stepName);
    checkExpirationDate(sessionId, cvc);
    validateCvcSignature(sessionId, cvc);
    return cvc;
  }

  public Cvc createAndValidateCvcCa(
      final String sessionId, final ScenarioResult scenarioResult, final String stepName) {
    final var cvc = createCvcCa(sessionId, scenarioResult, stepName);
    checkExpirationDate(sessionId, cvc);
    validateCvcSignature(sessionId, cvc);
    return cvc;
  }

  private Cvc createCvc(
      final String sessionId, final ScenarioResult scenarioResult, final String stepName) {
    final var result =
        scenarioResultFinder.find(sessionId, scenarioResult.scenarioResultSteps(), stepName);
    sessionAccessor.storeCvc(sessionId, result.data());

    return cvcFactory.create(result.data());
  }

  private Cvc createCvcCa(
      final String sessionId, final ScenarioResult scenarioResult, final String stepName) {
    final var result =
        scenarioResultFinder.find(sessionId, scenarioResult.scenarioResultSteps(), stepName);
    sessionAccessor.storeCvcCA(sessionId, result.data());

    return cvcFactory.create(result.data());
  }

  private void validateCvcSignature(final String sessionId, final Cvc cvc) {
    if (cvc.getSignatureStatus() != SignatureStatus.SIGNATURE_VALID) {
      if (cvc.isEndEntity()) {
        throw new ScenarioException(
            sessionId, "Signature of End-Entity CVC is not valid", "errorCode");
      } else {
        throw new ScenarioException(sessionId, "Signature of SubCA CVC is not valid", "errorCode");
      }
    }
  }

  private void checkExpirationDate(final String sessionId, final Cvc cvc) {
    final var endEntityExpireDate = cvc.getCxd().getDate();
    if (isExpired(endEntityExpireDate)) {
      if (cvc.isEndEntity()) {
        throw new ScenarioException(sessionId, "End-Entity CVC is expired", "errorCode");
      } else {
        throw new ScenarioException(sessionId, "SubCA CVC is expired", "errorCode");
      }
    }
  }

  private boolean isExpired(final LocalDate date) {
    return LocalDate.now().isAfter(date);
  }
}
