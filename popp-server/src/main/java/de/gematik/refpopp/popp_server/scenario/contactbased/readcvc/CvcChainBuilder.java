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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Builds the configured CVC chain required for a contact-based card scenario. */
@Component
public class CvcChainBuilder {

  private static final HexFormat HEX_FORMAT = HexFormat.of();

  private final CertificateProviderService certificateProviderService;
  private final ScenarioResultFinder scenarioResultFinder;
  private final KeyIdentifierExtractor keyIdentifierExtractor;

  /**
   * Creates a CVC chain builder using the configured certificate source and scenario result data.
   *
   * @param certificateProviderService provides configured CVCs and the CVC directory
   * @param scenarioResultFinder finds relevant scenario result steps
   * @param keyIdentifierExtractor extracts known key identifiers from card responses
   */
  public CvcChainBuilder(
      final CertificateProviderService certificateProviderService,
      final ScenarioResultFinder scenarioResultFinder,
      final KeyIdentifierExtractor keyIdentifierExtractor) {
    this.certificateProviderService = certificateProviderService;
    this.scenarioResultFinder = scenarioResultFinder;
    this.keyIdentifierExtractor = keyIdentifierExtractor;
  }

  /**
   * Builds the configured CVC chain up to a certificate already known by the card.
   *
   * @param sessionId the identifier of the current session
   * @param scenarioResult the result containing the card's public key identifiers
   * @return the ordered CVC chain to provide to the card
   * @throws IllegalStateException if no configured CVC chain reaches a key known by the card
   */
  public List<CvCertificate> build(final String sessionId, final ScenarioResult scenarioResult) {
    final var keyIdentifierSet =
        extractKeyIdentifiers(
            sessionId, scenarioResult, StepId.RETRIEVE_PUBLIC_KEY_IDENTIFIERS.value());

    return buildConfiguredServiceChain(
        certificateProviderService.getCvEndEntityCertificate(), keyIdentifierSet);
  }

  private List<CvCertificate> buildConfiguredServiceChain(
      final CvCertificate serviceEndEntityCvc, final Set<String> knownKeyIdentifiers) {
    if (knownKeyIdentifiers.contains(chrHex(serviceEndEntityCvc))) {
      return List.of();
    }

    final var pendingChains = new ArrayDeque<List<CvCertificate>>();
    pendingChains.add(List.of(serviceEndEntityCvc));
    final var visitedAuthorities = new HashSet<String>();

    while (!pendingChains.isEmpty()) {
      final var chain = pendingChains.removeFirst();
      final var current = chain.getLast();
      if (knownKeyIdentifiers.contains(
          HEX_FORMAT.formatHex(CvCertificateSupport.carBytes(current)))) {
        return List.copyOf(chain);
      }

      final var authority = CvCertificateSupport.car(current);
      if (!visitedAuthorities.add(authority)) {
        continue;
      }
      for (final var issuer :
          certificateProviderService.getCvcDirectory().findAllByChr(authority)) {
        final var candidateChain = new ArrayList<>(chain);
        candidateChain.add(issuer);
        pendingChains.addLast(candidateChain);
      }
    }

    throw new IllegalStateException(
        "No configured CVC chain reaches a public key known by the card");
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
