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
import de.gematik.refpopp.popp_server.certificates.CertificateProviderService;
import de.gematik.refpopp.popp_server.certificates.CvCertificateSupport;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CvcChainBuilder {

  private static final HexFormat HEX_FORMAT = HexFormat.of();

  private final CertificateProviderService certificateProviderService;
  private final ScenarioResultFinder scenarioResultFinder;
  private final KeyIdentifierExtractor keyIdentifierExtractor;

  public CvcChainBuilder(
      final CertificateProviderService certificateProviderService,
      final ScenarioResultFinder scenarioResultFinder,
      final KeyIdentifierExtractor keyIdentifierExtractor) {
    this.certificateProviderService = certificateProviderService;
    this.scenarioResultFinder = scenarioResultFinder;
    this.keyIdentifierExtractor = keyIdentifierExtractor;
  }

  public List<CvCertificate> build(final String sessionId, final ScenarioResult scenarioResult) {
    final var keyIdentifierSet =
        extractKeyIdentifiers(
            sessionId, scenarioResult, StepId.RETRIEVE_PUBLIC_KEY_IDENTIFIERS.value());

    return buildConfiguredServiceChain(
        certificateProviderService.getCvEndEntityCertificate(), keyIdentifierSet);
  }

  private List<CvCertificate> buildConfiguredServiceChain(
      final CvCertificate serviceEndEntityCvc, final Set<String> knownKeyIdentifiers) {
    final var chain = new ArrayList<CvCertificate>();
    final var visited = new HashSet<String>();
    var current = serviceEndEntityCvc;

    while (current != null) {
      final var chr = CvCertificateSupport.chr(current);
      final var car = CvCertificateSupport.car(current);
      if (knownKeyIdentifiers.contains(chrHex(current)) || car.equals(chr)) {
        current = null;
      } else {
        if (!visited.add(chr)) {
          throw new IllegalStateException("Loop detected in configured CVC chain at CHR " + chr);
        }
        chain.add(current);
        current = certificateProviderService.getCvcDirectory().findByChr(car).orElse(null);
      }
    }

    return chain;
  }

  private String chrHex(final CvCertificate certificate) {
    return HEX_FORMAT.formatHex(CvCertificateSupport.chrBytes(certificate));
  }

  private Set<String> extractKeyIdentifiers(
      final String sessionId, final ScenarioResult scenarioResult, final String stepName) {
    final var result =
        scenarioResultFinder.find(sessionId, scenarioResult.scenarioResultSteps(), stepName);
    return keyIdentifierExtractor.extract(result.data());
  }
}
