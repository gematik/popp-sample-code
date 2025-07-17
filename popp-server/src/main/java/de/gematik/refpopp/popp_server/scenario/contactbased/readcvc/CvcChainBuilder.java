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

import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.smartcards.g2icc.cos.SecureMessagingConverterSoftware;
import de.gematik.smartcards.g2icc.cvc.Cvc;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CvcChainBuilder {

  private final SecureMessagingConverterSoftware secureMessagingConverterSoftware;
  private final ScenarioResultFinder scenarioResultFinder;
  private final KeyIdentifierExtractor keyIdentifierExtractor;

  @Value("${step-names.retrieve-public-key-identifiers}")
  private String retrievePublicKeyIdsStepName;

  public CvcChainBuilder(
      final SecureMessagingConverterSoftware secureMessagingConverterSoftware,
      final ScenarioResultFinder scenarioResultFinder,
      final KeyIdentifierExtractor keyIdentifierExtractor) {
    this.secureMessagingConverterSoftware = secureMessagingConverterSoftware;
    this.scenarioResultFinder = scenarioResultFinder;
    this.keyIdentifierExtractor = keyIdentifierExtractor;
  }

  public List<Cvc> build(
      final String sessionId, final ScenarioResult scenarioResult, final Cvc endEntityCvc) {
    final var keyIdentifierSet =
        extractKeyIdentifiers(sessionId, scenarioResult, retrievePublicKeyIdsStepName);

    final var cvcChain = secureMessagingConverterSoftware.importCvc(endEntityCvc);
    return trimCvcChain(cvcChain, keyIdentifierSet);
  }

  private Set<String> extractKeyIdentifiers(
      final String sessionId, final ScenarioResult scenarioResult, final String stepName) {
    final var result =
        scenarioResultFinder.find(sessionId, scenarioResult.scenarioResultSteps(), stepName);
    return keyIdentifierExtractor.extract(result.data());
  }

  private List<Cvc> trimCvcChain(final List<Cvc> cvcChain, final Set<String> keyIdSet) {
    final List<Cvc> localCvcChain = new ArrayList<>(cvcChain);

    while (!localCvcChain.isEmpty()) {
      final var lastCvc = localCvcChain.getLast();
      final var lastChr = lastCvc.getChr();

      if (keyIdSet.contains(lastChr)) {
        localCvcChain.removeLast();
      } else {
        break;
      }
    }

    return localCvcChain;
  }
}
