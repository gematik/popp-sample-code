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

package de.gematik.refpopp.popp_server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "import_report_entries")
public class ImportReportEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  @Column(name = "start_time", nullable = false)
  private LocalDateTime startTime;

  @Column(name = "end_time")
  private LocalDateTime endTime;

  @Column(name = "imported_count", nullable = false)
  private long importedCount;

  @Column(name = "blocked_count", nullable = false)
  private long blockedCount;

  @Column(name = "skipped_count", nullable = false)
  private long skippedCount;

  @Column(name = "total_processed_count", nullable = false)
  private long totalProcessedCount;

  public ImportReportEntry() {}

  public ImportReportEntry(String sessionId) {
    this.sessionId = sessionId;
    this.startTime = LocalDateTime.now();
    this.importedCount = 0;
    this.blockedCount = 0;
    this.skippedCount = 0;
    this.totalProcessedCount = 0;
  }

  public long getDurationInSeconds() {
    if (startTime != null && endTime != null) {
      return java.time.Duration.between(startTime, endTime).getSeconds();
    }
    return 0;
  }

  public String getFormattedReport() {
    return "=== HashDB Import Report ===\n"
        + "Session ID: "
        + sessionId
        + "\n"
        + "Start Time: "
        + startTime
        + "\n"
        + "End Time: "
        + endTime
        + "\n"
        + "Duration: "
        + getDurationInSeconds()
        + " seconds\n"
        + "\n--- Statistics ---\n"
        + "Imported Entries: "
        + importedCount
        + "\n"
        + "Blocked Entries: "
        + blockedCount
        + "\n"
        + "Skipped Entries: "
        + skippedCount
        + "\n"
        + "Total Processed: "
        + totalProcessedCount
        + "\n";
  }
}
