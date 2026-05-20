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

package de.gematik.refpopp.popp_client.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.gematik.refpopp.popp_client.token_verification.PoppTokenVerificationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(
    name = "PoppTokenValidationResponse",
    description = "Response containing the result of PoPP token validation")
public record PoppTokenValidationResponse(
    @Schema(
            description = "Indicates whether the PoPP token is valid",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean valid,
    @Schema(
            description = "Issuer of the PoPP token",
            example = "https://idp.example",
            nullable = true)
        String issuer,
    @Schema(
            description = "Actor identifier extracted from the PoPP token",
            example = "actor-123",
            nullable = true)
        String actorId,
    @Schema(
            description = "Timestamp when the PoPP token was issued (UTC)",
            example = "2026-05-13T08:30:00Z",
            type = "string",
            format = "date-time",
            nullable = true)
        Instant issuedAt,
    @Schema(
            description = "Validation error details if the token is invalid",
            example = "Signature validation failed",
            nullable = true)
        String error) {

  public static PoppTokenValidationResponse toPoppTokenValidationResponse(
      PoppTokenVerificationResult result) {

    return new PoppTokenValidationResponse(
        result.valid(), result.issuer(), result.actorId(), result.issuedAt(), result.error());
  }
}
