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

import de.gematik.refpopp.popp_client.controller.dto.PoppTokenValidationResponse;
import de.gematik.refpopp.popp_client.token_verification.PoppTokenVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/token")
public class PoppTokenVerificationController {

  private final PoppTokenVerificationService poppTokenVerificationService;

  public PoppTokenVerificationController(
      PoppTokenVerificationService poppTokenVerificationService) {
    this.poppTokenVerificationService = poppTokenVerificationService;
  }

  @Operation(
      summary = "Verify a PoPP token",
      description = "Validates the provided PoPP token and returns the verification result.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "PoPP token verification request",
              required = true,
              content =
                  @Content(
                      schema = @Schema(implementation = VerifyPoppTokenRequest.class),
                      examples =
                          @ExampleObject(
                              name = "Verify PoPP token request",
                              value =
                                  """
                                  {
                                    "poppToken": "eyJhbGciOi..."
                                  }
                                  """))),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Token verification completed",
            content =
                @Content(
                    schema = @Schema(implementation = PoppTokenValidationResponse.class),
                    examples = {
                      @ExampleObject(
                          name = "Valid token",
                          value =
                              """
                              {
                                "valid": true,
                                "issuer": "https://idp.example",
                                "actorId": "actor-123",
                                "issuedAt": "2026-05-13T08:30:00Z"
                              }
                              """)
                    })),
        @ApiResponse(
            responseCode = "400",
            description = "Bad request, for example TOKEN_MISSING or TOKEN_MALFORMED",
            content =
                @Content(
                    schema = @Schema(implementation = PoppTokenValidationResponse.class),
                    examples =
                        @ExampleObject(
                            name = "Bad request",
                            value =
                                """
                                {
                                  "valid": false,
                                  "error": "TOKEN_MISSING"
                                }
                                """))),
        @ApiResponse(
            responseCode = "401",
            description =
                "Unauthorized, for example SIGNATURE_INVALID, TOKEN_EXPIRED or ISSUER_INVALID",
            content =
                @Content(
                    schema = @Schema(implementation = PoppTokenValidationResponse.class),
                    examples =
                        @ExampleObject(
                            name = "Unauthorized",
                            value =
                                """
                                {
                                  "valid": false,
                                  "error": "SIGNATURE_INVALID"
                                }
                                """))),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden, for example ACTOR_MISMATCH",
            content =
                @Content(
                    schema = @Schema(implementation = PoppTokenValidationResponse.class),
                    examples =
                        @ExampleObject(
                            name = "Forbidden",
                            value =
                                """
                                {
                                  "valid": false,
                                  "error": "ACTOR_MISMATCH"
                                }
                                """))),
        @ApiResponse(
            responseCode = "503",
            description =
                "Service unavailable, for example JWKS_FETCH_FAILED, OCSP_CHECK_FAILED or"
                    + " ENTITY_STATEMENT_FETCH_FAILED",
            content =
                @Content(
                    schema = @Schema(implementation = PoppTokenValidationResponse.class),
                    examples =
                        @ExampleObject(
                            name = "Service unavailable",
                            value =
                                """
                                {
                                  "valid": false,
                                  "error": "ENTITY_STATEMENT_FETCH_FAILED"
                                }
                                """))),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content =
                @Content(
                    schema = @Schema(implementation = PoppTokenValidationResponse.class),
                    examples =
                        @ExampleObject(
                            name = "Internal Error",
                            value =
                                """
                                {
                                  "valid": false,
                                  "error": "INTERNAL_ERROR"
                                }
                                """)))
      })
  @PostMapping("/verify")
  public ResponseEntity<PoppTokenValidationResponse> verifyPoppToken(
      @RequestBody VerifyPoppTokenRequest request) {

    if (request == null || request.poppToken() == null || request.poppToken().isBlank()) {
      throw new PoppTokenValidationException(ErrorCode.TOKEN_MISSING, "PoPP token is missing");
    }

    var result = poppTokenVerificationService.verifyToken(request.poppToken());

    return ResponseEntity.ok(PoppTokenValidationResponse.toPoppTokenValidationResponse(result));
  }

  @Schema(name = "VerifyPoppTokenRequest", description = "Request for PoPP token verification")
  public record VerifyPoppTokenRequest(
      @Schema(
              description = "PoPP token to verify",
              example = "eyJhbGciOi...",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String poppToken) {}
}
