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

package de.gematik.refpopp.popp_server.hashdb;

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.certificates.X509CertificateParser;
import de.gematik.refpopp.popp_server.model.CheckResult;
import de.gematik.refpopp.popp_server.model.EgkEntry;
import de.gematik.refpopp.popp_server.model.EgkEntryState;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EgkHashValidationService {

  private final X509CertificateParser x509CertificateParser;
  private final EgkEntryPersistenceService egkEntryPersistenceService;

  public EgkHashValidationService(
      final X509CertificateParser x509CertificateParser,
      final EgkEntryPersistenceService egkEntryPersistenceService) {
    this.x509CertificateParser = x509CertificateParser;
    this.egkEntryPersistenceService = egkEntryPersistenceService;
  }

  /**
   * Handles the certificate hash pair by checking if it exists in the database and updating the
   * state accordingly.
   *
   * @param cvc The CVC certificate.
   * @param aut The AUT certificate.
   * @param communicationMode The communication mode (contact or contactless).
   * @param sessionId The session ID for logging purposes.
   * @return The result of the check (MATCH, BLOCKED, MISMATCH, UNKNOWN).
   */
  public CheckResult validateAndProcess(
      final byte[] cvc,
      final byte[] aut,
      final CommunicationMode communicationMode,
      final String sessionId) {
    final var cvcHash = computeSHA256(cvc, sessionId);
    final var autHash = computeSHA256(aut, sessionId);

    final var matchedEntry = egkEntryPersistenceService.findByCvcAndAutHash(cvcHash, autHash);

    return matchedEntry
        .map(entry -> handleExistingEntry(entry, sessionId))
        .orElseGet(() -> handleNewEntry(aut, cvcHash, autHash, communicationMode, sessionId));
  }

  private CheckResult handleNewEntry(
      final byte[] aut,
      final byte[] cvcHash,
      final byte[] autHash,
      final CommunicationMode mode,
      final String sessionId) {

    final LocalDateTime notAfter = getNotAfterFromX509(aut, sessionId);
    final EgkTransferEntry transferEntry =
        EgkTransferEntry.builder()
            .cvcHash(cvcHash)
            .autHash(autHash)
            .communicationMode(mode)
            .notAfter(notAfter)
            .build();

    return egkEntryPersistenceService.process(transferEntry, sessionId);
  }

  private LocalDateTime getNotAfterFromX509(final byte[] aut, final String sessionId) {
    final var x509Certificate = x509CertificateParser.parse(aut, sessionId);
    final var notAfterAsDate = x509Certificate.getNotAfter();
    return convertDateToLocalDateTime(notAfterAsDate);
  }

  private CheckResult handleExistingEntry(
      final EgkEntry cvcAndAutHashEntryOpt, final String sessionId) {
    log.info("| {} Found entry in the database: {}", sessionId, cvcAndAutHashEntryOpt);
    return cvcAndAutHashEntryOpt.getState() == EgkEntryState.BLOCKED
        ? CheckResult.BLOCKED
        : CheckResult.MATCH;
  }

  private LocalDateTime convertDateToLocalDateTime(final Date notAfter) {
    final var instant = notAfter.toInstant();
    return LocalDateTime.ofInstant(instant, ZoneId.of("Europe/Berlin"));
  }

  private byte[] computeSHA256(final byte[] data, final String sessionId) {
    try {
      final var md = MessageDigest.getInstance("SHA-256");
      return md.digest(data);
    } catch (final NoSuchAlgorithmException e) {
      throw new ScenarioException(sessionId, "SHA-256 algorithm not found", "errorcode");
    }
  }
}
