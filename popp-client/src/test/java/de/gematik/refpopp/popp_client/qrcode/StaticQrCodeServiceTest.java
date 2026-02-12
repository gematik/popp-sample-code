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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

class StaticQrCodeServiceTest {

  private final StaticQrCodeService service = new StaticQrCodeService(new ObjectMapper());

  @Test
  void createsPayloadWithOptionalWorkplaceId() {
    assertThat(service.createPayloadJson("1-234567890", "REZEPTION-1"))
        .isEqualTo("{\"tid\":\"1-234567890\",\"typ\":\"popp-checkin\",\"wpid\":\"REZEPTION-1\"}");

    assertThat(service.createPayloadJson("1-234567890", null))
        .isEqualTo("{\"tid\":\"1-234567890\",\"typ\":\"popp-checkin\"}");

    assertThat(service.createPayloadJson("1-234567890", "   "))
        .isEqualTo("{\"tid\":\"1-234567890\",\"typ\":\"popp-checkin\"}");
  }

  @Test
  void encodesPayloadAsDecodablePngQrCode() throws Exception {
    String payloadJson = service.createPayloadJson("1-234567890", "REZEPTION-1");
    byte[] png = service.createPngQrCode(payloadJson, 300);

    assertThat(png).isNotEmpty();
    assertThat(decodePngQr(png)).isEqualTo(payloadJson);
  }

  @Test
  void createPayloadJsonWrapsSerializationErrors() {
    ObjectMapper mapper = Mockito.mock(ObjectMapper.class);
    RuntimeException rootCause = new RuntimeException("boom");
    Mockito.when(mapper.writeValueAsString(Mockito.any())).thenThrow(rootCause);

    StaticQrCodeService sut = new StaticQrCodeService(mapper);

    assertThatThrownBy(() -> sut.createPayloadJson("1-234567890", "REZEPTION-1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed to serialize static QR payload")
        .hasCause(rootCause);
  }

  private static String decodePngQr(byte[] png) throws Exception {
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    assertThat(image).isNotNull();

    BinaryBitmap bitmap =
        new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
    Result result = new MultiFormatReader().decode(bitmap);
    return result.getText();
  }
}
