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

package de.gematik.refpopp.popp_client.controller;

import static org.junit.jupiter.api.Assertions.*;

import de.gematik.refpopp.popp_client.controller.dto.PoppTokenValidationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("PoppTokenValidationExceptionHandler Tests")
class PoppTokenValidationExceptionHandlerTest {

  private PoppTokenValidationExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new PoppTokenValidationExceptionHandler();
  }

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  @DisplayName("Should handle PoppTokenValidationException with correct HTTP status")
  void handlePoppTokenValidationExceptionReturnsCorrectHttpStatus(ErrorCode errorCode) {
    // given
    PoppTokenValidationException exception =
        new PoppTokenValidationException(errorCode, "Test error message");

    // when
    ResponseEntity<PoppTokenValidationResponse> response =
        handler.handlePoppTokenValidationException(exception);

    // then
    assertEquals(errorCode.getHttpStatus(), response.getStatusCode());
  }

  @Test
  @DisplayName("Should return false validity in response for PoppTokenValidationException")
  void handlePoppTokenValidationExceptionReturnsInvalidResponse() {
    // given
    PoppTokenValidationException exception =
        new PoppTokenValidationException(ErrorCode.TOKEN_MALFORMED, "Token is malformed");

    // when
    ResponseEntity<PoppTokenValidationResponse> response =
        handler.handlePoppTokenValidationException(exception);

    // then
    assertNotNull(response.getBody());
    assertFalse(response.getBody().valid());
  }

  @Test
  @DisplayName("Should include error code name in response for PoppTokenValidationException")
  void handlePoppTokenValidationExceptionIncludesErrorCodeName() {
    // given
    PoppTokenValidationException exception =
        new PoppTokenValidationException(ErrorCode.SIGNATURE_INVALID, "Signature is invalid");

    // when
    ResponseEntity<PoppTokenValidationResponse> response =
        handler.handlePoppTokenValidationException(exception);

    // then
    assertNotNull(response.getBody());
    assertEquals("SIGNATURE_INVALID", response.getBody().error());
  }

  @Test
  @DisplayName("Should have null issuer and actorId in response for PoppTokenValidationException")
  void handlePoppTokenValidationExceptionReturnsNullIssuerAndActorId() {
    // given
    PoppTokenValidationException exception =
        new PoppTokenValidationException(ErrorCode.ISSUER_INVALID, "Issuer mismatch");

    // when
    ResponseEntity<PoppTokenValidationResponse> response =
        handler.handlePoppTokenValidationException(exception);

    // then
    assertNotNull(response.getBody());
    assertNull(response.getBody().issuer());
    assertNull(response.getBody().actorId());
    assertNull(response.getBody().issuedAt());
  }

  @Test
  @DisplayName("Should handle generic Exception with INTERNAL_ERROR status and response code")
  void handleGenericExceptionReturnsInternalErrorStatus() {
    // given
    Exception exception = new RuntimeException("Unexpected error");

    // when
    ResponseEntity<PoppTokenValidationResponse> response = handler.handleException(exception);

    // then
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
  }

  @Test
  @DisplayName("Should return false validity in response for generic Exception")
  void handleGenericExceptionReturnsInvalidResponse() {
    // given
    Exception exception = new IllegalArgumentException("Invalid argument");

    // when
    ResponseEntity<PoppTokenValidationResponse> response = handler.handleException(exception);

    // then
    assertNotNull(response.getBody());
    assertFalse(response.getBody().valid());
  }

  @Test
  @DisplayName("Should include INTERNAL_ERROR code name in response for generic Exception")
  void handleGenericExceptionIncludesErrorCodeName() {
    // given
    Exception exception = new NullPointerException("Null pointer exception");

    // when
    ResponseEntity<PoppTokenValidationResponse> response = handler.handleException(exception);

    // then
    assertNotNull(response.getBody());
    assertEquals("INTERNAL_ERROR", response.getBody().error());
  }

  @Test
  @DisplayName("Should have null issuer and actorId in response for generic Exception")
  void handleGenericExceptionReturnsNullIssuerAndActorId() {
    // given
    Exception exception = new Exception("Generic exception");

    // when
    ResponseEntity<PoppTokenValidationResponse> response = handler.handleException(exception);

    // then
    assertNotNull(response.getBody());
    assertNull(response.getBody().issuer());
    assertNull(response.getBody().actorId());
    assertNull(response.getBody().issuedAt());
  }
}
