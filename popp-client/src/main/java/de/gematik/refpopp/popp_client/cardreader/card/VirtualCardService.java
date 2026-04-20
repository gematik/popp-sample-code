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

import de.gematik.openhealth.asn1.Asn1FfiException;
import de.gematik.openhealth.asn1.Asn1TagClass;
import de.gematik.openhealth.asn1.Asn1TagForm;
import de.gematik.openhealth.asn1.Openhealth_asn1Kt;
import de.gematik.openhealth.crypto.CryptoException;
import de.gematik.openhealth.crypto.Openhealth_cryptoKt;
import de.gematik.openhealth.healthcard.ApduException;
import de.gematik.openhealth.healthcard.CommandBuilderException;
import de.gematik.openhealth.healthcard.HealthCardCommand;
import de.gematik.poppcommons.api.messages.ScenarioStep;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Service
@Setter
@Slf4j
public class VirtualCardService {
  private static final String EGK_AUT_CVC_PRIVATE_KEY_OBJECT = "PrK.eGK.AUT_CVC.E256";
  private static final int SFI_VERSION = 0x11;
  private static final int SFI_SUB_CA_CV_CERTIFICATE = 0x07;
  private static final int SFI_END_ENTITY_CV_CERTIFICATE = 0x06;
  private static final int SFI_EF_C_CH_AUT_E256 = 0x04;
  private static final byte PRIVATE_KEY_REFERENCE = 0x09;
  private static final byte ALGORITHM_ID_TRUSTED_CHANNEL = 0x54;
  private static final byte ALGORITHM_ID_CONTACTLESS = 0x00;
  private static final byte[] DUMMY_KEY_REFERENCE =
      HexFormat.of().parseHex("000A80276001011699902101");
  private static final byte[] DUMMY_UNCOMPRESSED_PUBLIC_KEY =
      HexFormat.of()
          .parseHex(
              "0400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
  private static final byte[] SECURE_MESSAGING_SUFFIX_MAC = HexFormat.of().parseHex("00000002");
  private static final byte[] EGK_AID = HexFormat.of().parseHex("D2760001448000");
  private static final byte[] DF_ESIGN_AID = HexFormat.of().parseHex("A000000167455349474E");
  private static final byte[] POPP_SERVICE_END_ENTITY_PUBLIC_KEY =
      HexFormat.of()
          .parseHex(
              "048F3EED7A9475CCC776CDC2D748D4B1A217FED1335132572202FBFA5D1F12351B443B32408B22461F369620102BE4F922D1AC6B4D174AC12B0FB30276CBF0E24E");

  public static final String APDU_RESPONSE_OK = "9000";

  public static final String APDU_RESPONSE_READ_VERSION =
      "ef2bc003020000c103040502c210444549444d4548435f39303030030005c403010000c503020000c703010000";
  public static final String APDU_RESPONSE_READ_SUB_CA_CV_CERTIFICATE =
      "7f2181d87f4e81915f290170420844454758588702227f494d06082a8648ce3d04030286410428405a0ccc5c53b6780356a5141eb47fed5f56be44bc22f2046fc053fedbc25e50e24a6d6af95c1cfee9497acce359a253f7d0b7abaea5d1a62de030145f0c975f200844454758581102237f4c1306082a8214004c0481185307800000000000005f25060203000703015f24060301000703005f37404cd260c0803b125a001ba81ba9f2e2b1390de4f14691c822a28cc776a186d7ba7f08704c27fdcdaeb1f8b243a37976cf37bf7c121858d0f0419de83217a395de";
  public static final String APDU_RESPONSE_RETRIEVE_PUBLIC_KEY_IDENTIFIERS =
      "e0154f07d2760001448000b60a83084445475858870222e0154f07d2760001448000b60a83084445475858120223e0194f07d2760001448000a40e830c000a80276001011699902101e0194f07d2760001448000a40e830c4d6f7270686f414343455353e0164f07d2760001448000b60b83094d6f7270686f564552e0154f07d2760001448000b60a83084445475858860220e0154f07d2760001448000b60a83080000000000000013";
  private static final String APDU_SELECT_MASTER_FILE =
      buildApduHex(() -> HealthCardCommand.Companion.selectAid(EGK_AID), false);
  private static final String APDU_READ_VERSION =
      buildApduHex(
          () -> HealthCardCommand.Companion.readSfiWithOffsetAndLength(SFI_VERSION, 0, 256), false);
  private static final String APDU_READ_SUB_CA_CV_CERTIFICATE =
      buildApduHex(
          () ->
              HealthCardCommand.Companion.readSfiWithOffsetAndLength(
                  SFI_SUB_CA_CV_CERTIFICATE, 0, 256),
          false);
  private static final String APDU_READ_END_ENTITY_CV_CERTIFICATE_PREFIX =
      buildApduPrefix(
          () ->
              HealthCardCommand.Companion.readSfiWithOffsetAndLength(
                  SFI_END_ENTITY_CV_CERTIFICATE, 0, 256),
          false,
          8);
  private static final String APDU_RETRIEVE_PUBLIC_KEY_IDENTIFIERS =
      buildApduHex(HealthCardCommand.Companion::listPublicKeys, true);
  private static final String APDU_SELECT_PRIVATE_KEY_TRUSTED =
      buildApduHex(
          () ->
              HealthCardCommand.Companion.manageSecEnvSelectPrivateKey(
                  PRIVATE_KEY_REFERENCE, ALGORITHM_ID_TRUSTED_CHANNEL),
          false);
  private static final String APDU_SELECT_PRIVATE_KEY_CONTACTLESS =
      buildApduHex(
          () ->
              HealthCardCommand.Companion.manageSecEnvSelectPrivateKey(
                  PRIVATE_KEY_REFERENCE, ALGORITHM_ID_CONTACTLESS),
          false);
  private static final String APDU_MUTUAL_AUTHENTICATION_STEP_1_PREFIX =
      buildApduPrefix(
          () ->
              HealthCardCommand.Companion.generalAuthenticateMutualAuthenticationStep1(
                  DUMMY_KEY_REFERENCE),
          false,
          8);
  private static final String APDU_MUTUAL_AUTHENTICATION_STEP_2_PREFIX =
      buildApduPrefix(
          () ->
              HealthCardCommand.Companion.generalAuthenticateElcStep2(
                  DUMMY_UNCOMPRESSED_PUBLIC_KEY),
          false,
          8);
  private static final String APDU_SELECT_DF_ESIGN =
      buildApduHex(() -> HealthCardCommand.Companion.selectAid(DF_ESIGN_AID), false);
  private static final String APDU_READ_EF_C_CH_AUT_E256_PREFIX =
      buildApduPrefix(() -> HealthCardCommand.Companion.readSfi(SFI_EF_C_CH_AUT_E256), true, 8);
  private static final String APDU_SECURE_READ_EF_C_CH_AUT_E256_PREFIX = "0CB08400";
  private static final String APDU_SECURE_SELECT_DF_ESIGN_PREFIX = "0CA4040C";

  private String apduReadEndEntityCvCertificate;
  private String apduMutualAuthenticationStep1;
  private String apduReadEfCChAutE256;

  private final ApplicationEventPublisher eventPublisher;

  private static final byte[] EMPTY_BYTES = new byte[0];

  private HashMap<String, String> staticApduResponses = new HashMap<>();
  private String cvcCertificate = null;
  private String authCertificate = null;
  private byte[] egkAuthCvcPrivateKey = null;
  private byte[] poppServiceEndEntityPublicKey = null;
  private SecureMessagingSession secureMessagingSession = null;
  private byte[] pendingCardEphemeralPrivateKey = null;

  public VirtualCardService(
      ApplicationEventPublisher eventPublisher,
      @Value("${virtual-card.image-file:}") String imageFile,
      @Value("${command-apdus.select-master-file:}") String apduSelectMasterFile,
      @Value("${command-apdus.read-version:}") String apduReadVersion,
      @Value("${command-apdus.read-sub-ca-cv-certificate:}") String apduReadSubCaCvCertificate,
      @Value("${command-apdus.read-end-entity-cv-certificate:}")
          String apduReadEndEntityCvCertificate,
      @Value("${command-apdus.retrieve-public-key-identifiers:}")
          String apduRetrievePublicKeyIdentifiers,
      @Value("${command-apdus.select-private-key:}") String apduSelectPrivateKey,
      @Value("${command-apdus.mutual-authentication-step-1:}") String apduMutualAuthenticationStep1,
      @Value("${command-apdus.mutual-authentication-step-2:}") String apduMutualAuthenticationStep2,
      @Value("${command-apdus.select-df-esign:}") String apduSelectDfEsign,
      @Value("${command-apdus.read-ef-c-ch-aut-e256:}") String apduReadEfCChAutE256) {
    this.eventPublisher = eventPublisher;
    log.debug("| Entering VirtualCardService()");

    if (imageFile == null || imageFile.isEmpty()) {
      log.info("| No image file configured for virtual card.");
    } else {
      log.info("| Loading certificates from " + imageFile);
      try (InputStream is = openImageFile(imageFile)) {
        String xmlString = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        cvcCertificate = getCertificateData(xmlString, "EF.C.eGK.AUT_CVC.E256");
        log.info("| CVC certificate:  " + cvcCertificate);
        authCertificate = getCertificateData(xmlString, "EF.C.CH.AUT.E256");
        log.info("| X.509 certificate:  " + authCertificate);
        egkAuthCvcPrivateKey = loadEgkAuthCvcPrivateKey(xmlString);
      } catch (IOException | SAXException | ParserConfigurationException e) {
        throw new RuntimeException("Error when loading XML card image file", e);
      }
    }
    poppServiceEndEntityPublicKey = loadPoppServiceEndEntityPublicKey();

    this.apduReadEndEntityCvCertificate = normalize(apduReadEndEntityCvCertificate);
    this.apduMutualAuthenticationStep1 = normalize(apduMutualAuthenticationStep1);
    this.apduReadEfCChAutE256 = normalize(apduReadEfCChAutE256);

    registerStaticApduResponse(apduSelectMasterFile, "");
    registerStaticApduResponse(apduReadVersion, APDU_RESPONSE_READ_VERSION);
    registerStaticApduResponse(
        apduReadSubCaCvCertificate, APDU_RESPONSE_READ_SUB_CA_CV_CERTIFICATE);
    registerStaticApduResponse(
        apduRetrievePublicKeyIdentifiers, APDU_RESPONSE_RETRIEVE_PUBLIC_KEY_IDENTIFIERS);
    registerStaticApduResponse(apduSelectPrivateKey, "");
    registerStaticApduResponse(apduMutualAuthenticationStep2, "");
    registerStaticApduResponse(apduSelectDfEsign, "");

    log.debug("| Exiting VirtualCardService()");
  }

  public String getCvcCertificate() {
    return cvcCertificate;
  }

  public String getAuthCertificate() {
    return authCertificate;
  }

  public boolean isConfigured() {
    return cvcCertificate != null && authCertificate != null;
  }

  public List<String> process(final List<ScenarioStep> scenarioStep) {
    final var responses = new ArrayList<String>();
    for (final var step : scenarioStep) {
      responses.add(process(step));
    }

    return responses;
  }

  private static InputStream openImageFile(String imageFile) throws IOException {
    if (imageFile.startsWith("classpath:")) {
      String cp = imageFile.substring("classpath:".length());
      InputStream is = VirtualCardService.class.getClassLoader().getResourceAsStream(cp);
      if (is == null) {
        throw new FileNotFoundException("Classpath resource not found: " + cp);
      }
      return is;
    }
    Path p = Paths.get(imageFile);
    if (Files.exists(p)) {
      return Files.newInputStream(p);
    }
    InputStream is = VirtualCardService.class.getClassLoader().getResourceAsStream(imageFile);
    if (is == null) {
      throw new FileNotFoundException("File not found on filesystem or classpath: " + imageFile);
    }
    return is;
  }

  private String process(final ScenarioStep scenarioStep) {
    final var normalizedCommandApdu = normalize(scenarioStep.getCommandApdu());
    log.info("| APDU command: {}", normalizedCommandApdu);

    String responseAPDU;
    String statusWord = APDU_RESPONSE_OK;
    if (isMutualAuthenticationStep1(normalizedCommandApdu)) {
      secureMessagingSession = null;
      responseAPDU = buildEphemeralPublicKeyResponse();
    } else if (isMutualAuthenticationStep2(normalizedCommandApdu)) {
      initializeSecureMessagingSession(normalizedCommandApdu);
      responseAPDU = "";
    } else if (isReadEndEntityCvCertificate(normalizedCommandApdu)) { // READ CVC certificate
      responseAPDU = cvcCertificate;
    } else if (isReadEfCChAutE256(normalizedCommandApdu)) { // READ X.509 certificate
      responseAPDU = authCertificate;
    } else {
      responseAPDU = resolveStaticResponse(normalizedCommandApdu);
    }
    if (isSecureMessagingProtectedCommand(normalizedCommandApdu)
        && secureMessagingSession != null) {
      responseAPDU = secureMessagingSession.protectResponse(responseAPDU, statusWord);
    } else {
      responseAPDU = responseAPDU + statusWord;
    }
    log.info("| APDU response: {}", responseAPDU);
    return responseAPDU;
  }

  private String normalize(final String s) {
    if (s == null || s.isBlank()) {
      return "";
    }
    return s.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
  }

  private void registerStaticApduResponse(final String commandApdu, final String responseApdu) {
    final var normalizedCommandApdu = normalize(commandApdu);
    if (!normalizedCommandApdu.isEmpty()) {
      staticApduResponses.put(normalizedCommandApdu, responseApdu);
    }
  }

  private String resolveStaticResponse(final String normalizedCommandApdu) {
    final var configuredResponse = staticApduResponses.get(normalizedCommandApdu);
    if (configuredResponse != null) {
      return configuredResponse;
    }
    if (normalizedCommandApdu.equals(APDU_SELECT_MASTER_FILE)
        || normalizedCommandApdu.equals(APDU_SELECT_PRIVATE_KEY_TRUSTED)
        || normalizedCommandApdu.equals(APDU_SELECT_PRIVATE_KEY_CONTACTLESS)
        || normalizedCommandApdu.equals(APDU_SELECT_DF_ESIGN)) {
      return "";
    }
    if (normalizedCommandApdu.equals(APDU_READ_VERSION)) {
      return APDU_RESPONSE_READ_VERSION;
    }
    if (normalizedCommandApdu.equals(APDU_READ_SUB_CA_CV_CERTIFICATE)) {
      return APDU_RESPONSE_READ_SUB_CA_CV_CERTIFICATE;
    }
    if (normalizedCommandApdu.equals(APDU_RETRIEVE_PUBLIC_KEY_IDENTIFIERS)) {
      return APDU_RESPONSE_RETRIEVE_PUBLIC_KEY_IDENTIFIERS;
    }
    return "";
  }

  private static String buildApduHex(
      final HealthCardCommandSupplier supplier, final boolean supportsExtendedLength) {
    try (final var command = supplier.get();
        final var commandApdu = command.toApdu(supportsExtendedLength);
        final var bytes = commandApdu.toVec()) {
      return HexFormat.of().withUpperCase().formatHex(bytes.cloneAsNonzeroizingVec());
    } catch (final ApduException | CommandBuilderException e) {
      throw new IllegalStateException("Failed to build static virtual-card APDU", e);
    }
  }

  private static String buildApduPrefix(
      final HealthCardCommandSupplier supplier,
      final boolean supportsExtendedLength,
      final int prefixLength) {
    return buildApduHex(supplier, supportsExtendedLength).substring(0, prefixLength);
  }

  private boolean isMutualAuthenticationStep1(final String normalizedCommandApdu) {
    return normalizedCommandApdu.equals(apduMutualAuthenticationStep1)
        || normalizedCommandApdu.startsWith(APDU_MUTUAL_AUTHENTICATION_STEP_1_PREFIX);
  }

  private boolean isMutualAuthenticationStep2(final String normalizedCommandApdu) {
    return normalizedCommandApdu.equals(APDU_MUTUAL_AUTHENTICATION_STEP_2_PREFIX)
        || normalizedCommandApdu.startsWith(APDU_MUTUAL_AUTHENTICATION_STEP_2_PREFIX);
  }

  private boolean isReadEndEntityCvCertificate(final String normalizedCommandApdu) {
    return normalizedCommandApdu.equals(apduReadEndEntityCvCertificate)
        || normalizedCommandApdu.startsWith(APDU_READ_END_ENTITY_CV_CERTIFICATE_PREFIX);
  }

  private boolean isReadEfCChAutE256(final String normalizedCommandApdu) {
    return normalizedCommandApdu.equals(apduReadEfCChAutE256)
        || normalizedCommandApdu.startsWith(APDU_READ_EF_C_CH_AUT_E256_PREFIX)
        || normalizedCommandApdu.startsWith(APDU_SECURE_READ_EF_C_CH_AUT_E256_PREFIX);
  }

  private boolean isSecureMessagingProtectedCommand(final String normalizedCommandApdu) {
    return normalizedCommandApdu.startsWith(APDU_SECURE_SELECT_DF_ESIGN_PREFIX)
        || normalizedCommandApdu.startsWith(APDU_SECURE_READ_EF_C_CH_AUT_E256_PREFIX);
  }

  private void initializeSecureMessagingSession(final String normalizedCommandApdu) {
    if (egkAuthCvcPrivateKey == null
        || poppServiceEndEntityPublicKey == null
        || pendingCardEphemeralPrivateKey == null) {
      log.warn("| Secure messaging session could not be initialized.");
      secureMessagingSession = null;
      return;
    }

    try {
      final var commandApdu =
          new javax.smartcardio.CommandAPDU(HexFormat.of().parseHex(normalizedCommandApdu));
      final byte[] dynamicAuthenticationData =
          Openhealth_asn1Kt.readTaggedObjectValue(
              commandApdu.getData(), Asn1TagClass.APPLICATION, Asn1TagForm.CONSTRUCTED, 0x1C);
      final byte[] serverEphemeralPublicKey =
          Openhealth_asn1Kt.readTaggedObjectValue(
              dynamicAuthenticationData,
              Asn1TagClass.CONTEXT_SPECIFIC,
              Asn1TagForm.PRIMITIVE,
              0x05);

      final byte[] sharedSecretStatic =
          Openhealth_cryptoKt.brainpoolP256r1Ecdh(egkAuthCvcPrivateKey, serverEphemeralPublicKey);
      final byte[] sharedSecretEphemeral =
          Openhealth_cryptoKt.brainpoolP256r1Ecdh(
              pendingCardEphemeralPrivateKey, poppServiceEndEntityPublicKey);
      final byte[] sharedSecret = concatenate(sharedSecretStatic, sharedSecretEphemeral);
      secureMessagingSession = SecureMessagingSession.fromSharedSecret(sharedSecret);
    } catch (Asn1FfiException
        | CryptoException
        | IllegalArgumentException
        | NoSuchAlgorithmException e) {
      log.warn("| Failed to initialize secure messaging session.", e);
      secureMessagingSession = null;
    } finally {
      pendingCardEphemeralPrivateKey = null;
    }
  }

  private String buildEphemeralPublicKeyResponse() {
    try (final var keyPair = Openhealth_cryptoKt.generateBrainpoolP256r1KeyPair()) {
      pendingCardEphemeralPrivateKey = keyPair.privateKey();
      return toHex(
          Openhealth_asn1Kt.writeTaggedObject(
              Asn1TagClass.APPLICATION,
              Asn1TagForm.CONSTRUCTED,
              0x1C,
              Openhealth_asn1Kt.writeTaggedObject(
                  Asn1TagClass.CONTEXT_SPECIFIC,
                  Asn1TagForm.PRIMITIVE,
                  0x05,
                  keyPair.publicKey())));
    } catch (Asn1FfiException | CryptoException e) {
      throw new IllegalStateException("Failed to generate ephemeral EC key", e);
    }
  }

  private byte[] toFixedLength(byte[] value, int length) {
    if (value.length == length) {
      return value;
    }
    final byte[] result = new byte[length];
    if (value.length > length) {
      System.arraycopy(value, value.length - length, result, 0, length);
    } else {
      System.arraycopy(value, 0, result, length - value.length, value.length);
    }
    return result;
  }

  private String toHex(byte[] value) {
    return HexFormat.of().withUpperCase().formatHex(value);
  }

  private static byte[] concatenate(byte[]... values) {
    int totalLength = 0;
    for (byte[] value : values) {
      totalLength += value.length;
    }

    final byte[] result = new byte[totalLength];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private Element getDOMRootElement(String xmlDoc)
      throws IOException, SAXException, ParserConfigurationException {
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    dbFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document doc = dBuilder.parse(new InputSource(new StringReader(xmlDoc)));
    doc.getDocumentElement().normalize();
    return doc.getDocumentElement();
  }

  private String getCertificateData(String xmlDoc, String certName)
      throws IOException, SAXException, ParserConfigurationException {
    Element e = getDOMRootElement(xmlDoc);
    NodeList nList = e.getElementsByTagName("child");
    for (int i = 0; i < nList.getLength(); i++) {
      Element e2 = (Element) nList.item(i);
      if (certName.equals(e2.getAttribute("id"))) {
        NodeList nList2 = e2.getElementsByTagName("attribute");

        for (int j = 0; j < nList2.getLength(); j++) {
          Element e3 = (Element) nList2.item(j);
          if ("body".equals(e3.getAttribute("id"))) {
            return e3.getTextContent();
          }
        }
      }
    }

    return null;
  }

  private byte[] loadEgkAuthCvcPrivateKey(String xmlDoc)
      throws IOException, SAXException, ParserConfigurationException {
    final String privateKeyHex =
        getChildAttribute(xmlDoc, EGK_AUT_CVC_PRIVATE_KEY_OBJECT, "privateKey");
    if (privateKeyHex == null) {
      return null;
    }
    return toFixedLength(HexFormat.of().parseHex(privateKeyHex), 32);
  }

  private byte[] loadPoppServiceEndEntityPublicKey() {
    if (egkAuthCvcPrivateKey == null) {
      return null;
    }
    return Arrays.copyOf(
        POPP_SERVICE_END_ENTITY_PUBLIC_KEY, POPP_SERVICE_END_ENTITY_PUBLIC_KEY.length);
  }

  private String getChildAttribute(String xmlDoc, String childId, String attributeId)
      throws IOException, SAXException, ParserConfigurationException {
    Element e = getDOMRootElement(xmlDoc);
    NodeList nList = e.getElementsByTagName("child");
    for (int i = 0; i < nList.getLength(); i++) {
      Element child = (Element) nList.item(i);
      if (!childId.equals(child.getAttribute("id"))) {
        continue;
      }
      NodeList attributes = child.getElementsByTagName("attribute");
      for (int j = 0; j < attributes.getLength(); j++) {
        Element attribute = (Element) attributes.item(j);
        if (attributeId.equals(attribute.getAttribute("id"))) {
          return attribute.getTextContent();
        }
      }
    }
    return null;
  }

  private static final class SecureMessagingSession {
    private final byte[] kmac;
    private final byte[] sscMacRsp = new byte[16];

    private SecureMessagingSession(byte[] kmac) {
      this.kmac = kmac;
    }

    private static SecureMessagingSession fromSharedSecret(final byte[] sharedSecret)
        throws NoSuchAlgorithmException {
      return new SecureMessagingSession(deriveSecureMessagingMacKey(sharedSecret));
    }

    private String protectResponse(String responseDataHex, String statusWordHex) {
      try {
        final byte[] responseData =
            responseDataHex == null || responseDataHex.isEmpty()
                ? EMPTY_BYTES
                : HexFormat.of().parseHex(responseDataHex);
        final byte[] statusWord = HexFormat.of().parseHex(statusWordHex);

        incrementCounter(sscMacRsp);
        incrementCounter(sscMacRsp);

        final byte[] responseDataDo =
            responseData.length == 0
                ? EMPTY_BYTES
                : Openhealth_asn1Kt.writeTaggedObject(
                    Asn1TagClass.CONTEXT_SPECIFIC, Asn1TagForm.PRIMITIVE, 0x01, responseData);
        final byte[] statusWordDo =
            Openhealth_asn1Kt.writeTaggedObject(
                Asn1TagClass.CONTEXT_SPECIFIC, Asn1TagForm.PRIMITIVE, 0x19, statusWord);
        final byte[] macInput = concatenate(responseDataDo, statusWordDo);
        final byte[] mac =
            Openhealth_cryptoKt.aesCmac(kmac, concatenate(sscMacRsp, padIso(macInput)), 8);
        final byte[] macDo =
            Openhealth_asn1Kt.writeTaggedObject(
                Asn1TagClass.CONTEXT_SPECIFIC, Asn1TagForm.PRIMITIVE, 0x0E, mac);

        return HexFormat.of().formatHex(concatenate(responseDataDo, statusWordDo, macDo))
            + statusWordHex;
      } catch (Asn1FfiException | CryptoException e) {
        throw new IllegalStateException("Failed to protect response", e);
      }
    }

    @SuppressWarnings("java:S4790")
    private static byte[] deriveSecureMessagingMacKey(final byte[] sharedSecret)
        throws NoSuchAlgorithmException {
      // This KDF must stay byte-compatible with the existing virtual-card secure-messaging flow.
      // The previous implementation used EafiHashAlgorithm.SHA_1 over sharedSecret || 00000002 and
      // truncated the result to 16 bytes for the AES-CMAC key.
      return Arrays.copyOf(
          MessageDigest.getInstance("SHA-1")
              .digest(concatenate(sharedSecret, SECURE_MESSAGING_SUFFIX_MAC)),
          16);
    }

    private static void incrementCounter(byte[] counter) {
      for (int i = counter.length - 1; i >= 0; i--) {
        counter[i]++;
        if (counter[i] != 0) {
          return;
        }
      }
    }

    private static byte[] padIso(byte[] value) {
      final int paddedLength = ((value.length + 1 + 15) / 16) * 16;
      final byte[] padded = Arrays.copyOf(value, paddedLength);
      padded[value.length] = (byte) 0x80;
      return padded;
    }
  }

  @FunctionalInterface
  private interface HealthCardCommandSupplier {
    HealthCardCommand get() throws CommandBuilderException;
  }
}
