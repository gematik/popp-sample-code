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

package de.gematik.refpopp.popp_server.scenario.common.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CardApduFactoryTest {

  private static final HexFormat HEX_FORMAT = HexFormat.of();
  private static final byte[] TRUSTED_CHANNEL_CHR = HEX_FORMAT.parseHex("000a80276001011699902101");

  private CardApduFactory sut;

  @BeforeEach
  void setUp() {
    sut = new CardApduFactory(TRUSTED_CHANNEL_CHR);
  }

  @Test
  void buildsStaticScenarioApdusFromOpenHealth() {
    assertThat(sut.selectMasterFile()).isEqualTo("00a4040c07d2760001448000");
    assertThat(sut.readVersion()).isEqualTo("00b0910000");
    assertThat(sut.readSubCaCvCertificate()).isEqualTo("00b0870000");
    assertThat(sut.readEndEntityCvCertificate()).isEqualTo("00b0860000");
    assertThat(sut.retrievePublicKeyIdentifiers()).isEqualTo("80ca0100000000");
    assertThat(sut.selectPrivateKeyForTrustedChannel()).isEqualTo("002241a406840109800154");
    assertThat(sut.selectPrivateKeyForContactless()).isEqualTo("002241a406840109800100");
    assertThat(sut.selectDfEsign()).isEqualTo("00a4040c0aa000000167455349474e");
    assertThat(sut.readEfCChAutE256()).isEqualTo("00b08400000000");
  }

  @Test
  void buildsTrustedChannelSpecificApdusFromOpenHealth() {
    assertThat(sut.manageSecEnvSetSignatureKeyReference(HEX_FORMAT.parseHex("4445475858870222")))
        .isEqualTo("002281b60a83084445475858870222");
    assertThat(sut.psoComputeDigitalSignatureCvc(HEX_FORMAT.parseHex("010203")))
        .isEqualTo("002a00be03010203");
    assertThat(sut.generalAuthenticateMutualAuthenticationStep1())
        .isEqualTo("10860000107c0ec30c000a8027600101169990210100");
  }

  @Test
  void buildsInternalAuthenticateFromOpenHealthBuilder() {
    assertThat(
            sut.internalAuthenticate(
                HEX_FORMAT.parseHex("000102030405060708090a0b0c0d0e0f1011121314151617")))
        .isEqualTo("0088000018000102030405060708090a0b0c0d0e0f101112131415161700");
  }

  @Test
  void buildsElcStep2FromInjectedEphemeralKey() throws IOException {
    final byte[] cvcBytes =
        Files.readAllBytes(
            Path.of("src/main/resources/certificates/cvc/80276001011699902101-cvc-flag0.crt"));
    final byte[] ephemeralPublicKey = new byte[65];
    IntStream.range(0, ephemeralPublicKey.length)
        .forEach(index -> ephemeralPublicKey[index] = (byte) (index == 0 ? 0x04 : index));
    sut = new CardApduFactory(TRUSTED_CHANNEL_CHR, cvc -> ephemeralPublicKey);

    final var commandApdu = sut.generalAuthenticateElcStep2(cvcBytes);

    assertThat(commandApdu)
        .isEqualTo("00860000457c438541" + HEX_FORMAT.formatHex(ephemeralPublicKey));
  }
}
