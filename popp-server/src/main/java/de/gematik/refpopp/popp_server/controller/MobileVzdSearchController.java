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

import de.gematik.refpopp.popp_server.controller.dto.FhirVzdSearchResponse;
import de.gematik.refpopp.popp_server.controller.dto.FhirVzdSearchResponseMapper;
import de.gematik.refpopp.popp_server.vzd.VzdSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the mobile VZD search flow of the PoPP service.
 *
 * <p>Currently only the FHIR-VZD search operation is implemented, which is used to retrieve
 * information about a healthcare company for obtaining the user's consent.
 */
@Slf4j
@RestController
@RequestMapping("/popp/patient/api/v1/mobile")
@Tag(name = "health company informations")
public class MobileVzdSearchController {

  private final VzdSearchService vzdSearchService;

  public MobileVzdSearchController(final VzdSearchService vzdSearchService) {
    this.vzdSearchService = vzdSearchService;
  }

  @Operation(
      operationId = "loadFhirVzdInformation",
      summary = "Load informations for a Telematik-ID or search criteria for giving user consent",
      description = "FHIR-VZD request to retrieve data on a healthcare company.")
  @ApiResponse(responseCode = "200", description = "HttpStatus.OK (200)")
  @ApiResponse(responseCode = "400", description = "HttpStatus.BAD_REQUEST (400)")
  @ApiResponse(responseCode = "404", description = "HttpStatus.NOT_FOUND (404)")
  @ApiResponse(responseCode = "500", description = "HttpStatus.INTERNAL_SERVER_ERROR (500)")
  @GetMapping(value = "/fhirvzdsearch", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FhirVzdSearchResponse> loadFhirVzdInformation(
      @Parameter(
              name = "searchrequest",
              description = "FHIR-VZD request to retrieve data on a healthcare company",
              required = true)
          @RequestParam("searchrequest")
          final String searchRequest) {
    log.info("| Received FHIR-VZD search request");
    final var result = vzdSearchService.searchByPageUrl(searchRequest);
    final var response = FhirVzdSearchResponseMapper.toResponse(result);
    if (response == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(response);
  }
}
