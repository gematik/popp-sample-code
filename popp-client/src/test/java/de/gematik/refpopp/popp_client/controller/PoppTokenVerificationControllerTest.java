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
import static org.mockito.Mockito.*;

import de.gematik.refpopp.popp_client.controller.dto.PoppTokenValidationResponse;
import de.gematik.refpopp.popp_client.token_verification.PoppTokenVerificationResult;
import de.gematik.refpopp.popp_client.token_verification.PoppTokenVerificationService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("PoppTokenVerificationController Tests")
class PoppTokenVerificationControllerTest {

  @Mock private PoppTokenVerificationService poppTokenVerificationService;

  private PoppTokenVerificationController controller;

  @BeforeEach
  void setUp() {
    controller = new PoppTokenVerificationController(poppTokenVerificationService);
  }

  @Test
  @DisplayName("Should successfully verify a valid PoPP token")
  void testVerifyPoppTokenSuccess() {
    // given
    String poppToken =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6InZuZC50ZWxlbWF0aWsucG9wcCtqd3QiLCJraWQiOiJrZXktMSJ9.eyJpc3MiOiJodHRwczovL2lkcC5leGFtcGxlIiwiYWN0b3JJZCI6ImFjdG9yLTEyMyIsImlhdCI6MTcxNjA1NTAwMH0.signature";
    var request = new PoppTokenVerificationController.VerifyPoppTokenRequest(poppToken);

    Instant issuedAt = Instant.parse("2026-05-13T08:30:00Z");
    var verificationResult =
        new PoppTokenVerificationResult(true, "https://idp.example", "actor-123", issuedAt, null);

    when(poppTokenVerificationService.verifyToken(poppToken)).thenReturn(verificationResult);

    // when
    ResponseEntity<PoppTokenValidationResponse> response = controller.verifyPoppToken(request);

    // then
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().valid());
    assertEquals("https://idp.example", response.getBody().issuer());
    assertEquals("actor-123", response.getBody().actorId());
    assertEquals(issuedAt, response.getBody().issuedAt());
    assertNull(response.getBody().error());

    verify(poppTokenVerificationService, times(1)).verifyToken(poppToken);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  @NullSource
  @DisplayName("Should throw exception when PoPP token is null, empty, or blank")
  void testVerifyPoppTokenWithInvalidToken(String invalidToken) {
    // given
    var request = new PoppTokenVerificationController.VerifyPoppTokenRequest(invalidToken);

    // when & then
    PoppTokenValidationException exception =
        assertThrows(
            PoppTokenValidationException.class,
            () -> controller.verifyPoppToken(request),
            "Should throw PoppTokenValidationException");

    assertEquals(ErrorCode.TOKEN_MISSING, exception.getErrorCode());
    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    verify(poppTokenVerificationService, never()).verifyToken(any());
  }

  @Test
  @DisplayName("Should throw exception when request body is null")
  void testVerifyPoppTokenWithNullRequest() {
    // when & then
    PoppTokenValidationException exception =
        assertThrows(
            PoppTokenValidationException.class,
            () -> controller.verifyPoppToken(null),
            "Should throw PoppTokenValidationException");

    assertEquals(ErrorCode.TOKEN_MISSING, exception.getErrorCode());
    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    verify(poppTokenVerificationService, never()).verifyToken(any());
  }

  @Test
  @DisplayName("Should propagate validation service exception")
  void testVerifyPoppTokenServiceThrowsException() {
    // given
    String poppToken = "invalidToken";
    var request = new PoppTokenVerificationController.VerifyPoppTokenRequest(poppToken);

    when(poppTokenVerificationService.verifyToken(poppToken))
        .thenThrow(
            new PoppTokenValidationException(ErrorCode.TOKEN_MALFORMED, "Token format is invalid"));

    // when & then
    PoppTokenValidationException exception =
        assertThrows(
            PoppTokenValidationException.class,
            () -> controller.verifyPoppToken(request),
            "Should propagate service exception");

    assertEquals(ErrorCode.TOKEN_MALFORMED, exception.getErrorCode());
    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    verify(poppTokenVerificationService, times(1)).verifyToken(poppToken);
  }

  @Test
  @DisplayName("Should return invalid token response with signature error")
  void testVerifyPoppTokenSignatureInvalid() {
    // given
    String poppToken =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6InZuZC50ZWxlbWF0aWsucG9wcCtqd3QiLCJraWQiOiJrZXktMSJ9.invalid.signature";
    var request = new PoppTokenVerificationController.VerifyPoppTokenRequest(poppToken);

    when(poppTokenVerificationService.verifyToken(poppToken))
        .thenThrow(
            new PoppTokenValidationException(
                ErrorCode.SIGNATURE_INVALID, "Signature verification failed"));

    // when & then
    PoppTokenValidationException exception =
        assertThrows(PoppTokenValidationException.class, () -> controller.verifyPoppToken(request));

    assertEquals(ErrorCode.SIGNATURE_INVALID, exception.getErrorCode());
    assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
  }

  @Test
  @DisplayName("Should handle actor mismatch error")
  void testVerifyPoppTokenActorMismatch() {
    // given
    String poppToken =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6InZuZC50ZWxlbWF0aWsucG9wcCtqd3QiLCJraWQiOiJrZXktMSJ9.eyJpc3MiOiJodHRwczovL2lkcC5leGFtcGxlIiwiYWN0b3JJZCI6Indyb25nLWFjdG9yIiwiaWF0IjoxNzE2MDU1MDAwfQ.signature";
    var request = new PoppTokenVerificationController.VerifyPoppTokenRequest(poppToken);

    when(poppTokenVerificationService.verifyToken(poppToken))
        .thenThrow(
            new PoppTokenValidationException(
                ErrorCode.ACTOR_MISMATCH, "Actor ID does not match authenticated entity"));

    // when & then
    PoppTokenValidationException exception =
        assertThrows(PoppTokenValidationException.class, () -> controller.verifyPoppToken(request));

    assertEquals(ErrorCode.ACTOR_MISMATCH, exception.getErrorCode());
    assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
  }

  @Test
  @DisplayName("Should handle token expired error")
  void testVerifyPoppTokenExpired() {
    // given
    String poppToken = "expiredToken";
    var request = new PoppTokenVerificationController.VerifyPoppTokenRequest(poppToken);

    when(poppTokenVerificationService.verifyToken(poppToken))
        .thenThrow(new PoppTokenValidationException(ErrorCode.TOKEN_EXPIRED, "Token has expired"));

    // when & then
    PoppTokenValidationException exception =
        assertThrows(PoppTokenValidationException.class, () -> controller.verifyPoppToken(request));

    assertEquals(ErrorCode.TOKEN_EXPIRED, exception.getErrorCode());
    assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
  }

  @Test
  @DisplayName("Should handle JWKS fetch failed error")
  void testVerifyPoppTokenJwksFetchFailed() {
    // given
    String poppToken = "validToken";
    var request = new PoppTokenVerificationController.VerifyPoppTokenRequest(poppToken);

    when(poppTokenVerificationService.verifyToken(poppToken))
        .thenThrow(
            new PoppTokenValidationException(
                ErrorCode.JWKS_FETCH_FAILED, "Failed to fetch JWKS from endpoint"));

    // when & then
    PoppTokenValidationException exception =
        assertThrows(PoppTokenValidationException.class, () -> controller.verifyPoppToken(request));

    assertEquals(ErrorCode.JWKS_FETCH_FAILED, exception.getErrorCode());
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getHttpStatus());
  }

  @Test
  @DisplayName("Should handle entity statement fetch failed error")
  void testVerifyPoppTokenEntityStatementFetchFailed() {
    // given
    String poppToken = "validToken";
    var request = new PoppTokenVerificationController.VerifyPoppTokenRequest(poppToken);

    when(poppTokenVerificationService.verifyToken(poppToken))
        .thenThrow(
            new PoppTokenValidationException(
                ErrorCode.ENTITY_STATEMENT_FETCH_FAILED, "Failed to fetch entity statement"));

    // when & then
    PoppTokenValidationException exception =
        assertThrows(PoppTokenValidationException.class, () -> controller.verifyPoppToken(request));

    assertEquals(ErrorCode.ENTITY_STATEMENT_FETCH_FAILED, exception.getErrorCode());
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getHttpStatus());
  }

  @Test
  @DisplayName("Should handle issuer invalid error")
  void testVerifyPoppTokenIssuerInvalid() {
    // given
    String poppToken = "tokenWithInvalidIssuer";
    var request = new PoppTokenVerificationController.VerifyPoppTokenRequest(poppToken);

    when(poppTokenVerificationService.verifyToken(poppToken))
        .thenThrow(
            new PoppTokenValidationException(
                ErrorCode.ISSUER_INVALID, "Token issuer is not trusted"));

    // when & then
    PoppTokenValidationException exception =
        assertThrows(PoppTokenValidationException.class, () -> controller.verifyPoppToken(request));

    assertEquals(ErrorCode.ISSUER_INVALID, exception.getErrorCode());
    assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
  }

  @Test
  @DisplayName("Should handle internal server error")
  void testVerifyPoppTokenInternalError() {
    // given
    String poppToken = "validToken";
    var request = new PoppTokenVerificationController.VerifyPoppTokenRequest(poppToken);

    when(poppTokenVerificationService.verifyToken(poppToken))
        .thenThrow(
            new PoppTokenValidationException(
                ErrorCode.INTERNAL_ERROR, "An unexpected error occurred"));

    // when & then
    PoppTokenValidationException exception =
        assertThrows(PoppTokenValidationException.class, () -> controller.verifyPoppToken(request));

    assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatus());
  }
}
