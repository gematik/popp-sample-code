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
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(
    name = "PoppClientResponse",
    description =
        """
        Standard response object returned by the PoPP client token API.
        Contains either a successfully generated token or error details.
        """)
public record PoppClientResponse(
    @Schema(
            description =
                """
                Indicates whether the request was successful (`OK`) or failed (`ERROR`).
                """,
            example = "OK")
        PoppClientResponseStatus status,
    @Schema(
            description =
                """
                The generated PoPP token.
                Present only when the operation is successful and `status` is `OK`.
                """,
            example = "eyJhbGciOiJSUzI1NiIsInR...",
            nullable = true)
        String token,
    @Schema(
            description =
                """
                Human-readable error message describing why the request failed.
                Present only when `status` is `ERROR`.
                """,
            example = "Token generation timed out",
            nullable = true)
        String errorMessage) {

  public static PoppClientResponse ok() {
    return new PoppClientResponse(PoppClientResponseStatus.OK, null, null);
  }

  public static PoppClientResponse ok(String token) {
    return new PoppClientResponse(PoppClientResponseStatus.OK, token, null);
  }

  public static PoppClientResponse error(String errorMessage) {
    return new PoppClientResponse(PoppClientResponseStatus.ERROR, null, errorMessage);
  }
}
