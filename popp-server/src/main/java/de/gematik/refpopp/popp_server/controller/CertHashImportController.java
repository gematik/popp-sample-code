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

import de.gematik.refpopp.popp_server.file.temp.EgkImportTempFileService;
import de.gematik.refpopp.popp_server.hashdb.EgkHashImportService;
import java.io.IOException;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/cert-hash")
public class CertHashImportController {

  private final EgkHashImportService egkHashImportService;
  private final EgkImportTempFileService egkImportTempFileService;
  private final String certHashImportLocation;

  public CertHashImportController(
      final EgkHashImportService egkHashImportService,
      final EgkImportTempFileService egkImportTempFileService,
      @Value("${cert-hash-import.location}") final String certHashImportLocation) {
    this.egkHashImportService = egkHashImportService;
    this.egkImportTempFileService = egkImportTempFileService;
    this.certHashImportLocation = certHashImportLocation;
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> certHash(
      @RequestParam("file") final MultipartFile file,
      @RequestParam(value = "sessionId", required = false) final String sessionId) {
    log.info("Received request to import cert hash file: {}", file.getOriginalFilename());
    if (file.isEmpty()) {
      return ResponseEntity.badRequest().body("No file uploaded");
    }
    Path path = null;
    try {
      path = egkImportTempFileService.createFile(certHashImportLocation);
      file.transferTo(path.toFile());
      egkHashImportService.importData(path, sessionId);
      return ResponseEntity.ok("Request to import cert hash file was successful");
    } catch (final IOException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Could not save uploaded file: " + e.getMessage());
    } finally {
      egkImportTempFileService.deleteFile(path);
    }
  }
}
