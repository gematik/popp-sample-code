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

import static de.gematik.refpopp.popp_server.model.EgkEntryState.BLOCKED;

import de.gematik.refpopp.popp_server.model.CheckResult;
import de.gematik.refpopp.popp_server.model.EgkEntry;
import de.gematik.refpopp.popp_server.model.EgkEntryState;
import de.gematik.refpopp.popp_server.repository.CertHashRepository;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EgkEntryPersistenceService {
  private final CertHashRepository certHashRepository;

  public EgkEntryPersistenceService(final CertHashRepository certHashRepository) {
    this.certHashRepository = certHashRepository;
  }

  public Optional<EgkEntry> findByCvcAndAutHash(final byte[] cvcHash, final byte[] autHash) {
    return certHashRepository.findByCvcHashAndAutHash(cvcHash, autHash);
  }

  public CheckResult process(final EgkTransferEntry egkTransferEntry, final String sessionId) {
    if (egkTransferEntry.getCommunicationMode() == CommunicationMode.CONTACT) {
      return processUnpairedContact(egkTransferEntry, sessionId);
    }
    return processUnpairedContactless(egkTransferEntry);
  }

  private void blockEntries(final List<EgkEntry> entries, final String sessionId) {
    final var blocked =
        entries.stream()
            .map(
                original -> {
                  final var copy = new EgkEntry(original);
                  copy.setState(EgkEntryState.BLOCKED);
                  return copy;
                })
            .toList();
    certHashRepository.saveAll(blocked);
    log.debug("| {} Updated entries to BLOCKED: {}", sessionId, entries);
  }

  private CheckResult processUnpairedContact(
      final EgkTransferEntry egkTransferEntry, final String sessionId) {
    log.debug(
        "| sessionId {} Entry does not exist in the database: {}.", sessionId, egkTransferEntry);
    final var cvcEntries = certHashRepository.findByCvcHash(egkTransferEntry.getCvcHash());
    final var autEntries = certHashRepository.findByAutHash(egkTransferEntry.getAutHash());
    if (cvcEntries.isEmpty() && autEntries.isEmpty()) {
      saveNewEntry(EgkEntryState.AD_HOC, egkTransferEntry, sessionId);
      return CheckResult.UNKNOWN;
    } else {
      if (!cvcEntries.isEmpty()) {
        blockEntries(cvcEntries, sessionId);
      }
      if (!autEntries.isEmpty()) {
        blockEntries(autEntries, sessionId);
      }
      saveNewEntry(BLOCKED, egkTransferEntry, sessionId);
    }
    return CheckResult.MISMATCH;
  }

  private void saveNewEntry(
      final EgkEntryState state, final EgkTransferEntry egkTransferEntry, final String sessionId) {
    final var newEntry =
        new EgkEntry(
            egkTransferEntry.getCvcHash(),
            egkTransferEntry.getAutHash(),
            state,
            egkTransferEntry.getNotAfter());
    certHashRepository.save(newEntry);
    log.debug("| {} Added new entry to the database: {}", sessionId, newEntry);
  }

  private CheckResult processUnpairedContactless(final EgkTransferEntry egkTransferEntry) {
    final var cvcEntries = certHashRepository.findByCvcHash(egkTransferEntry.getCvcHash());
    final var autEntries = certHashRepository.findByAutHash(egkTransferEntry.getAutHash());
    if (cvcEntries.isEmpty() && autEntries.isEmpty()) {
      return CheckResult.UNKNOWN;
    }
    return CheckResult.MISMATCH;
  }
}
