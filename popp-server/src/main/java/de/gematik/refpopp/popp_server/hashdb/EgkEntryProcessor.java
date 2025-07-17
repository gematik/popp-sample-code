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

import de.gematik.refpopp.popp_server.model.EgkEntry;
import de.gematik.refpopp.popp_server.model.EgkEntryState;
import de.gematik.refpopp.popp_server.repository.CertHashRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EgkEntryProcessor {
  private final CertHashRepository certHashRepository;

  public EgkEntryProcessor(final CertHashRepository certHashRepository) {
    this.certHashRepository = certHashRepository;
  }

  public List<EgkEntry> process(final EgkTransferEntry entry, final String sessionId) {
    final var byCvcAndAutHash =
        certHashRepository.findByCvcHashAndAutHash(entry.getCvcHash(), entry.getAutHash());

    if (byCvcAndAutHash.isPresent()) {
      final var matchedEntry = byCvcAndAutHash.get();
      if (matchedEntry.getState() == EgkEntryState.AD_HOC) {
        return List.of(toImportEntry(matchedEntry));
      }
    } else {
      return handleUnpairedEntry(entry, sessionId);
    }
    return List.of();
  }

  private EgkEntry toImportEntry(final EgkEntry entry) {
    final var newEgkEntry = new EgkEntry(entry);
    newEgkEntry.setState(EgkEntryState.IMPORTED);
    return newEgkEntry;
  }

  private List<EgkEntry> handleUnpairedEntry(
      final EgkTransferEntry egkTransferEntry, final String sessionId) {
    log.debug(
        "| sessionId {} Entry does not exist in the database: {}.", sessionId, egkTransferEntry);
    final var cvcEntries = certHashRepository.findByCvcHash(egkTransferEntry.getCvcHash());
    final var autEntries = certHashRepository.findByAutHash(egkTransferEntry.getAutHash());
    final List<EgkEntry> entries = new ArrayList<>();
    if (cvcEntries.isEmpty() && autEntries.isEmpty()) {
      return List.of(buildNewEgkEntry(EgkEntryState.IMPORTED, egkTransferEntry));
    } else {
      if (!cvcEntries.isEmpty()) {
        entries.addAll(toBlockedEntries(cvcEntries));
      }
      if (!autEntries.isEmpty()) {
        entries.addAll(toBlockedEntries(autEntries));
      }
      entries.add(buildNewEgkEntry(BLOCKED, egkTransferEntry));
    }
    return entries;
  }

  private List<EgkEntry> toBlockedEntries(final List<EgkEntry> entries) {
    return entries.stream()
        .map(
            original -> {
              final var copy = new EgkEntry(original);
              copy.setState(EgkEntryState.BLOCKED);
              return copy;
            })
        .toList();
  }

  private EgkEntry buildNewEgkEntry(
      final EgkEntryState state, final EgkTransferEntry egkTransferEntry) {
    return new EgkEntry(
        egkTransferEntry.getCvcHash(),
        egkTransferEntry.getAutHash(),
        state,
        egkTransferEntry.getNotAfter());
  }
}
