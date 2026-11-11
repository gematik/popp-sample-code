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

import de.gematik.refpopp.popp_server.controller.dto.TokenDeliveryRequest;
import de.gematik.refpopp.popp_server.controller.dto.TokenDeliveryResponse;
import de.gematik.refpopp.popp_server.tokengeneration.TokenDeliveryRequestValidator;
import de.gematik.refpopp.popp_server.tokengeneration.TokenDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST entry point for retrieving PoPP tokens after an online check-in. */
@RestController
@RequestMapping("/popp/practitioner/api/v1/token-deliveries")
@Tag(name = "fetch Popp Token")
public class TokenDeliveryController {

  private final TokenDeliveryRequestValidator requestValidator;
  private final TokenDeliveryService deliveryService;

  public TokenDeliveryController(
      final TokenDeliveryRequestValidator requestValidator,
      final TokenDeliveryService deliveryService) {
    this.requestValidator = requestValidator;
    this.deliveryService = deliveryService;
  }

  @Operation(summary = "Generate and deliver PoPP tokens for completed online check-ins")
  @ApiResponse(
      responseCode = "200",
      description = "PoPP tokens and remaining-record indicator",
      content = @Content(schema = @Schema(implementation = TokenDeliveryResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Malformed request",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "No records match the requested institution",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "500",
      description = "Internal error",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public TokenDeliveryResponse deliver(
      @Parameter(
              description = "Client ID and version",
              required = true,
              schema = @Schema(ref = "#/components/schemas/UserAgentSchema"))
          @RequestHeader("x-useragent")
          final String userAgent,
      @RequestBody final TokenDeliveryRequest request) {
    requestValidator.validate(request, userAgent);
    return deliveryService.deliver(request.telematikId(), request.workplaceIds(), request.limit());
  }
}
