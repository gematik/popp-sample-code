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

import de.gematik.refpopp.popp_server.controller.dto.CheckInStatusResponse;
import de.gematik.refpopp.popp_server.tokengeneration.PoppTokenGenerationRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.beans.PropertyEditorSupport;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST entry point for reading the stored status of a mobile check-in. */
@RestController
@RequestMapping("/popp/patient/api/v1/mobile/checkin")
@Tag(name = "status popp")
public class MobileCheckInStatusController {

  private final PoppTokenGenerationRecordService recordService;

  public MobileCheckInStatusController(final PoppTokenGenerationRecordService recordService) {
    this.recordService = recordService;
  }

  @InitBinder
  void bindUuid(WebDataBinder binder) {
    binder.registerCustomEditor(
        UUID.class,
        new PropertyEditorSupport() {
          @Override
          public void setAsText(String text) {
            final var id = UUID.fromString(text);
            // UUID.fromString also accepts shortened groups that do not match the UUID format.
            if (!id.toString().equalsIgnoreCase(text)) {
              throw new IllegalArgumentException("Invalid UUID format");
            }
            setValue(id);
          }
        });
  }

  @Operation(
      operationId = "readStatusPoPP",
      summary = "Read the status of PoPP-Token generation to a poppStatusRecordId",
      description =
          """
          The poppStatusRecordId identifies the PoPP record of the check-in
          process.
          The PoPP-Service reads the record and generates a response with the
          current status of the PoPP-Token generation.
          """)
  @SecurityRequirements
  @ApiResponse(
      responseCode = "200",
      description =
          """
          HttpStatus.OK (200) and status information about PoPP-Token Generation Process
          - success: - PoPP data record and PoPP-Token generated and sent to health company
          - pending: - PoPP data record was generated but not yet sent to health company
          PoPP-Server is trying to send the PoPP-Token
          - canceled: - The process for generating the PoPP-Token was aborted.
          Abbortion occurred either due to an error, because transmission to the
          healthcare company was not possible within the expired deadline,
          or because the healthcare company rejected the PoPP-Token.
          The response also contains the ID of the created message record for the
          PoPP record.
          """,
      content = @Content(schema = @Schema(implementation = CheckInStatusResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "HttpStatus.BAD_REQUEST (400)",
      content =
          @Content(
              schema = @Schema(ref = "#/components/schemas/ErrorType"),
              examples = @ExampleObject(value = "{\"errorCode\":\"malformedRequest\"}")))
  @ApiResponse(
      responseCode = "404",
      description = "HttpStatus.NOT_FOUND (404)",
      content =
          @Content(
              schema = @Schema(ref = "#/components/schemas/ErrorType"),
              examples = @ExampleObject(value = "{\"errorCode\":\"noResource\"}")))
  @ApiResponse(
      responseCode = "500",
      description = "HttpStatus.INTERNAL_SERVER_ERROR (500)",
      content =
          @Content(
              schema = @Schema(ref = "#/components/schemas/ErrorType"),
              examples = @ExampleObject(value = "{\"errorCode\":\"internalError\"}")))
  @GetMapping(value = "/poppStatusRecordId", produces = MediaType.APPLICATION_JSON_VALUE)
  public CheckInStatusResponse readStatus(
      @Parameter(
              description =
                  "Request to read the status of PoPP-Token generation to a poppStatusRecordId",
              required = true,
              schema = @Schema(ref = "#/components/schemas/PoppStatusRecordIdSchema"))
          @RequestParam("statusrequest")
          final UUID statusRequest) {
    final var message = recordService.readStatus(statusRequest);
    return new CheckInStatusResponse(message.getStatus().getValue(), message.getId());
  }
}
