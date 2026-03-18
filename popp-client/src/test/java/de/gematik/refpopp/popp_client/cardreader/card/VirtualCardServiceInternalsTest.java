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

package de.gematik.refpopp.popp_client.cardreader.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.security.Security;
import java.util.HexFormat;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class VirtualCardServiceInternalsTest {

  private VirtualCardService newService() {
    return new VirtualCardService(
        mock(ApplicationEventPublisher.class),
        "",
        "00 A4 00 00",
        "00 B0 00 00",
        "00 B0 01 00",
        "00 B0 02 00",
        "00 B0 03 00",
        "00 A4 00 01",
        "00 82 00 00",
        "00 82 00 01",
        "00 A4 00 02",
        "00 B0 04 00");
  }

  @Test
  void buildEphemeralPublicKeyResponseProduces7cFrame() throws Exception {
    final var service = newService();
    final Method m = VirtualCardService.class.getDeclaredMethod("buildEphemeralPublicKeyResponse");
    m.setAccessible(true);

    final String hex = (String) m.invoke(service);

    assertAll(
        () -> assertThat(hex).isNotBlank(),
        () -> assertThat(hex).startsWith("7C"),
        () -> assertThat(hex.length() % 2).isZero(),
        () -> assertThat(HexFormat.of().parseHex(hex)).hasSizeGreaterThan(10));
  }

  @Test
  void toFixedLengthPadsOrTrims() throws Exception {
    final var service = newService();
    final Method m =
        VirtualCardService.class.getDeclaredMethod("toFixedLength", byte[].class, int.class);
    m.setAccessible(true);

    byte[] padded = (byte[]) m.invoke(service, new byte[] {0x01, 0x02}, 4);
    byte[] trimmed = (byte[]) m.invoke(service, new byte[] {0x01, 0x02, 0x03}, 2);

    assertAll(
        () -> assertThat(padded).containsExactly(0x00, 0x00, 0x01, 0x02),
        () -> assertThat(trimmed).containsExactly(0x02, 0x03));
  }

  @Test
  void normalizeRemovesSpacesAndUpperCases() throws Exception {
    final var service = newService();
    final Method m = VirtualCardService.class.getDeclaredMethod("normalize", String.class);
    m.setAccessible(true);

    String normalized = (String) m.invoke(service, "00 b0 0a 00");
    assertThat(normalized).isEqualTo("00B00A00");
  }

  @Test
  void bouncyCastleProviderRegistered() {
    assertThat(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)).isNotNull();
  }
}
