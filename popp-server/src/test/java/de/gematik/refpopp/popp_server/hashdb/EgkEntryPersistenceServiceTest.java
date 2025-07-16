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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.model.CheckResult;
import de.gematik.refpopp.popp_server.model.EgkEntry;
import de.gematik.refpopp.popp_server.model.EgkEntryState;
import de.gematik.refpopp.popp_server.repository.CertHashRepository;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EgkEntryPersistenceServiceTest {
  private EgkEntryPersistenceService sut;
  private CertHashRepository certHashRepositoryMock;

  @BeforeEach
  void setUp() {
    certHashRepositoryMock = mock(CertHashRepository.class);
    sut = new EgkEntryPersistenceService(certHashRepositoryMock);
  }

  @Test
  void findByCvcAndAutHashReturnsEmptyWhenNoEntriesFound() {
    // given
    final byte[] cvcHash = new byte[1];
    final byte[] autHash = new byte[2];
    when(certHashRepositoryMock.findByCvcHashAndAutHash(cvcHash, autHash))
        .thenReturn(Optional.empty());

    // when
    final var result = sut.findByCvcAndAutHash(cvcHash, autHash);

    // then
    assertThat(result).isEmpty();
    verify(certHashRepositoryMock).findByCvcHashAndAutHash(cvcHash, autHash);
  }

  @Test
  void processUnpairedContactlessReturnsUnknownWhenNoEntriesFound() {
    // given
    final var egkTransferEntry =
        EgkTransferEntry.builder()
            .cvcHash(new byte[1])
            .autHash(new byte[2])
            .communicationMode(CommunicationMode.CONTACTLESS)
            .build();
    when(certHashRepositoryMock.findByCvcHash(egkTransferEntry.getCvcHash())).thenReturn(List.of());
    when(certHashRepositoryMock.findByAutHash(egkTransferEntry.getAutHash())).thenReturn(List.of());

    // when
    final var result = sut.process(egkTransferEntry, "sessionId");

    // then
    assertThat(result).isEqualTo(CheckResult.UNKNOWN);
  }

  @Test
  void processUnpairedContactlessReturnsMismatchWhenCvcEntriesFound() {
    // given
    final var egkTransferEntry =
        EgkTransferEntry.builder()
            .cvcHash(new byte[1])
            .autHash(new byte[2])
            .communicationMode(CommunicationMode.CONTACTLESS)
            .build();
    when(certHashRepositoryMock.findByCvcHash(egkTransferEntry.getCvcHash()))
        .thenReturn(List.of(new EgkEntry()));

    // when
    final var result = sut.process(egkTransferEntry, "sessionId");

    // then
    assertThat(result).isEqualTo(CheckResult.MISMATCH);
  }

  @Test
  void processUnpairedContactlessReturnsMismatchWhenAutEntriesFound() {
    // given
    final var egkTransferEntry =
        EgkTransferEntry.builder()
            .cvcHash(new byte[1])
            .autHash(new byte[2])
            .communicationMode(CommunicationMode.CONTACTLESS)
            .build();
    when(certHashRepositoryMock.findByAutHash(egkTransferEntry.getAutHash()))
        .thenReturn(List.of(new EgkEntry()));

    // when
    final var result = sut.process(egkTransferEntry, "sessionId");

    // then
    assertThat(result).isEqualTo(CheckResult.MISMATCH);
  }

  @Test
  void processUnpairedContactReturnsUnknownWhenNoEntriesFound() {
    // given
    final var egkTransferEntry =
        EgkTransferEntry.builder()
            .cvcHash(new byte[1])
            .autHash(new byte[2])
            .communicationMode(CommunicationMode.CONTACT)
            .build();
    when(certHashRepositoryMock.findByCvcHash(egkTransferEntry.getCvcHash())).thenReturn(List.of());
    when(certHashRepositoryMock.findByAutHash(egkTransferEntry.getAutHash())).thenReturn(List.of());
    final ArgumentCaptor<EgkEntry> entryCaptor = ArgumentCaptor.forClass(EgkEntry.class);

    // when
    final var result = sut.process(egkTransferEntry, "sessionId");

    // then
    assertThat(result).isEqualTo(CheckResult.UNKNOWN);
    verify(certHashRepositoryMock).save(entryCaptor.capture());
    final var savedEntry = entryCaptor.getValue();
    assertThat(savedEntry.getState()).isEqualTo(EgkEntryState.AD_HOC);
  }

  @Test
  void processUnpairedContactBlocksEntriesWhenCvcEntriesFound() {
    // given
    final var egkTransferEntry =
        EgkTransferEntry.builder()
            .cvcHash(new byte[1])
            .autHash(new byte[2])
            .communicationMode(CommunicationMode.CONTACT)
            .build();
    when(certHashRepositoryMock.findByCvcHash(egkTransferEntry.getCvcHash()))
        .thenReturn(List.of(new EgkEntry()));
    final ArgumentCaptor<List<EgkEntry>> listCaptor = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<EgkEntry> entryCaptor = ArgumentCaptor.forClass(EgkEntry.class);

    // when
    final var result = sut.process(egkTransferEntry, "sessionId");

    // then
    assertThat(result).isEqualTo(CheckResult.MISMATCH);
    verify(certHashRepositoryMock).saveAll(listCaptor.capture());
    final var blockedList = listCaptor.getValue();
    assertThat(blockedList)
        .hasSize(1)
        .allSatisfy(e -> assertThat(e.getState()).isEqualTo(EgkEntryState.BLOCKED));
    verify(certHashRepositoryMock).save(entryCaptor.capture());
    final var savedEntry = entryCaptor.getValue();
    assertThat(savedEntry.getState()).isEqualTo(EgkEntryState.BLOCKED);
  }

  @Test
  void processUnpairedContactBlocksEntriesWhenAutEntriesFound() {
    // given
    final var egkTransferEntry =
        EgkTransferEntry.builder()
            .cvcHash(new byte[1])
            .autHash(new byte[2])
            .communicationMode(CommunicationMode.CONTACT)
            .build();
    when(certHashRepositoryMock.findByCvcHash(egkTransferEntry.getCvcHash())).thenReturn(List.of());
    when(certHashRepositoryMock.findByAutHash(egkTransferEntry.getAutHash()))
        .thenReturn(List.of(new EgkEntry()));
    final ArgumentCaptor<List<EgkEntry>> listCaptor = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<EgkEntry> entryCaptor = ArgumentCaptor.forClass(EgkEntry.class);

    // when
    final var result = sut.process(egkTransferEntry, "sessionId");

    // then
    assertThat(result).isEqualTo(CheckResult.MISMATCH);
    verify(certHashRepositoryMock).saveAll(listCaptor.capture());
    final var blockedList = listCaptor.getValue();
    assertThat(blockedList)
        .hasSize(1)
        .allSatisfy(e -> assertThat(e.getState()).isEqualTo(EgkEntryState.BLOCKED));
    verify(certHashRepositoryMock).save(entryCaptor.capture());
    final var savedEntry = entryCaptor.getValue();
    assertThat(savedEntry.getState()).isEqualTo(EgkEntryState.BLOCKED);
  }
}
