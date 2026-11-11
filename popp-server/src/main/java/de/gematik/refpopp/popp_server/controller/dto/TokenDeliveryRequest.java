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

package de.gematik.refpopp.popp_server.controller.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Request to retrieve PoPP tokens for a healthcare institution. */
@Schema(name = "TokenRequestSchema")
public record TokenDeliveryRequest(
    @Schema(
            ref = "#/components/schemas/TelematikIdSchema",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @JsonDeserialize(using = StrictStringDeserializer.class)
        String telematikId,
    @ArraySchema(schema = @Schema(ref = "#/components/schemas/WorkplaceIdSchema"))
        @JsonDeserialize(contentUsing = StrictStringDeserializer.class)
        List<@NotNull @Pattern(regexp = "^[^/:*?\"<>|]{1,64}$") String> workplaceIds,
    @Schema(
            description = "Maximum number of tokens to retrieve",
            minimum = "1",
            maximum = "100",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Min(1)
        @Max(100)
        @JsonDeserialize(using = IntegerLimitDeserializer.class)
        Integer limit) {}
