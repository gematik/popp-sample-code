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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.gematik.openhealth.asn1.Asn1TagClass;
import de.gematik.openhealth.asn1.Asn1TagForm;
import de.gematik.openhealth.asn1.Openhealth_asn1Kt;
import de.gematik.openhealth.crypto.Openhealth_cryptoKt;
import de.gematik.openhealth.healthcard.ApduException;
import de.gematik.openhealth.healthcard.HealthCardCommand;
import de.gematik.poppcommons.api.messages.ScenarioStep;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class VirtualCardServiceTest {
  private static final String GENERAL_AUTHENTICATE_STEP_1 =
      "10 86 0000107c0ec30c000a8027600101169990210100";
  private static final String GENERAL_AUTHENTICATE_STEP_2 =
      "00 86 0000    45"
          + " 7c43854104987fce93bfc191e4db006b56f8fd5f749d256fc5842f0f3f31becf613ce146f66318f77ff7ee51c10b6b6a0f349896400c7601bfc07608ff08fe0ce1d921ca42";
  private static final String APDU_RESPONSE_READ_VERSION =
      "ef2bc003020000c103040502c21045474b5f47322e312020202020040502c403010000c503020000c703010000";
  private static final String APDU_RESPONSE_READ_SUB_CA_CV_CERTIFICATE =
      "7F2181D87F4E81915F290170420844454758588702227F494D06082A8648CE3D04030286410428405A0CCC5C53B6780356A5141EB47FED5F56BE44BC22F2046FC053FEDBC25E50E24A6D6AF95C1CFEE9497ACCE359A253F7D0B7ABAEA5D1A62DE030145F0C975F200844454758581102237F4C1306082A8214004C0481185307800000000000005F25060203000703015F24060301000703005F37404CD260C0803B125A001BA81BA9F2E2B1390DE4F14691C822A28CC776A186D7BA7F08704C27FDCDAEB1F8B243A37976CF37BF7C121858D0F0419DE83217A395DE";

  private VirtualCardService virtualCardService;

  @BeforeEach
  void setUp() {
    var eventPublisher = mock(ApplicationEventPublisher.class);

    virtualCardService =
        new VirtualCardService(
            eventPublisher,
            "IMG_eGK_G21_TU_root6 1.xml",
            "00 a4 040c    07 D2760001448000",
            "00 b0 9100    00",
            "00 b0 8700    00",
            "00 b0 8600    00",
            "80 ca 0100    00   0000",
            "00 22 41A4    06   840109  800154",
            GENERAL_AUTHENTICATE_STEP_1,
            GENERAL_AUTHENTICATE_STEP_2,
            "00 a4 040c   0a   a000000167455349474e",
            "00 b0 8400   00   0000");
  }

  @Test
  void isConfiguredTrue() {
    assertTrue(virtualCardService.isConfigured());
  }

  @Test
  void isConfiguredFalse() {
    virtualCardService.setCvCertificate(null);
    virtualCardService.setAuthCertificate(null);
    assertFalse(virtualCardService.isConfigured());
  }

  @Test
  void isConfiguredFalseWhenAuthCertificateIsMissing() {
    virtualCardService.setCvCertificate("CV_CERT");
    virtualCardService.setAuthCertificate(null);

    assertFalse(virtualCardService.isConfigured());
  }

  @Test
  void processScenarioList() {
    ScenarioStep step1 = Mockito.mock(ScenarioStep.class);
    ScenarioStep step2 = Mockito.mock(ScenarioStep.class);
    ScenarioStep step3 = Mockito.mock(ScenarioStep.class);
    ScenarioStep step4 = Mockito.mock(ScenarioStep.class);

    when(step1.getCommandApdu()).thenReturn("00b0860000");
    when(step2.getCommandApdu()).thenReturn(GENERAL_AUTHENTICATE_STEP_1);
    when(step3.getCommandApdu()).thenReturn("00b08400000000");
    when(step4.getCommandApdu()).thenReturn("00a4040c07D2760001448000");

    List<String> responses = virtualCardService.process(List.of(step1, step2, step3, step4));

    assertEquals(4, responses.size());

    assertEquals(
        virtualCardService.getCvCertificate() + VirtualCardService.APDU_RESPONSE_OK,
        responses.get(0));
    assertEquals(
        VirtualCardService.APDU_RESPONSE_OK,
        responses
            .get(1)
            .substring(responses.get(1).length() - VirtualCardService.APDU_RESPONSE_OK.length()));
    assertTrue(responses.get(1).startsWith("7C"));
    assertEquals(
        virtualCardService.getAuthCertificate() + VirtualCardService.APDU_RESPONSE_OK,
        responses.get(2));
    assertEquals(VirtualCardService.APDU_RESPONSE_OK, responses.get(3));

    verify(step1).getCommandApdu();
    verify(step2).getCommandApdu();
    verify(step3).getCommandApdu();
    verify(step4).getCommandApdu();
    verifyNoMoreInteractions(step1, step2, step3, step4);
  }

  @Test
  void processReturnsStaticAndDynamicResponsesAndReportsConfiguredState() {
    final var eventPublisher = mock(ApplicationEventPublisher.class);

    final var service =
        new VirtualCardService(
            eventPublisher,
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

    Assertions.assertThat(service.isConfigured()).isFalse();

    service.setCvCertificate("CV_CERT");
    service.setAuthCertificate("AUTH_CERT");

    Assertions.assertThat(service.isConfigured()).isTrue();
    Assertions.assertThat(service.getCvCertificate()).isEqualTo("CV_CERT");
    Assertions.assertThat(service.getAuthCertificate()).isEqualTo("AUTH_CERT");

    final var steps =
        List.of(
            new ScenarioStep("00 B0 02 00", List.of(VirtualCardService.APDU_RESPONSE_OK)),
            new ScenarioStep("00 82 00 00", List.of(VirtualCardService.APDU_RESPONSE_OK)),
            new ScenarioStep("00 B0 04 00", List.of(VirtualCardService.APDU_RESPONSE_OK)),
            new ScenarioStep("00 B0 00 00", List.of(VirtualCardService.APDU_RESPONSE_OK)),
            new ScenarioStep("00 A4 00 00", List.of(VirtualCardService.APDU_RESPONSE_OK)));

    final var responses = service.process(steps);

    Assertions.assertThat(responses).hasSize(5);
    Assertions.assertThat(responses.get(0))
        .isEqualTo("CV_CERT" + VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(1))
        .startsWith("7C")
        .endsWith(VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(2))
        .isEqualTo("AUTH_CERT" + VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(4)).isEqualTo(VirtualCardService.APDU_RESPONSE_OK);
  }

  @Test
  void processSupportsOpenHealthDefaultsWithoutConfiguredCommandApdus() {
    final var eventPublisher = mock(ApplicationEventPublisher.class);
    final var service =
        new VirtualCardService(
            eventPublisher, "IMG_eGK_G21_TU_root6 1.xml", "", "", "", "", "", "", "", "", "", "");
    service.setCvCertificate("CV_CERT");
    service.setAuthCertificate("AUTH_CERT");

    final var responses =
        service.process(
            List.of(
                new ScenarioStep("00 A4 04 0C 07 D2760001448000", List.of("9000")),
                new ScenarioStep("00 B0 91 00 00", List.of("9000")),
                new ScenarioStep("00 B0 87 00 00", List.of("9000")),
                new ScenarioStep("80 CA 01 00 00 00 00", List.of("9000")),
                new ScenarioStep("00 B0 86 00 00", List.of("9000")),
                new ScenarioStep(GENERAL_AUTHENTICATE_STEP_1, List.of("9000")),
                new ScenarioStep("00 86 0000", List.of("9000")),
                new ScenarioStep("00 B0 84 00 00 00 00", List.of("9000")),
                new ScenarioStep("0C B0 84 00 00", List.of("9000")),
                new ScenarioStep("0C A4 04 0C 0A A000000167455349474E", List.of("9000"))));

    Assertions.assertThat(responses.get(0)).isEqualTo(VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(1))
        .isEqualTo(APDU_RESPONSE_READ_VERSION + VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(2))
        .isEqualTo(APDU_RESPONSE_READ_SUB_CA_CV_CERTIFICATE + VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(3))
        .isEqualTo(
            VirtualCardService.APDU_RESPONSE_RETRIEVE_PUBLIC_KEY_IDENTIFIERS
                + VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(4))
        .isEqualTo("CV_CERT" + VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(5))
        .startsWith("7C")
        .endsWith(VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(6)).isEqualTo(VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(7))
        .isEqualTo("AUTH_CERT" + VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(8))
        .isEqualTo("AUTH_CERT" + VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(9)).isEqualTo(VirtualCardService.APDU_RESPONSE_OK);
  }

  @Test
  void processReturnsOkForUnknownApdu() {
    final var eventPublisher = mock(ApplicationEventPublisher.class);
    final var service =
        new VirtualCardService(
            eventPublisher,
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

    final var responses =
        service.process(List.of(new ScenarioStep("DE AD BE EF", List.of("9000"))));

    Assertions.assertThat(responses).containsExactly(VirtualCardService.APDU_RESPONSE_OK);
  }

  @Test
  void processReturnsProtectedResponseForSecureMessagingRead() {
    final var eventPublisher = mock(ApplicationEventPublisher.class);
    final var service =
        new VirtualCardService(
            eventPublisher,
            "IMG_eGK_G21_TU_root6 1.xml",
            "00 a4 040c    07 D2760001448000",
            "00 b0 9100    00",
            "00 b0 8700    00",
            "00 b0 8600    00",
            "80 ca 0100    00   0000",
            "00 22 41A4    06   840109  800154",
            GENERAL_AUTHENTICATE_STEP_1,
            GENERAL_AUTHENTICATE_STEP_2,
            "00 a4 040c   0a   a000000167455349474e",
            "00 b0 8400   00   0000");

    final var step1Response =
        service.process(List.of(new ScenarioStep(GENERAL_AUTHENTICATE_STEP_1, List.of("9000"))));

    Assertions.assertThat(step1Response.getFirst())
        .startsWith("7C")
        .endsWith(VirtualCardService.APDU_RESPONSE_OK);

    final var handshakeResponses =
        service.process(
            List.of(
                new ScenarioStep(GENERAL_AUTHENTICATE_STEP_2, List.of("9000")),
                new ScenarioStep("0C A4 04 0C 0A A000000167455349474E", List.of("9000")),
                new ScenarioStep("0C B0 84 00 00", List.of("9000"))));

    Assertions.assertThat(handshakeResponses.get(0)).isEqualTo(VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(handshakeResponses.get(1)).endsWith(VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(handshakeResponses.get(2)).startsWith("81");

    final byte[] selectStatusWord = readProtectedField(handshakeResponses.get(1), 0x19);
    final byte[] selectMac = readProtectedField(handshakeResponses.get(1), 0x0E);
    final byte[] responseData = readProtectedField(handshakeResponses.get(2), 0x01);
    final byte[] responseStatusWord = readProtectedField(handshakeResponses.get(2), 0x19);
    final byte[] responseMac = readProtectedField(handshakeResponses.get(2), 0x0E);

    Assertions.assertThat(selectStatusWord).containsExactly((byte) 0x90, 0x00);
    Assertions.assertThat(selectMac).hasSize(8);
    Assertions.assertThat(responseData)
        .isEqualTo(HexFormat.of().parseHex(service.getAuthCertificate()));
    Assertions.assertThat(responseStatusWord).containsExactly((byte) 0x90, 0x00);
    Assertions.assertThat(responseMac).hasSize(8);
  }

  @Test
  void processSignsInternalAuthenticateChallengeWithCvcKey() throws Exception {
    final byte[] nonce =
        HexFormat.of().parseHex("000102030405060708090A0B0C0D0E0F1011121314151617");
    final var internalAuthenticate =
        commandApduHex(HealthCardCommand.Companion.internalAuthenticate(nonce));

    final var response =
        virtualCardService
            .process(
                List.of(
                    new ScenarioStep(
                        internalAuthenticate, List.of(VirtualCardService.APDU_RESPONSE_OK))))
            .getFirst();

    Assertions.assertThat(response).endsWith(VirtualCardService.APDU_RESPONSE_OK);
    final byte[] signature = HexFormat.of().parseHex(response.substring(0, response.length() - 4));
    final byte[] tau = Arrays.copyOf(nonce, nonce.length + 1);
    try (final var cvc =
        Openhealth_asn1Kt.parseCvCertificate(
            HexFormat.of().parseHex(virtualCardService.getCvCertificate()))) {
      Assertions.assertThat(signature).hasSize(64);
      Assertions.assertThat(Openhealth_cryptoKt.verifyCvcEcdsaValueSignature(cvc, tau, signature))
          .isTrue();
    }
  }

  @Test
  void constructorLoadsImageFromClasspathPrefix() {
    final var service = newService("classpath:IMG_eGK_G21_TU_root6 1.xml");

    Assertions.assertThat(service.isConfigured()).isTrue();
    Assertions.assertThat(service.getCvCertificate()).isNotBlank();
    Assertions.assertThat(service.getAuthCertificate()).isNotBlank();
  }

  @Test
  void constructorLoadsImageFromFilesystem() throws Exception {
    final Path imageFile = Files.createTempFile("virtual-card", ".xml");
    try (InputStream source =
        VirtualCardServiceTest.class
            .getClassLoader()
            .getResourceAsStream("IMG_eGK_G21_TU_root6 1.xml")) {
      Assertions.assertThat(source).isNotNull();
      Files.copy(source, imageFile, StandardCopyOption.REPLACE_EXISTING);
    }

    final var service = newService(imageFile.toString());

    Assertions.assertThat(service.isConfigured()).isTrue();
  }

  @Test
  void constructorTreatsNullImageAsNotConfigured() {
    final var service = newService(null);

    Assertions.assertThat(service.isConfigured()).isFalse();
  }

  @Test
  void constructorFailsWhenClasspathImageIsMissing() {
    Assertions.assertThatThrownBy(this::createServiceWithMissingClasspathImage)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Error when loading XML card image file")
        .hasRootCauseInstanceOf(FileNotFoundException.class)
        .hasRootCauseMessage("Classpath resource not found: missing-virtual-card.xml");
  }

  @Test
  void constructorFailsWhenImageCannotBeFound() {
    Assertions.assertThatThrownBy(this::createServiceWithMissingImage)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Error when loading XML card image file")
        .hasRootCauseInstanceOf(FileNotFoundException.class)
        .hasRootCauseMessage("File not found on filesystem or classpath: missing-virtual-card.xml");
  }

  @Test
  void processInternalAuthenticateFailsWithoutCvcPrivateKey() throws Exception {
    final var service = newService("");
    final var internalAuthenticate =
        commandApduHex(HealthCardCommand.Companion.internalAuthenticate(new byte[24]));
    final var steps =
        List.of(
            new ScenarioStep(internalAuthenticate, List.of(VirtualCardService.APDU_RESPONSE_OK)));

    Assertions.assertThatThrownBy(() -> service.process(steps))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No CVC private key configured for virtual card.");
  }

  @Test
  void processInternalAuthenticateFailsWithInvalidCvcPrivateKey() throws Exception {
    final var service = newService("");
    service.setEgkAuthCvcPrivateKey(new byte[32]);
    final var internalAuthenticate =
        commandApduHex(HealthCardCommand.Companion.internalAuthenticate(new byte[24]));
    final var steps =
        List.of(
            new ScenarioStep(internalAuthenticate, List.of(VirtualCardService.APDU_RESPONSE_OK)));

    Assertions.assertThatThrownBy(() -> service.process(steps))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Invalid CVC private key configured for virtual card.");
  }

  @Test
  void processReturnsOkForOpenHealthContactlessPrivateKeySelection() throws Exception {
    final var service = newService("");
    final var selectContactlessCvcKey =
        commandApduHex(
            HealthCardCommand.Companion.manageSecEnvSelectPrivateKey((byte) 0x09, (byte) 0x00));

    final var response =
        service
            .process(
                List.of(
                    new ScenarioStep(
                        selectContactlessCvcKey, List.of(VirtualCardService.APDU_RESPONSE_OK))))
            .getFirst();

    Assertions.assertThat(response).isEqualTo(VirtualCardService.APDU_RESPONSE_OK);
  }

  private VirtualCardService newService(final String imageFile) {
    return new VirtualCardService(
        mock(ApplicationEventPublisher.class), imageFile, "", "", "", "", "", "", "", "", "", "");
  }

  private void createServiceWithMissingClasspathImage() {
    newService("classpath:missing-virtual-card.xml");
  }

  private void createServiceWithMissingImage() {
    newService("missing-virtual-card.xml");
  }

  private String commandApduHex(final HealthCardCommand command) throws ApduException {
    return HexFormat.of().formatHex(command.toApdu(false).toVec().cloneAsNonzeroizingVec());
  }

  private byte[] readProtectedField(final String responseHex, final int tagNumber) {
    try {
      final byte[] responseBytes = HexFormat.of().parseHex(responseHex);
      final byte[] payload = Arrays.copyOf(responseBytes, responseBytes.length - 2);
      return Openhealth_asn1Kt.readTaggedObjectValue(
          payload, Asn1TagClass.CONTEXT_SPECIFIC, Asn1TagForm.PRIMITIVE, tagNumber);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to read protected response field", e);
    }
  }
}
