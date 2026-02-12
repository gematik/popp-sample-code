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

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import de.gematik.refpopp.popp_client.qrcode.StaticQrCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/qrcode")
@Tag(
    name = "QR Code",
    description =
        "API for generating a static PoPP check-in QR code (JSON payload, UTF-8, ISO/IEC 18004).")
public class StaticQrCodeController {

  private final StaticQrCodeService staticQrCodeService;
  private final String defaultTelematikId;
  private final String defaultWorkplaceId;
  private final int defaultSize;

  public StaticQrCodeController(
      StaticQrCodeService staticQrCodeService,
      @Value("${popp-client.static-qr.tid:}") String defaultTelematikId,
      @Value("${popp-client.static-qr.wpid:}") String defaultWorkplaceId,
      @Value("${popp-client.static-qr.size:300}") int defaultSize) {
    this.staticQrCodeService = staticQrCodeService;
    this.defaultTelematikId = defaultTelematikId;
    this.defaultWorkplaceId = defaultWorkplaceId;
    this.defaultSize = defaultSize;
  }

  @Operation(
      summary = "Generate a static check-in QR code (PNG)",
      description =
          """
          Encodes the JSON payload:
          - tid: Telematik-ID
          - typ: fixed value "popp-checkin"
          - wpid: Workplace-ID (optional)

          Default values are taken from `application-dev.yaml` (profile `dev`).
          """,
      responses = {
        @ApiResponse(responseCode = "200", description = "QR code image (PNG)."),
        @ApiResponse(
            responseCode = "400",
            description = "Missing Telematik-ID.",
            content = @Content)
      })
  @GetMapping(value = "/static", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> createStaticQrCode(
      @RequestParam(name = "tid", required = false) String telematikId,
      @RequestParam(name = "wpid", required = false) String workplaceId,
      @RequestParam(name = "size", required = false) Integer size) {
    String tid = firstNonBlank(telematikId, defaultTelematikId);
    if (tid == null) {
      throw new ResponseStatusException(BAD_REQUEST, "Missing required parameter tid");
    }

    String wpid = firstNonBlank(workplaceId, defaultWorkplaceId);
    int qrSize = (size != null ? size : defaultSize);
    if (qrSize < 64 || qrSize > 2048) {
      throw new ResponseStatusException(BAD_REQUEST, "Invalid size (allowed: 64..2048)");
    }

    String payloadJson = staticQrCodeService.createPayloadJson(tid, wpid);
    byte[] png = staticQrCodeService.createPngQrCode(payloadJson, qrSize);

    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.noStore())
        .body(png);
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    if (second != null && !second.isBlank()) {
      return second;
    }
    return null;
  }
}
