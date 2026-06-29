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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.gematik.poppcommons.api.enums.BdeErrorCode;
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
            new ImportDataException(
                SESSION_ID,
                "Signature verification failed",
                BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR));

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

  @Test
  void importDataHandlesEmptyEntryList() throws URISyntaxException {
    // given
    final URL resource = getClass().getClassLoader().getResource("import/no_1.simulation");
    assertNotNull(resource, "Import file not found!");
    final Path path = Paths.get(resource.toURI());

    when(egkTransferEntryParserMock.parseAll(any(), anyString())).thenReturn(List.of());
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
            eq(0L), // 0 imported
            eq(0L), // 0 blocked
            eq(0L), // 0 skipped
            eq(0L) // 0 total
            );
  }

  @Test
  void importDataThrowsExceptionWhenParsingFails() throws URISyntaxException {
    // given
    final URL resource = getClass().getClassLoader().getResource("import/no_1.simulation");
    assertNotNull(resource, "Import file not found!");
    final Path path = Paths.get(resource.toURI());

    when(cmsSignatureVerifierMock.isSignatureValid(any(), anyString())).thenReturn(true);
    when(egkTransferEntryParserMock.parseAll(any(), anyString()))
        .thenThrow(
            new ImportDataException(
                SESSION_ID,
                "Error streaming CMS eContent parse: test error",
                BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR));

    // when / then
    assertThatThrownBy(() -> sut.importData(path, SESSION_ID))
        .isInstanceOf(ImportDataException.class)
        .hasMessageContaining("Error streaming CMS eContent parse");
  }

  @Test
  void importDataHandlesExceptionInEntryProcessor() throws URISyntaxException {
    // given
    final URL resource = getClass().getClassLoader().getResource("import/no_1.simulation");
    assertNotNull(resource, "Import file not found!");
    final Path path = Paths.get(resource.toURI());

    EgkTransferEntry transferEntry =
        EgkTransferEntry.builder()
            .autHash(new byte[] {1, 2, 3})
            .cvcHash(new byte[] {1, 2, 3})
            .notAfter(LocalDateTime.now())
            .communicationMode(CommunicationMode.CONTACT)
            .build();

    when(egkTransferEntryParserMock.parseAll(any(), anyString()))
        .thenReturn(List.of(transferEntry));
    when(cmsSignatureVerifierMock.isSignatureValid(any(), anyString())).thenReturn(true);
    when(egkEntryProcessorMock.process(eq(transferEntry), anyString()))
        .thenThrow(new RuntimeException("Processing error"));
    when(batchFlusherFactoryMock.<EgkEntry>create(anyInt(), any()))
        .thenReturn(mock(BatchFlusher.class));

    // when
    sut.importData(path, SESSION_ID);

    // then
    verify(importReportProcessorMock).createReport(SESSION_ID);
    verify(importReportProcessorMock)
        .finalizeReport(
            any(ImportReportEntry.class),
            eq(0L), // 0 imported
            eq(1L), // 1 blocked (due to exception)
            eq(0L), // 0 skipped
            eq(0L) // 0 total processed
            );
  }

  @Test
  void importDataProcessesMultipleEntriesWithMixedStates() throws URISyntaxException {
    // given
    final URL resource = getClass().getClassLoader().getResource("import/no_1.simulation");
    assertNotNull(resource, "Import file not found!");
    final Path path = Paths.get(resource.toURI());

    EgkTransferEntry entry1 =
        EgkTransferEntry.builder()
            .autHash(new byte[] {1})
            .cvcHash(new byte[] {1})
            .notAfter(LocalDateTime.now())
            .communicationMode(CommunicationMode.CONTACT)
            .build();

    EgkTransferEntry entry2 =
        EgkTransferEntry.builder()
            .autHash(new byte[] {2})
            .cvcHash(new byte[] {2})
            .notAfter(LocalDateTime.now())
            .communicationMode(CommunicationMode.CONTACTLESS)
            .build();

    EgkTransferEntry entry3 =
        EgkTransferEntry.builder()
            .autHash(new byte[] {3})
            .cvcHash(new byte[] {3})
            .notAfter(LocalDateTime.now())
            .communicationMode(CommunicationMode.G3)
            .build();

    when(egkTransferEntryParserMock.parseAll(any(), anyString()))
        .thenReturn(List.of(entry1, entry2, entry3));

    EgkEntry importedEntry1 =
        new EgkEntry(new byte[] {1}, new byte[] {1}, EgkEntryState.IMPORTED, LocalDateTime.now());
    EgkEntry blockedEntry =
        new EgkEntry(new byte[] {2}, new byte[] {2}, EgkEntryState.BLOCKED, LocalDateTime.now());
    EgkEntry skippedEntry =
        new EgkEntry(new byte[] {3}, new byte[] {3}, EgkEntryState.AD_HOC, LocalDateTime.now());

    when(egkEntryProcessorMock.process(eq(entry1), anyString()))
        .thenReturn(List.of(importedEntry1));
    when(egkEntryProcessorMock.process(eq(entry2), anyString())).thenReturn(List.of(blockedEntry));
    when(egkEntryProcessorMock.process(eq(entry3), anyString())).thenReturn(List.of(skippedEntry));

    when(cmsSignatureVerifierMock.isSignatureValid(any(), anyString())).thenReturn(true);
    when(batchFlusherFactoryMock.<EgkEntry>create(anyInt(), any()))
        .thenReturn(mock(BatchFlusher.class));

    // when
    sut.importData(path, SESSION_ID);

    // then
    verify(importReportProcessorMock)
        .finalizeReport(
            any(ImportReportEntry.class),
            eq(1L), // 1 imported
            eq(1L), // 1 blocked
            eq(1L), // 1 skipped
            eq(3L) // 3 total
            );
  }

  @Test
  void importDataCallsBatchFlusherForEachConsumerThread() throws URISyntaxException {
    // given
    final var processorCount = Runtime.getRuntime().availableProcessors();
    final URL resource = getClass().getClassLoader().getResource("import/no_1.simulation");
    assertNotNull(resource, "Import file not found!");
    final Path path = Paths.get(resource.toURI());

    EgkTransferEntry entry =
        EgkTransferEntry.builder()
            .autHash(new byte[] {1})
            .cvcHash(new byte[] {1})
            .notAfter(LocalDateTime.now())
            .communicationMode(CommunicationMode.CONTACT)
            .build();

    when(egkTransferEntryParserMock.parseAll(any(), anyString())).thenReturn(List.of(entry));
    when(cmsSignatureVerifierMock.isSignatureValid(any(), anyString())).thenReturn(true);
    final BatchFlusher<EgkEntry> flusherMock = mock(BatchFlusher.class);
    when(batchFlusherFactoryMock.<EgkEntry>create(anyInt(), any())).thenReturn(flusherMock);

    EgkEntry processedEntry =
        new EgkEntry(new byte[] {1}, new byte[] {1}, EgkEntryState.IMPORTED, LocalDateTime.now());
    when(egkEntryProcessorMock.process(eq(entry), anyString())).thenReturn(List.of(processedEntry));

    // when
    sut.importData(path, SESSION_ID);

    // then
    verify(batchFlusherFactoryMock, times(processorCount)).create(anyInt(), any());
    verify(flusherMock, times(processorCount)).flushRemaining();
  }
}
