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

import de.gematik.refpopp.popp_server.model.ImportReportEntry;
import de.gematik.refpopp.popp_server.repository.ImportReportRepository;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ImportReportProcessor {

  private final ImportReportRepository importReportRepository;

  public ImportReportProcessor(final ImportReportRepository importReportRepository) {
    this.importReportRepository = importReportRepository;
  }

  public ImportReportEntry createReport(final String sessionId) {
    ImportReportEntry report = new ImportReportEntry(sessionId);
    report.setStartTime(LocalDateTime.now());
    return importReportRepository.save(report);
  }

  public void finalizeReport(
      final ImportReportEntry report,
      final long importedCount,
      final long blockedCount,
      final long skippedCount,
      final long totalProcessedCount) {

    report.setEndTime(LocalDateTime.now());
    report.setImportedCount(importedCount);
    report.setBlockedCount(blockedCount);
    report.setSkippedCount(skippedCount);
    report.setTotalProcessedCount(totalProcessedCount);

    ImportReportEntry savedReport = importReportRepository.save(report);
    log.info("Import report finalized: {}", savedReport.getFormattedReport());
  }
}
