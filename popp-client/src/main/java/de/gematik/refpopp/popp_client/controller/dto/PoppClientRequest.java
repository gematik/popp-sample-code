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
import de.gematik.poppcommons.api.enums.CardConnectionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(
    name = "PoppClientRequest",
    description =
        "Request payload used to initiate token generation through a specific card communication"
            + " type.")
public record PoppClientRequest(
    @NotNull
        @Schema(
            description =
                """
                Specifies which card communication channel to use when generating the PoPP token.
                This defines how the system interacts with the card or connector (contact, contactless, connector).
                """,
            example = "contact-standard")
        CardConnectionType communicationType,
    @Schema(
            description =
                """
                Optional client session identifier.
                Used to correlate the token generation process with a client-side session.
                """,
            example = "e7cd2f3a-5c0b-4f01-9d68-0cdd4db82f21",
            nullable = true)
        String clientSessionId) {}
