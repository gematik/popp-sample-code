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

package de.gematik.refpopp.popp_client.qrcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class StaticQrCodeService {

  static final String POPP_CHECKIN_TYPE = "popp-checkin";

  private final ObjectMapper objectMapper;

  public StaticQrCodeService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String createPayloadJson(String telematikId, String workplaceId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tid", telematikId);
    payload.put("typ", POPP_CHECKIN_TYPE);
    if (workplaceId != null && !workplaceId.isBlank()) {
      payload.put("wpid", workplaceId);
    }

    try {
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize static QR payload", e);
    }
  }

  public byte[] createPngQrCode(String payloadJson, int size) {
    Map<EncodeHintType, Object> hints =
        Map.of(
            EncodeHintType.CHARACTER_SET,
            StandardCharsets.UTF_8.name(),
            EncodeHintType.ERROR_CORRECTION,
            ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN,
            1);

    try {
      var matrix = new QRCodeWriter().encode(payloadJson, BarcodeFormat.QR_CODE, size, size, hints);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(matrix, "PNG", out);
      return out.toByteArray();
    } catch (WriterException e) {
      throw new IllegalArgumentException("Failed to encode QR code", e);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to render QR code image", e);
    }
  }
}
