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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.refpopp.popp_server.model.ImportReportEntry;
import de.gematik.refpopp.popp_server.repository.ImportReportRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ImportReportControllerTest {

  @Mock private ImportReportRepository importReportRepository;

  @InjectMocks private ImportReportController importReportController;

  private ImportReportEntry report1;
  private ImportReportEntry report2;

  @BeforeEach
  void setUp() {
    report1 = new ImportReportEntry();
    report1.setSessionId("session1");
    report1.setStartTime(LocalDateTime.now().minusDays(1));

    report2 = new ImportReportEntry();
    report2.setSessionId("session2");
    report2.setStartTime(LocalDateTime.now());
  }

  @Test
  void getAllReports_shouldReturnAllReportsOrderedByStartTimeDesc() {
    // given
    List<ImportReportEntry> reports = Arrays.asList(report2, report1);
    when(importReportRepository.findAllByOrderByStartTimeDesc()).thenReturn(reports);

    // when
    ResponseEntity<List<ImportReportEntry>> response = importReportController.getAllReports();

    // then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(reports, response.getBody());
    verify(importReportRepository).findAllByOrderByStartTimeDesc();
  }

  @Test
  void getAllReports_shouldReturnEmptyListWhenNoReports() {
    // given
    when(importReportRepository.findAllByOrderByStartTimeDesc())
        .thenReturn(Collections.emptyList());

    // when
    ResponseEntity<List<ImportReportEntry>> response = importReportController.getAllReports();

    // then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().isEmpty());
  }

  @Test
  void getReportBySessionId_shouldReturnReportWhenSessionIdExists() {
    // given
    String sessionId = "session1";
    when(importReportRepository.findBySessionId(sessionId)).thenReturn(Optional.of(report1));

    // when
    ResponseEntity<ImportReportEntry> response =
        importReportController.getReportBySessionId(sessionId);

    // then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(report1, response.getBody());
  }

  @Test
  void getReportBySessionId_shouldReturnNotFoundWhenSessionIdDoesNotExist() {
    // given
    String sessionId = "nonexistent";
    when(importReportRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

    // when
    ResponseEntity<ImportReportEntry> response =
        importReportController.getReportBySessionId(sessionId);

    // then
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertNull(response.getBody());
  }

  @Test
  void getLatestReport_shouldReturnLatestReportWhenReportsExist() {
    // given
    when(importReportRepository.findTopByOrderByStartTimeDesc()).thenReturn(Optional.of(report2));

    // when
    ResponseEntity<ImportReportEntry> response = importReportController.getLatestReport();

    // then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(report2, response.getBody());
  }

  @Test
  void getLatestReport_shouldReturnNotFoundWhenNoReports() {
    // given
    when(importReportRepository.findTopByOrderByStartTimeDesc()).thenReturn(Optional.empty());

    // when
    ResponseEntity<ImportReportEntry> response = importReportController.getLatestReport();

    // then
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertNull(response.getBody());
  }
}
