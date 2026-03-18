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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.poppcommons.api.messages.ScenarioStep;
import de.gematik.smartcards.g2icc.cos.SecureMessagingConverterSoftware;
import de.gematik.smartcards.g2icc.cvc.Cvc;
import de.gematik.smartcards.g2icc.cvc.TrustCenter;
import de.gematik.smartcards.sdcom.apdu.CommandApdu;
import de.gematik.smartcards.sdcom.apdu.ResponseApdu;
import de.gematik.smartcards.utils.Hex;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class VirtualCardServiceTest {
  private static final Path REPO_ROOT =
      Path.of(System.getProperty("user.dir")).toAbsolutePath().getParent();
  private static final Path POPP_SERVER_RESOURCES =
      REPO_ROOT.resolve("popp-server/src/main/resources");

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
            "10 86 0000107c0ec30c000a8027600101169990210100",
            "00 86 0000    45"
                + " 7c43854104987fce93bfc191e4db006b56f8fd5f749d256fc5842f0f3f31becf613ce146f66318f77ff7ee51c10b6b6a0f349896400c7601bfc07608ff08fe0ce1d921ca42",
            "00 a4 040c   0a   a000000167455349474e",
            "00 b0 8400   00   0000");
  }

  @Test
  void isConfiguredTrue() {
    assertTrue(virtualCardService.isConfigured());
  }

  @Test
  void isConfiguredFalse() {
    virtualCardService.setCvcCertificate(null);
    virtualCardService.setAuthCertificate(null);
    assertFalse(virtualCardService.isConfigured());
  }

  @Test
  void processScenarioList() {
    ScenarioStep step1 = Mockito.mock(ScenarioStep.class);
    ScenarioStep step2 = Mockito.mock(ScenarioStep.class);
    ScenarioStep step3 = Mockito.mock(ScenarioStep.class);
    ScenarioStep step4 = Mockito.mock(ScenarioStep.class);

    when(step1.getCommandApdu()).thenReturn("00b0860000"); // read-end-entity-cv-certificate
    when(step2.getCommandApdu())
        .thenReturn("10860000107c0ec30c000a8027600101169990210100"); // mutual-authentication-step-1
    when(step3.getCommandApdu()).thenReturn("00b08400000000"); // read-ef-c-ch-aut-e256
    when(step4.getCommandApdu()).thenReturn("00a4040c07D2760001448000"); // select-master-file

    List<String> responses = virtualCardService.process(List.of(step1, step2, step3, step4));

    assertEquals(4, responses.size());

    assertEquals(
        virtualCardService.getCvcCertificate() + VirtualCardService.APDU_RESPONSE_OK,
        responses.get(0)); // read-end-entity-cv-certificate
    assertEquals(
        VirtualCardService.APDU_RESPONSE_OK,
        responses
            .get(1)
            .substring(responses.get(1).length() - VirtualCardService.APDU_RESPONSE_OK.length()));
    assertTrue(responses.get(1).startsWith("7C")); // mutual-authentication-step-1
    assertEquals(
        virtualCardService.getAuthCertificate() + VirtualCardService.APDU_RESPONSE_OK,
        responses.get(2)); // read-ef-c-ch-aut-e256
    assertEquals(VirtualCardService.APDU_RESPONSE_OK, responses.get(3)); // select-master-file

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

    service.setCvcCertificate("CVC_CERT");
    service.setAuthCertificate("AUTH_CERT");

    Assertions.assertThat(service.isConfigured()).isTrue();
    Assertions.assertThat(service.getCvcCertificate()).isEqualTo("CVC_CERT");
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
        .isEqualTo("CVC_CERT" + VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(1))
        .startsWith("7C")
        .endsWith(VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(2))
        .isEqualTo("AUTH_CERT" + VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(3))
        .isEqualTo(
            VirtualCardService.APDU_RESPONSE_READ_VERSION + VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(responses.get(4)).isEqualTo(VirtualCardService.APDU_RESPONSE_OK);
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
  void processReturnsProtectedResponseForSecureMessagingRead() throws Exception {
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
            "10 86 0000107c0ec30c000a8027600101169990210100",
            "00 86 0000    45"
                + " 7c43854104987fce93bfc191e4db006b56f8fd5f749d256fc5842f0f3f31becf613ce146f66318f77ff7ee51c10b6b6a0f349896400c7601bfc07608ff08fe0ce1d921ca42",
            "00 a4 040c   0a   a000000167455349474e",
            "00 b0 8400   00   0000");

    final var serverConverter = newServerSecureMessagingConverter();
    serverConverter.importCvc(new Cvc(HexFormat.of().parseHex(service.getCvcCertificate())));

    final var step1Response =
        service.process(
            List.of(
                new ScenarioStep(
                    "10 86 0000107c0ec30c000a8027600101169990210100", List.of("9000"))));
    final var generalAuthenticateStep2 =
        serverConverter.getGeneralAuthenticateStep2(new ResponseApdu(step1Response.getFirst()));

    final var handshakeResponses =
        service.process(
            List.of(
                new ScenarioStep(
                    Hex.toHexDigits(generalAuthenticateStep2.getBytes()), List.of("9000")),
                new ScenarioStep(
                    Hex.toHexDigits(
                        serverConverter
                            .secureCommand(new CommandApdu("00 A4 04 0C 0A A000000167455349474E"))
                            .getBytes()),
                    List.of("9000")),
                new ScenarioStep(
                    Hex.toHexDigits(
                        serverConverter
                            .secureCommand(new CommandApdu("00 B0 84 00 00"))
                            .getBytes()),
                    List.of("9000"))));

    Assertions.assertThat(handshakeResponses.get(0)).isEqualTo(VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(handshakeResponses.get(1)).endsWith(VirtualCardService.APDU_RESPONSE_OK);
    Assertions.assertThat(handshakeResponses.get(2)).startsWith("8182");

    final var unprotectedSelectResponse =
        serverConverter.unsecureResponse(new ResponseApdu(handshakeResponses.get(1)));
    Assertions.assertThat(Integer.toHexString(unprotectedSelectResponse.getSw())).isEqualTo("9000");

    final var unprotectedResponse =
        serverConverter.unsecureResponse(new ResponseApdu(handshakeResponses.get(2)));

    Assertions.assertThat(Hex.toHexDigits(unprotectedResponse.getData()))
        .isEqualToIgnoringCase(service.getAuthCertificate());
    Assertions.assertThat(Integer.toHexString(unprotectedResponse.getSw())).isEqualTo("9000");
  }

  private SecureMessagingConverterSoftware newServerSecureMessagingConverter() throws Exception {
    TrustCenter.initializeCache(POPP_SERVER_RESOURCES.resolve("identities/PKI_CVC.G2"));
    final var subCaCvc = loadSubCaCvc();
    final var endEntityCvc = loadPoppServiceEndEntityCvc();
    final var privateKeyBytes =
        Files.readAllBytes(
            POPP_SERVER_RESOURCES.resolve(
                "identities/PoPP26-Server/80276001011699902101-cvc-flag0/80276001011699902101-cvc-flag0.prv"));
    final var privateKey =
        (java.security.interfaces.ECPrivateKey)
            java.security.KeyFactory.getInstance("EC", "BC")
                .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes));
    return new SecureMessagingConverterSoftware(subCaCvc, endEntityCvc, privateKey);
  }

  private Cvc loadSubCaCvc() throws Exception {
    final String text =
        Files.readString(
            POPP_SERVER_RESOURCES.resolve(
                "identities/PKI_CVC.G2/trusted/DEGXX_8-7-02-22/DEGXX_1-2-02-23/DEGXX_1-2-02-23_CV-Certificate.txt"));
    final var start = text.indexOf('\'');
    final var end = text.indexOf('\'', start + 1);
    return new Cvc(HexFormat.of().parseHex(text.substring(start + 1, end)));
  }

  private Cvc loadPoppServiceEndEntityCvc() throws Exception {
    final String text =
        Files.readString(
            POPP_SERVER_RESOURCES.resolve(
                "identities/PKI_CVC.G2/trusted/DEGXX_8-7-02-22/DEGXX_1-2-02-23/000a_80-276-00101-1699902101/000a_80-276-00101-1699902101_CV-Certificate.txt"));
    final var start = text.indexOf('\'');
    final var end = text.indexOf('\'', start + 1);
    return new Cvc(HexFormat.of().parseHex(text.substring(start + 1, end)));
  }
}
