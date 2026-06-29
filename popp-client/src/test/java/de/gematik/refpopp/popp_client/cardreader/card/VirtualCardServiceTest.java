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
import java.lang.reflect.Method;
import java.math.BigInteger;
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
    virtualCardService = new VirtualCardService("IMG_eGK_G21_TU_root6 1.xml");
  }

  @Test
  void isConfiguredTrue() {
    assertTrue(virtualCardService.isConfigured());
  }

  @Test
  void isConfiguredFalse() {
    final var service =
        serviceWithCardData(
            null, null, APDU_RESPONSE_READ_SUB_CA_CV_CERTIFICATE, APDU_RESPONSE_READ_VERSION, null);

    assertFalse(service.isConfigured());
  }

  @Test
  void isConfiguredFalseWhenAuthCertificateIsMissing() {
    final var service =
        serviceWithCardData(
            "CV_CERT",
            null,
            APDU_RESPONSE_READ_SUB_CA_CV_CERTIFICATE,
            APDU_RESPONSE_READ_VERSION,
            null);

    assertFalse(service.isConfigured());
  }

  @Test
  void constructorUsesEmptyCardDataWhenExplicitCardDataIsNull() {
    final VirtualCardImageData cardData = null;
    final var service = new VirtualCardService(cardData, new ApduMatcher());

    Assertions.assertThat(service.isConfigured()).isFalse();
    Assertions.assertThat(service.getCvCertificate()).isNull();
    Assertions.assertThat(service.getAuthCertificate()).isNull();
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
    final var service =
        serviceWithCardData(
            "CV_CERT",
            "AUTH_CERT",
            APDU_RESPONSE_READ_SUB_CA_CV_CERTIFICATE,
            APDU_RESPONSE_READ_VERSION,
            null);

    Assertions.assertThat(service.isConfigured()).isTrue();
    Assertions.assertThat(service.getCvCertificate()).isEqualTo("CV_CERT");
    Assertions.assertThat(service.getAuthCertificate()).isEqualTo("AUTH_CERT");

    final var steps =
        List.of(
            new ScenarioStep("00 B0 86 00 00", List.of(VirtualCardService.APDU_RESPONSE_OK)),
            new ScenarioStep(
                GENERAL_AUTHENTICATE_STEP_1, List.of(VirtualCardService.APDU_RESPONSE_OK)),
            new ScenarioStep("00 B0 84 00 00 00 00", List.of(VirtualCardService.APDU_RESPONSE_OK)),
            new ScenarioStep("00 B0 00 00", List.of(VirtualCardService.APDU_RESPONSE_OK)),
            new ScenarioStep(
                "00 A4 04 0C 07 D2760001448000", List.of(VirtualCardService.APDU_RESPONSE_OK)));

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
    final var service =
        serviceWithCardData(
            "CV_CERT",
            "AUTH_CERT",
            APDU_RESPONSE_READ_SUB_CA_CV_CERTIFICATE,
            APDU_RESPONSE_READ_VERSION,
            null);

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
    final var service = new VirtualCardService("");

    final var responses =
        service.process(List.of(new ScenarioStep("DE AD BE EF", List.of("9000"))));

    Assertions.assertThat(responses).containsExactly(VirtualCardService.APDU_RESPONSE_OK);
  }

  @Test
  void processReturnsProtectedResponseForSecureMessagingRead() {
    final var service = new VirtualCardService("IMG_eGK_G21_TU_root6 1.xml");
    final var sessionState = new VirtualCardSessionState();

    final var step1Response =
        service.process(
            List.of(new ScenarioStep(GENERAL_AUTHENTICATE_STEP_1, List.of("9000"))), sessionState);

    Assertions.assertThat(step1Response.getFirst())
        .startsWith("7C")
        .endsWith(VirtualCardService.APDU_RESPONSE_OK);

    final var handshakeResponses =
        service.process(
            List.of(
                new ScenarioStep(GENERAL_AUTHENTICATE_STEP_2, List.of("9000")),
                new ScenarioStep("0C A4 04 0C 0A A000000167455349474E", List.of("9000")),
                new ScenarioStep("0C B0 84 00 00", List.of("9000"))),
            sessionState);

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
    final var service =
        serviceWithCardData(
            null,
            null,
            APDU_RESPONSE_READ_SUB_CA_CV_CERTIFICATE,
            APDU_RESPONSE_READ_VERSION,
            new byte[32]);
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

  @Test
  void processReturnsOkForOpenHealthTrustedPrivateKeySelection() throws Exception {
    final var service = newService("");
    final var selectTrustedCvcKey =
        commandApduHex(
            HealthCardCommand.Companion.manageSecEnvSelectPrivateKey((byte) 0x09, (byte) 0x54));

    final var response =
        service
            .process(
                List.of(
                    new ScenarioStep(
                        selectTrustedCvcKey, List.of(VirtualCardService.APDU_RESPONSE_OK))))
            .getFirst();

    Assertions.assertThat(response).isEqualTo(VirtualCardService.APDU_RESPONSE_OK);
  }

  @Test
  void processReturnsOkForOpenHealthDfEsignSelection() throws Exception {
    final var service = newService("");
    final var selectDfEsign =
        commandApduHex(
            HealthCardCommand.Companion.selectAid(HexFormat.of().parseHex("A000000167455349474E")));

    final var response =
        service
            .process(
                List.of(
                    new ScenarioStep(selectDfEsign, List.of(VirtualCardService.APDU_RESPONSE_OK))))
            .getFirst();

    Assertions.assertThat(response).isEqualTo(VirtualCardService.APDU_RESPONSE_OK);
  }

  @Test
  void processMutualAuthenticationStep2WithInvalidHexClearsPendingEphemeralKey() {
    final var service = newService("IMG_eGK_G21_TU_root6 1.xml");
    final var sessionState = new VirtualCardSessionState();

    service.process(
        List.of(new ScenarioStep(GENERAL_AUTHENTICATE_STEP_1, List.of("9000"))), sessionState);
    Assertions.assertThat(sessionState.getPendingCardEphemeralPrivateKey()).isNotNull();

    final var responses =
        service.process(
            List.of(new ScenarioStep("00 86 00 00 45 ZZ", List.of("9000"))), sessionState);

    Assertions.assertThat(responses).containsExactly(VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(sessionState.hasSecureMessagingSession()).isFalse();
    Assertions.assertThat(sessionState.getPendingCardEphemeralPrivateKey()).isNull();
  }

  @Test
  void tryCreateSignatureReturnsEmptyWhenEphemeralPrivateKeyIsInvalid() throws Exception {
    final var service = newService("");
    final var result =
        invokeTryCreateSignature(
            service,
            BigInteger.ONE,
            BigInteger.ONE,
            new byte[] {0x00},
            uncompressedPublicKeyWithX(BigInteger.ONE));

    Assertions.assertThat(result).isEmpty();
  }

  @Test
  void tryCreateSignatureReturnsEmptyWhenComputedRIsZero() throws Exception {
    final var service = newService("");
    final var result =
        invokeTryCreateSignature(
            service,
            BigInteger.ONE,
            BigInteger.ONE,
            new byte[] {0x01},
            uncompressedPublicKeyWithX(BigInteger.ZERO));

    Assertions.assertThat(result).isEmpty();
  }

  @Test
  void tryCreateSignatureReturnsEmptyWhenComputedSIsZero() throws Exception {
    final var service = newService("");
    final var curveOrder =
        new BigInteger("A9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A7", 16);
    final var result =
        invokeTryCreateSignature(
            service,
            BigInteger.ONE,
            curveOrder.subtract(BigInteger.ONE),
            new byte[] {0x01},
            uncompressedPublicKeyWithX(BigInteger.ONE));

    Assertions.assertThat(result).isEmpty();
  }

  @Test
  void truncateDigestShiftsWhenInputBitLengthExceedsCurveOrder() throws Exception {
    final var value = new byte[64];
    Arrays.fill(value, (byte) 0xFF);

    final var truncated =
        (BigInteger)
            invokePrivateStatic("truncateDigest", new Class<?>[] {byte[].class}, (Object) value);

    final var curveOrder =
        new BigInteger("A9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A7", 16);
    Assertions.assertThat(truncated.bitLength()).isLessThanOrEqualTo(curveOrder.bitLength());
  }

  @Test
  void isValidScalarRejectsValuesOutsideCurveOrderRange() throws Exception {
    final var curveOrder =
        new BigInteger("A9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A7", 16);

    final var zeroResult =
        (boolean)
            invokePrivateStatic(
                "isValidScalar", new Class<?>[] {BigInteger.class}, BigInteger.ZERO);
    final var tooLargeResult =
        (boolean)
            invokePrivateStatic("isValidScalar", new Class<?>[] {BigInteger.class}, curveOrder);

    Assertions.assertThat(zeroResult).isFalse();
    Assertions.assertThat(tooLargeResult).isFalse();
  }

  private VirtualCardService newService(final String imageFile) {
    return new VirtualCardService(imageFile);
  }

  private VirtualCardService serviceWithCardData(
      final String cvCertificate,
      final String authCertificate,
      final String subCaCvCertificate,
      final String version2,
      final byte[] egkAuthCvcPrivateKey) {
    return new VirtualCardService(
        new VirtualCardImageData(
            cvCertificate, authCertificate, subCaCvCertificate, version2, egkAuthCvcPrivateKey),
        new ApduMatcher());
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

  private static byte[] uncompressedPublicKeyWithX(final BigInteger xCoordinate) {
    final byte[] publicKey = new byte[65];
    publicKey[0] = 0x04;
    final byte[] xBytes = VirtualCardPureHelper.toFixedLength(xCoordinate, 32);
    System.arraycopy(xBytes, 0, publicKey, 1, xBytes.length);
    return publicKey;
  }

  private static Object invokePrivate(
      final Object target,
      final String methodName,
      final Class<?>[] parameterTypes,
      final Object... args)
      throws Exception {
    final Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return method.invoke(target, args);
  }

  private static java.util.Optional<?> invokeTryCreateSignature(
      final VirtualCardService service,
      final BigInteger privateKey,
      final BigInteger z,
      final byte[] ephemeralPrivateKey,
      final byte[] ephemeralPublicKey)
      throws Exception {
    final Object result =
        invokePrivate(
            service,
            "tryCreateSignature",
            new Class<?>[] {BigInteger.class, BigInteger.class, byte[].class, byte[].class},
            privateKey,
            z,
            ephemeralPrivateKey,
            ephemeralPublicKey);
    return (java.util.Optional<?>) result;
  }

  private static Object invokePrivateStatic(
      final String methodName, final Class<?>[] parameterTypes, final Object... args)
      throws Exception {
    final Method method = VirtualCardService.class.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return method.invoke(null, args);
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
