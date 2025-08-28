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

package de.gematik.refpopp.popp_server.controller;

import de.gematik.refpopp.popp_server.model.ImportReportEntry;
import de.gematik.refpopp.popp_server.repository.ImportReportRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/import-reports")
public class ImportReportController {

  private final ImportReportRepository importReportRepository;

  public ImportReportController(ImportReportRepository importReportRepository) {
    this.importReportRepository = importReportRepository;
  }

  @GetMapping
  public ResponseEntity<List<ImportReportEntry>> getAllReports() {
    return ResponseEntity.ok(importReportRepository.findAllByOrderByStartTimeDesc());
  }

  @GetMapping("/{sessionId}")
  public ResponseEntity<ImportReportEntry> getReportBySessionId(@PathVariable String sessionId) {
    return importReportRepository
        .findBySessionId(sessionId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/latest")
  public ResponseEntity<ImportReportEntry> getLatestReport() {
    return importReportRepository
        .findTopByOrderByStartTimeDesc()
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
