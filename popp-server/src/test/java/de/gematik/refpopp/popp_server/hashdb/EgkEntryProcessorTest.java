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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.model.EgkEntry;
import de.gematik.refpopp.popp_server.model.EgkEntryState;
import de.gematik.refpopp.popp_server.repository.CertHashRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EgkEntryProcessorTest {

  private CertHashRepository mockCertHashRepository;
  private EgkEntryProcessor sut;

  @BeforeEach
  void setUp() {
    mockCertHashRepository = mock(CertHashRepository.class);
    sut = new EgkEntryProcessor(mockCertHashRepository);
  }

  @Test
  void processWithMatchingAdHocEntryReturnsImportedEntry() {
    // given
    final var transferEntryMock = mock(EgkTransferEntry.class);
    final var adHocEntry = new EgkEntry();
    adHocEntry.setState(EgkEntryState.AD_HOC);
    when(mockCertHashRepository.findByCvcHashAndAutHash(any(), any()))
        .thenReturn(Optional.of(adHocEntry));

    // when
    final List<EgkEntry> result = sut.process(transferEntryMock, "session-id");

    // then
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getState()).isEqualTo(EgkEntryState.IMPORTED);
  }

  @Test
  void processWithMatchingImportedEntryReturnsEmptyList() {
    // given
    final var transferEntryMock = mock(EgkTransferEntry.class);
    final var importedEntry = new EgkEntry();
    importedEntry.setState(EgkEntryState.IMPORTED);
    when(mockCertHashRepository.findByCvcHashAndAutHash(any(), any()))
        .thenReturn(Optional.of(importedEntry));

    // when
    final List<EgkEntry> result = sut.process(transferEntryMock, "session-id");

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void processWithUnpairedEntryReturnsBlockedEntries() {
    // given
    final var transferEntry = mock(EgkTransferEntry.class);
    final var cvcEntry = new EgkEntry();
    final var autEntry = new EgkEntry();
    when(mockCertHashRepository.findByCvcHashAndAutHash(any(), any())).thenReturn(Optional.empty());
    when(mockCertHashRepository.findByCvcHash(any())).thenReturn(List.of(cvcEntry));
    when(mockCertHashRepository.findByAutHash(any())).thenReturn(List.of(autEntry));

    // when
    final List<EgkEntry> result = sut.process(transferEntry, "session-id");

    // then
    assertThat(result).hasSize(3);
    assertThat(result.get(0).getState()).isEqualTo(EgkEntryState.BLOCKED);
    assertThat(result.get(1).getState()).isEqualTo(EgkEntryState.BLOCKED);
    assertThat(result.get(2).getState()).isEqualTo(EgkEntryState.BLOCKED);
  }

  @Test
  void processWithUnpairedEntryAndNoCvcOrAutEntriesReturnsImportedEntry() {
    // given
    final var transferEntry = mock(EgkTransferEntry.class);
    when(mockCertHashRepository.findByCvcHashAndAutHash(any(), any())).thenReturn(Optional.empty());
    when(mockCertHashRepository.findByCvcHash(any())).thenReturn(List.of());
    when(mockCertHashRepository.findByAutHash(any())).thenReturn(List.of());

    // when
    final List<EgkEntry> result = sut.process(transferEntry, "session-id");

    // then
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getState()).isEqualTo(EgkEntryState.IMPORTED);
  }
}
