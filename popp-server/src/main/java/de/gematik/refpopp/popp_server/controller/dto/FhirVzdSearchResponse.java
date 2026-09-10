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
import java.util.List;

/** Public API representation of the FHIR-VZD search response. */
@Schema(name = "FhirVzdSearchResponse")
public record FhirVzdSearchResponse(
    @ArraySchema(
            minItems = 1,
            maxItems = 100,
            schema = @Schema(description = "Information about the healthcare companies"))
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<FhirVzdSearchResultItem> practitioners) {}
