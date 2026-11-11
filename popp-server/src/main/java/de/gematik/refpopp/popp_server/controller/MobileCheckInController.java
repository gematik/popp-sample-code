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

import de.gematik.refpopp.popp_server.controller.dto.MobileCheckInRequest;
import de.gematik.refpopp.popp_server.controller.dto.MobileCheckInResponse;
import de.gematik.refpopp.popp_server.tokengeneration.MobileCheckInRequestValidator;
import de.gematik.refpopp.popp_server.tokengeneration.PoppTokenGenerationRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/popp/patient/api/v1/mobile")
@Tag(name = "Online Check-in")
public class MobileCheckInController {

  private final MobileCheckInRequestValidator requestValidator;
  private final PoppTokenGenerationRecordService recordService;

  public MobileCheckInController(
      final MobileCheckInRequestValidator requestValidator,
      final PoppTokenGenerationRecordService recordService) {
    this.requestValidator = requestValidator;
    this.recordService = recordService;
  }

  @Operation(
      operationId = "checkInGID",
      summary = "Create a PoPP record after Health-ID authentication")
  @ApiResponse(
      responseCode = "201",
      description = "PoPP record created",
      content = @Content(schema = @Schema(implementation = MobileCheckInResponse.class)))
  @ApiResponse(
      responseCode = "200",
      description = "Existing pending PoPP record reused",
      content = @Content(schema = @Schema(implementation = MobileCheckInResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Malformed request",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Resource not found",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "500",
      description = "Internal error",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping(
      value = "/checkin",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<MobileCheckInResponse> checkIn(
      @Parameter(
              description = "Client ID and version",
              required = true,
              schema = @Schema(ref = "#/components/schemas/UserAgentSchema"))
          @RequestHeader("x-useragent")
          final String userAgent,
      @Parameter(
              description = "KVNR injected by ZETA Guard; mobile client must not set",
              required = true,
              schema = @Schema(ref = "#/components/schemas/KVNRSchema"))
          @RequestHeader("x-kvnr")
          final String kvnr,
      @Parameter(
              description = "IK number injected by ZETA Guard; mobile client must not set",
              required = true,
              schema = @Schema(ref = "#/components/schemas/IKNrSchema"))
          @RequestHeader("x-iknr")
          final String iknr,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true) @RequestBody
          final MobileCheckInRequest request) {
    final var clientId = requestValidator.validate(request, userAgent, kvnr, iknr);
    final var result =
        recordService.create(kvnr, iknr, request.telematikId(), request.workplaceId(), clientId);
    final var response =
        new MobileCheckInResponse(
            result.message().getStatus().getValue(), result.message().getId());
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(response);
  }
}
