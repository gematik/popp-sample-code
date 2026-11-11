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

import de.gematik.refpopp.popp_server.tokengeneration.InvalidTokenDeliveryRequestException;
import de.gematik.refpopp.popp_server.tokengeneration.TokenDeliveryNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps failures of the token-delivery endpoint to its documented error format. */
@Slf4j
@RestControllerAdvice(assignableTypes = TokenDeliveryController.class)
public class TokenDeliveryExceptionHandler {

  private static final String MALFORMED_REQUEST = "malformedRequest";

  @ExceptionHandler(InvalidTokenDeliveryRequestException.class)
  public ResponseEntity<ErrorResponse> handleInvalidRequest(
      InvalidTokenDeliveryRequestException ex) {
    return error(HttpStatus.BAD_REQUEST, MALFORMED_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
    return error(HttpStatus.BAD_REQUEST, MALFORMED_REQUEST, "Malformed request body");
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
    return error(HttpStatus.BAD_REQUEST, MALFORMED_REQUEST, "Missing x-useragent header");
  }

  @ExceptionHandler(TokenDeliveryNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(TokenDeliveryNotFoundException ex) {
    return error(HttpStatus.NOT_FOUND, "noResource", ex.getMessage());
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ErrorResponse> handleInternalError(RuntimeException ex) {
    log.error("PoPP token delivery failed", ex);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "internalError", "PoPP token delivery failed");
  }

  private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String detail) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ErrorResponse.of(code, detail));
  }
}
