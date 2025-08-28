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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.model.ImportReportEntry;
import de.gematik.refpopp.popp_server.repository.ImportReportRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportReportProcessorTest {

  @Mock private ImportReportRepository importReportRepository;
  private ImportReportProcessor sut;
  private static final String SESSION_ID = "test-session-123";

  @BeforeEach
  void setUp() {
    sut = new ImportReportProcessor(importReportRepository);
  }

  @Test
  void createReportShouldInitializeNewReport() {
    // given
    ImportReportEntry report = new ImportReportEntry(SESSION_ID);
    when(importReportRepository.save(any(ImportReportEntry.class))).thenReturn(report);

    // when
    ImportReportEntry result = sut.createReport(SESSION_ID);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
    assertThat(result.getStartTime()).isNotNull();
    assertThat(result.getEndTime()).isNull();
    assertThat(result.getImportedCount()).isZero();
    assertThat(result.getBlockedCount()).isZero();
    assertThat(result.getSkippedCount()).isZero();
    assertThat(result.getTotalProcessedCount()).isZero();

    ArgumentCaptor<ImportReportEntry> reportCaptor =
        ArgumentCaptor.forClass(ImportReportEntry.class);
    verify(importReportRepository).save(reportCaptor.capture());
    ImportReportEntry capturedReport = reportCaptor.getValue();
    assertThat(capturedReport.getSessionId()).isEqualTo(SESSION_ID);
  }

  @Test
  void finalizeReportShouldUpdateReportWithStatistics() {
    // given
    ImportReportEntry report = new ImportReportEntry(SESSION_ID);
    report.setStartTime(LocalDateTime.now().minusMinutes(5));
    when(importReportRepository.save(any(ImportReportEntry.class))).thenReturn(report);

    // when
    sut.finalizeReport(report, 10L, 5L, 2L, 17L);

    // then
    ArgumentCaptor<ImportReportEntry> reportCaptor =
        ArgumentCaptor.forClass(ImportReportEntry.class);
    verify(importReportRepository).save(reportCaptor.capture());

    ImportReportEntry capturedReport = reportCaptor.getValue();
    assertThat(capturedReport.getSessionId()).isEqualTo(SESSION_ID);
    assertThat(capturedReport.getEndTime()).isNotNull();
    assertThat(capturedReport.getImportedCount()).isEqualTo(10L);
    assertThat(capturedReport.getBlockedCount()).isEqualTo(5L);
    assertThat(capturedReport.getSkippedCount()).isEqualTo(2L);
    assertThat(capturedReport.getTotalProcessedCount()).isEqualTo(17L);
    assertThat(capturedReport.getDurationInSeconds()).isPositive();
  }
}
