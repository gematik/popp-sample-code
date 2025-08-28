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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.exceptions.ImportDataException;
import de.gematik.refpopp.popp_server.model.EgkEntry;
import de.gematik.refpopp.popp_server.model.EgkEntryState;
import de.gematik.refpopp.popp_server.model.ImportReportEntry;
import de.gematik.refpopp.popp_server.repository.CertHashRepository;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EgkHashImportServiceTest {
  private CmsSignatureVerifier cmsSignatureVerifierMock;
  private EgkTransferEntryParser egkTransferEntryParserMock;
  private EgkEntryProcessor egkEntryProcessorMock;
  private BatchFlusherFactory batchFlusherFactoryMock;

  private ImportReportProcessor importReportProcessorMock;

  private static final String SESSION_ID = "sessionId";
  private EgkHashImportService sut;

  @BeforeEach
  void setUp() {
    cmsSignatureVerifierMock = mock(CmsSignatureVerifier.class);
    final CertHashRepository certHashRepositoryMock = mock(CertHashRepository.class);
    egkTransferEntryParserMock = mock(EgkTransferEntryParser.class);
    egkEntryProcessorMock = mock(EgkEntryProcessor.class);
    batchFlusherFactoryMock = mock(BatchFlusherFactory.class);
    importReportProcessorMock = mock(ImportReportProcessor.class);

    ImportReportEntry reportMock = mock(ImportReportEntry.class);
    when(importReportProcessorMock.createReport(anyString())).thenReturn(reportMock);

    sut =
        new EgkHashImportService(
            cmsSignatureVerifierMock,
            certHashRepositoryMock,
            egkTransferEntryParserMock,
            egkEntryProcessorMock,
            batchFlusherFactoryMock,
            importReportProcessorMock);
  }

  @Test
  void importDataSuccessfully() throws URISyntaxException {
    // given
    final var availableProcessors = Runtime.getRuntime().availableProcessors();
    final URL resource = getClass().getClassLoader().getResource("import/no_1.simulation");
    assertNotNull(resource, "Import file not found!");
    final var egkTransferEntry =
        EgkTransferEntry.builder()
            .autHash(new byte[] {1, 2, 3})
            .cvcHash(new byte[] {1, 2, 3})
            .notAfter(LocalDateTime.now())
            .state(EgkEntryState.IMPORTED)
            .communicationMode(CommunicationMode.CONTACT)
            .build();
    when(egkTransferEntryParserMock.parseAll(any(), anyString()))
        .thenReturn(List.of(egkTransferEntry));
    final var path = Paths.get(resource.toURI());
    when(cmsSignatureVerifierMock.isSignatureValid(any(), anyString())).thenReturn(true);
    when(batchFlusherFactoryMock.<EgkEntry>create(anyInt(), any()))
        .thenReturn(mock(BatchFlusher.class));

    // when
    sut.importData(path, SESSION_ID);

    // then
    verify(cmsSignatureVerifierMock).isSignatureValid(any(), eq(SESSION_ID));
    verify(egkEntryProcessorMock).process(any(), anyString());
    verify(egkTransferEntryParserMock).parseAll(any(), anyString());
    verify(batchFlusherFactoryMock, times(availableProcessors)).create(anyInt(), any());
  }

  @Test
  void importDataFailsWhenSignatureNotValid() throws URISyntaxException {
    // given
    final URL resource = getClass().getClassLoader().getResource("import/no_1.simulation");
    assertNotNull(resource, "Import file not found!");
    final var path = Paths.get(resource.toURI());
    when(cmsSignatureVerifierMock.isSignatureValid(any(), anyString())).thenReturn(false);

    // when
    sut.importData(path, SESSION_ID);

    // then
    verify(cmsSignatureVerifierMock).isSignatureValid(any(), eq(SESSION_ID));
  }

  @Test
  void importDataFailsWhenNoFileFound() {
    // given
    final Path path = Path.of("notfound.simulation");
    when(cmsSignatureVerifierMock.isSignatureValid(any(), anyString())).thenReturn(true);

    // when / then
    assertThatThrownBy(() -> sut.importData(path, SESSION_ID))
        .isInstanceOf(ImportDataException.class)
        .hasMessageContaining("Error reading file");
  }

  @Test
  void importDataToUpdatedEntryToImportedWhenEntryExists() throws URISyntaxException {
    // given
    final URL resource = getClass().getClassLoader().getResource("import/no_1.simulation");
    assertNotNull(resource, "Import file not found!");
    final var path = Paths.get(resource.toURI());
    when(cmsSignatureVerifierMock.isSignatureValid(any(), anyString())).thenReturn(true);
    final var egkEntry = new EgkEntry();
    egkEntry.setState(EgkEntryState.AD_HOC);

    // when
    sut.importData(path, SESSION_ID);

    // then

  }

  @Test
  void importDataThrowsExceptionWhenSignatureVerificationFails() throws URISyntaxException {
    // given
    final URL resource = getClass().getClassLoader().getResource("import/no_1.simulation");
    assertNotNull(resource);
    final var path = Paths.get(resource.toURI());
    when(cmsSignatureVerifierMock.isSignatureValid(any(), anyString()))
        .thenThrow(
            new ImportDataException(SESSION_ID, "Signature verification failed", "errorCode"));

    // when / then
    assertThatThrownBy(() -> sut.importData(path, SESSION_ID))
        .isInstanceOf(ImportDataException.class)
        .hasMessageContaining("Signature verification failed");
  }

  @Test
  void importDataCorrectlyCategorizesDifferentEntryStates() throws URISyntaxException {
    // given
    final URL resource = getClass().getClassLoader().getResource("import/no_1.simulation");
    assertNotNull(resource, "Import file not found!");
    final Path path = Paths.get(resource.toURI());

    EgkTransferEntry transferEntry1 =
        EgkTransferEntry.builder()
            .autHash(new byte[] {1, 2, 3})
            .cvcHash(new byte[] {1, 2, 3})
            .notAfter(LocalDateTime.now())
            .communicationMode(CommunicationMode.CONTACT)
            .build();

    EgkTransferEntry transferEntry2 =
        EgkTransferEntry.builder()
            .autHash(new byte[] {4, 5, 6})
            .cvcHash(new byte[] {4, 5, 6})
            .notAfter(LocalDateTime.now())
            .communicationMode(CommunicationMode.CONTACT)
            .build();

    when(egkTransferEntryParserMock.parseAll(any(), anyString()))
        .thenReturn(List.of(transferEntry1, transferEntry2));

    EgkEntry importedEntry =
        new EgkEntry(
            new byte[] {1, 2, 3},
            new byte[] {1, 2, 3},
            EgkEntryState.IMPORTED,
            LocalDateTime.now());

    EgkEntry blockedEntry =
        new EgkEntry(
            new byte[] {4, 5, 6}, new byte[] {4, 5, 6}, EgkEntryState.BLOCKED, LocalDateTime.now());

    EgkEntry skippedEntry =
        new EgkEntry(
            new byte[] {7, 8, 9}, new byte[] {7, 8, 9}, EgkEntryState.AD_HOC, LocalDateTime.now());

    when(egkEntryProcessorMock.process(eq(transferEntry1), anyString()))
        .thenReturn(List.of(importedEntry, skippedEntry));

    when(egkEntryProcessorMock.process(eq(transferEntry2), anyString()))
        .thenReturn(List.of(blockedEntry));

    when(cmsSignatureVerifierMock.isSignatureValid(any(), anyString())).thenReturn(true);
    when(batchFlusherFactoryMock.<EgkEntry>create(anyInt(), any()))
        .thenReturn(mock(BatchFlusher.class));

    // when
    sut.importData(path, SESSION_ID);

    // then
    verify(importReportProcessorMock).createReport(SESSION_ID);
    verify(importReportProcessorMock)
        .finalizeReport(
            any(ImportReportEntry.class),
            eq(1L), // 1 imported entry
            eq(1L), // 1 blocked entry
            eq(1L), // 1 skipped (AD_HOC) entry
            eq(3L) // 3 total entries
            );
  }
}
