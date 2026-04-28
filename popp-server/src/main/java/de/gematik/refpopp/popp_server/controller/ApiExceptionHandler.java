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

import de.gematik.refpopp.popp_server.federation.FederationEntityStatementCreationException;
import de.gematik.refpopp.popp_server.federation.SignedJwksCreationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(FederationEntityStatementCreationException.class)
  public ResponseEntity<ErrorResponse> handleFederationEntityStatementCreation(
      FederationEntityStatementCreationException ignoredEx) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            ErrorResponse.of(
                "FEDERATION_ENTITY_STATEMENT_CREATION_FAILED",
                "Failed to create federation entity statement"));
  }

  @ExceptionHandler(SignedJwksCreationException.class)
  public ResponseEntity<ErrorResponse> handleSignedJwksCreation(
      SignedJwksCreationException ignoredEx) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ErrorResponse.of("SIGNED_JWKS_CREATION_FAILED", "Failed to create signed JWKS"));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ErrorResponse.of("INTERNAL_ERROR", ex.getMessage()));
  }
}
