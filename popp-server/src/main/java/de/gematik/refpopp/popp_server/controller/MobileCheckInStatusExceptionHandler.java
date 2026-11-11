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

import de.gematik.refpopp.popp_server.tokengeneration.PoppTokenGenerationRecordNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps failures of the mobile check-in status endpoint to its documented JSON error format. */
@Slf4j
@RestControllerAdvice(assignableTypes = MobileCheckInStatusController.class)
public class MobileCheckInStatusExceptionHandler {

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex) {
    return error(HttpStatus.BAD_REQUEST, "malformedRequest", "Invalid statusrequest parameter");
  }

  @ExceptionHandler(PoppTokenGenerationRecordNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      PoppTokenGenerationRecordNotFoundException ex) {
    return error(HttpStatus.NOT_FOUND, "noResource", ex.getMessage());
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ErrorResponse> handleInternalError(RuntimeException ex) {
    log.error("Failed to read PoPP check-in status", ex);
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR, "internalError", "Failed to read check-in status");
  }

  private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String detail) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ErrorResponse.of(code, detail));
  }
}
