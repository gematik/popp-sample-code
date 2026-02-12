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

package de.gematik.refpopp.popp_client.controller;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.refpopp.popp_client.cardreader.CardReader;
import de.gematik.refpopp.popp_client.client.CommunicationService;
import de.gematik.refpopp.popp_client.controller.dto.PoppClientRequest;
import de.gematik.refpopp.popp_client.controller.dto.PoppClientResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/token")
@Tag(
    name = "Token",
    description =
        """
        API for generating PoPP tokens based on different card communication types.

        <b>This API is intended for testing purposes only and is not an official API for the PoPP client.</b>
        """)
@RequiredArgsConstructor
@Slf4j
public class TokenController {

  private final CommunicationService communicationService;
  private final CardReader cardReaderService;

  @Operation(
      summary = "Generate a PoPP token",
      description =
          """
          Starts the communication process using the selected card connection type
          and generates a PoPP token.

          • Contact and contactless communication may require a card reader.
          • Connector communication may use a clientSessionId.

          Returns a structured PoPP token on success.
          """,
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "Input parameters required for PoPP token generation.",
              required = true,
              content =
                  @Content(
                      schema = @Schema(implementation = PoppClientRequest.class),
                      examples = {
                        @ExampleObject(
                            name = "Contact Standard",
                            value =
                                """
                                {
                                  "communicationType": "contact-standard",
                                  "clientSessionId": "123456"
                                }
                                """),
                        @ExampleObject(
                            name = "Contactless Standard",
                            value =
                                """
                                {
                                  "communicationType": "contactless-standard",
                                  "clientSessionId": ""
                                }
                                """),
                        @ExampleObject(
                            name = "Contact Virtual",
                            value =
                                """
                                {
                                  "communicationType": "contact-virtual",
                                  "clientSessionId": "123456"
                                }
                                """)
                      })),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Token successfully generated.",
            content = @Content(schema = @Schema(implementation = PoppClientResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Unsupported or invalid communication type.",
            content = @Content(schema = @Schema(implementation = PoppClientResponse.class))),
        @ApiResponse(
            responseCode = "504",
            description = "The token generation timed out.",
            content = @Content(schema = @Schema(implementation = PoppClientResponse.class))),
        @ApiResponse(
            responseCode = "500",
            description = "Unexpected server error.",
            content = @Content(schema = @Schema(implementation = PoppClientResponse.class)))
      })
  @PostMapping
  public ResponseEntity<PoppClientResponse> createToken(
      @Valid @RequestBody PoppClientRequest request) {
    String clientSessionId = request.clientSessionId();
    try {
      if (request.communicationType().requiresCardReader()) {
        cardReaderService.startCheckForCardReader();
      }
      String token = startCommunication(request.communicationType(), clientSessionId);

      return ResponseEntity.ok(PoppClientResponse.ok(token));
    } catch (UnsupportedOperationException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(PoppClientResponse.error(e.getMessage()));
    } catch (RuntimeException e) {
      if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
            .body(PoppClientResponse.error("Token generation timed out"));
      }

      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(PoppClientResponse.error("Unexpected error: " + e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(PoppClientResponse.error(e.getMessage()));
    }
  }

  private String startCommunication(CardConnectionType type, String clientSessionId) {
    return switch (type) {
      case CONTACT_CONNECTOR_VIA_STANDARD_TERMINAL ->
          communicationService.startConnectorMock(clientSessionId);
      case CONTACT_STANDARD, CONTACTLESS_STANDARD, CONTACT_CONNECTOR, CONTACTLESS_CONNECTOR ->
          communicationService.start(type, clientSessionId);
      case CONTACT_VIRTUAL ->
          communicationService.startVirtualCard(
              CardConnectionType.CONTACT_STANDARD, clientSessionId);

      case G3 -> throw new UnsupportedOperationException("G3 not yet implemented");
      default -> throw new UnsupportedOperationException("Unsupported type: " + type);
    };
  }
}
