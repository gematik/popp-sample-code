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

import io.swagger.v3.oas.annotations.media.Schema;

/** Public API representation of one healthcare company in a FHIR-VZD search result. */
@Schema(name = "FhirVzdSearchResultItem")
public record FhirVzdSearchResultItem(
    @Schema(description = "Name of the healthcare company", example = "Apotheke am Hauptbahnhof")
        String name,
    @Schema(
            description = "Telematik-ID of a healthcare institution",
            example = "1-SMC-B-Testkarte--883110000168757")
        String telematikId,
    @Schema(description = "Institution number (9 digits).", example = "123456789") String iknr,
    @Schema(
            description = "Address of the healthcare company",
            example = "Hauptbahnhofstraße 1, 12345 Musterstadt")
        String address,
    @Schema(
            description = "Contact information of the healthcare company",
            example = "Telefon: 01234 567890")
        String contact) {}
