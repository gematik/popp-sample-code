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

import de.gematik.poppcommons.api.messages.ScenarioStep;
import de.gematik.smartcards.crypto.AesKey;
import de.gematik.smartcards.crypto.AfiElcUtils;
import de.gematik.smartcards.crypto.EcPrivateKeyImpl;
import de.gematik.smartcards.crypto.EcPublicKeyImpl;
import de.gematik.smartcards.tlv.BerTlv;
import de.gematik.smartcards.tlv.ConstructedBerTlv;
import de.gematik.smartcards.utils.AfiUtils;
import de.gematik.smartcards.utils.EafiHashAlgorithm;
import de.gematik.smartcards.utils.Hex;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
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
  private static final byte[] SECURE_MESSAGING_SUFFIX_MAC = Hex.toByteArray("00000002");
  private static final byte[] POPP_SERVICE_END_ENTITY_PUBLIC_KEY =
      Hex.toByteArray(
          "048F3EED7A9475CCC776CDC2D748D4B1A217FED1335132572202FBFA5D1F12351B443B32408B22461F369620102BE4F922D1AC6B4D174AC12B0FB30276CBF0E24E");

  static {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  public static final String APDU_RESPONSE_OK = "9000";

  public static final String APDU_RESPONSE_READ_VERSION =
      "ef2bc003020000c103040502c210444549444d4548435f39303030030005c403010000c503020000c703010000";
  public static final String APDU_RESPONSE_READ_SUB_CA_CV_CERTIFICATE =
      "7f2181d87f4e81915f290170420844454758588702227f494d06082a8648ce3d04030286410428405a0ccc5c53b6780356a5141eb47fed5f56be44bc22f2046fc053fedbc25e50e24a6d6af95c1cfee9497acce359a253f7d0b7abaea5d1a62de030145f0c975f200844454758581102237f4c1306082a8214004c0481185307800000000000005f25060203000703015f24060301000703005f37404cd260c0803b125a001ba81ba9f2e2b1390de4f14691c822a28cc776a186d7ba7f08704c27fdcdaeb1f8b243a37976cf37bf7c121858d0f0419de83217a395de";
  public static final String APDU_RESPONSE_RETRIEVE_PUBLIC_KEY_IDENTIFIERS =
      "e0154f07d2760001448000b60a83084445475858870222e0154f07d2760001448000b60a83084445475858120223e0194f07d2760001448000a40e830c000a80276001011699902101e0194f07d2760001448000a40e830c4d6f7270686f414343455353e0164f07d2760001448000b60b83094d6f7270686f564552e0154f07d2760001448000b60a83084445475858860220e0154f07d2760001448000b60a83080000000000000013";

  private String apduReadEndEntityCvCertificate;
  private String apduMutualAuthenticationStep1;
  private String apduReadEfCChAutE256;

  private final ApplicationEventPublisher eventPublisher;

  private HashMap<String, String> staticApduResponses = new HashMap<>();
  private String cvcCertificate = null;
  private String authCertificate = null;
  private EcPrivateKeyImpl egkAuthCvcPrivateKey = null;
  private EcPublicKeyImpl poppServiceEndEntityPublicKey = null;
  private SecureMessagingSession secureMessagingSession = null;
  private EcPrivateKeyImpl pendingCardEphemeralPrivateKey = null;
  private final SecureRandom secureRandom = new SecureRandom();

  public VirtualCardService(
      ApplicationEventPublisher eventPublisher,
      @Value("${virtual-card.image-file}") String imageFile,
      @Value("${command-apdus.select-master-file}") String apduSelectMasterFile,
      @Value("${command-apdus.read-version}") String apduReadVersion,
      @Value("${command-apdus.read-sub-ca-cv-certificate}") String apduReadSubCaCvCertificate,
      @Value("${command-apdus.read-end-entity-cv-certificate}")
          String apduReadEndEntityCvCertificate,
      @Value("${command-apdus.retrieve-public-key-identifiers}")
          String apduRetrievePublicKeyIdentifiers,
      @Value("${command-apdus.select-private-key}") String apduSelectPrivateKey,
      @Value("${command-apdus.mutual-authentication-step-1}") String apduMutualAuthenticationStep1,
      @Value("${command-apdus.mutual-authentication-step-2}") String apduMutualAuthenticationStep2,
      @Value("${command-apdus.select-df-esign}") String apduSelectDfEsign,
      @Value("${command-apdus.read-ef-c-ch-aut-e256}") String apduReadEfCChAutE256) {
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
      } catch (IOException
          | SAXException
          | ParserConfigurationException
          | GeneralSecurityException e) {
        throw new RuntimeException("Error when loading XML card image file", e);
      }
    }
    poppServiceEndEntityPublicKey = loadPoppServiceEndEntityPublicKey();

    this.apduReadEndEntityCvCertificate = normalize(apduReadEndEntityCvCertificate);
    this.apduMutualAuthenticationStep1 = normalize(apduMutualAuthenticationStep1);
    this.apduReadEfCChAutE256 = normalize(apduReadEfCChAutE256);

    staticApduResponses.put(normalize(apduSelectMasterFile), "");
    staticApduResponses.put(normalize(apduReadVersion), APDU_RESPONSE_READ_VERSION);
    staticApduResponses.put(
        normalize(apduReadSubCaCvCertificate), APDU_RESPONSE_READ_SUB_CA_CV_CERTIFICATE);
    staticApduResponses.put(
        normalize(apduRetrievePublicKeyIdentifiers), APDU_RESPONSE_RETRIEVE_PUBLIC_KEY_IDENTIFIERS);
    staticApduResponses.put(normalize(apduSelectPrivateKey), "");
    staticApduResponses.put(normalize(apduMutualAuthenticationStep2), "");
    staticApduResponses.put(normalize(apduSelectDfEsign), "");

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
    if (normalizedCommandApdu.equals(apduMutualAuthenticationStep1)) {
      secureMessagingSession = null;
      responseAPDU = buildEphemeralPublicKeyResponse();
    } else if (isMutualAuthenticationStep2(normalizedCommandApdu)) {
      initializeSecureMessagingSession(normalizedCommandApdu);
      responseAPDU = "";
    } else if (normalizedCommandApdu.equals(
        apduReadEndEntityCvCertificate)) { // READ CVC certificate
      responseAPDU = cvcCertificate;
    } else if (isReadEfCChAutE256(normalizedCommandApdu)) { // READ X.509 certificate
      responseAPDU = authCertificate;
    } else {
      responseAPDU = staticApduResponses.getOrDefault(normalizedCommandApdu, "");
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
    return s.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
  }

  private boolean isMutualAuthenticationStep2(final String normalizedCommandApdu) {
    return normalizedCommandApdu.equals(normalize("00 86 0000"))
        || normalizedCommandApdu.startsWith(normalize("00 86 0000"));
  }

  private boolean isReadEfCChAutE256(final String normalizedCommandApdu) {
    return normalizedCommandApdu.equals(apduReadEfCChAutE256)
        || normalizedCommandApdu.startsWith(normalize("0C B0 84 00"));
  }

  private boolean isSecureMessagingProtectedCommand(final String normalizedCommandApdu) {
    return normalizedCommandApdu.startsWith(normalize("0C A4 04 0C"))
        || normalizedCommandApdu.startsWith(normalize("0C B0 84 00"));
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
      final var dataTlv = (ConstructedBerTlv) BerTlv.getInstance(commandApdu.getData());
      final var ephemeralPublicKeyData =
          dataTlv
              .getPrimitive(0x85L)
              .orElseThrow(() -> new IllegalArgumentException("Missing ephemeral public key"))
              .getValueField();
      final var serverEphemeralPublicKey =
          new EcPublicKeyImpl(
              AfiElcUtils.os2p(ephemeralPublicKeyData, egkAuthCvcPrivateKey.getParams()),
              egkAuthCvcPrivateKey.getParams());

      final byte[] sharedSecretStatic =
          AfiElcUtils.sharedSecret(egkAuthCvcPrivateKey, serverEphemeralPublicKey);
      final byte[] sharedSecretEphemeral =
          AfiElcUtils.sharedSecret(pendingCardEphemeralPrivateKey, poppServiceEndEntityPublicKey);
      final byte[] sharedSecret = AfiUtils.concatenate(sharedSecretStatic, sharedSecretEphemeral);

      final var kmac =
          new AesKey(
              EafiHashAlgorithm.SHA_1.digest(
                  AfiUtils.concatenate(sharedSecret, SECURE_MESSAGING_SUFFIX_MAC)),
              0,
              16);
      secureMessagingSession = new SecureMessagingSession(kmac);
    } catch (RuntimeException e) {
      log.warn("| Failed to initialize secure messaging session.", e);
      secureMessagingSession = null;
    } finally {
      pendingCardEphemeralPrivateKey = null;
    }
  }

  private String buildEphemeralPublicKeyResponse() {
    try {
      final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", "BC");
      keyPairGenerator.initialize(new ECGenParameterSpec("brainpoolP256r1"), secureRandom);
      final KeyPair keyPair = keyPairGenerator.generateKeyPair();
      pendingCardEphemeralPrivateKey =
          new EcPrivateKeyImpl((java.security.interfaces.ECPrivateKey) keyPair.getPrivate());
      final ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();

      final byte[] x = toFixedLength(publicKey.getW().getAffineX().toByteArray(), 32);
      final byte[] y = toFixedLength(publicKey.getW().getAffineY().toByteArray(), 32);

      final byte[] uncompressed = new byte[1 + x.length + y.length];
      uncompressed[0] = 0x04;
      System.arraycopy(x, 0, uncompressed, 1, x.length);
      System.arraycopy(y, 0, uncompressed, 1 + x.length, y.length);

      final byte[] inner = new byte[2 + uncompressed.length];
      inner[0] = (byte) 0x85;
      inner[1] = (byte) uncompressed.length;
      System.arraycopy(uncompressed, 0, inner, 2, uncompressed.length);
      final byte[] responseData = new byte[2 + inner.length];
      responseData[0] = 0x7C;
      responseData[1] = (byte) inner.length;
      System.arraycopy(inner, 0, responseData, 2, inner.length);

      return toHex(responseData);
    } catch (GeneralSecurityException e) {
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
    final StringBuilder sb = new StringBuilder(value.length * 2);
    for (byte b : value) {
      sb.append(String.format("%02X", b));
    }
    return sb.toString();
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

  private EcPrivateKeyImpl loadEgkAuthCvcPrivateKey(String xmlDoc)
      throws IOException, SAXException, ParserConfigurationException, GeneralSecurityException {
    final String privateKeyHex =
        getChildAttribute(xmlDoc, EGK_AUT_CVC_PRIVATE_KEY_OBJECT, "privateKey");
    if (privateKeyHex == null) {
      return null;
    }
    final AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC", "BC");
    parameters.init(new ECGenParameterSpec("brainpoolP256r1"));
    return new EcPrivateKeyImpl(
        new java.math.BigInteger(1, HexFormat.of().parseHex(privateKeyHex)),
        parameters.getParameterSpec(java.security.spec.ECParameterSpec.class));
  }

  private EcPublicKeyImpl loadPoppServiceEndEntityPublicKey() {
    if (egkAuthCvcPrivateKey == null) {
      return null;
    }
    try {
      return new EcPublicKeyImpl(
          AfiElcUtils.os2p(POPP_SERVICE_END_ENTITY_PUBLIC_KEY, egkAuthCvcPrivateKey.getParams()),
          egkAuthCvcPrivateKey.getParams());
    } catch (RuntimeException e) {
      log.warn("| Failed to load PoPP service end-entity public key.", e);
      return null;
    }
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
    private final AesKey kmac;
    private final byte[] sscMacRsp = new byte[16];

    private SecureMessagingSession(AesKey kmac) {
      this.kmac = kmac;
    }

    private String protectResponse(String responseDataHex, String statusWordHex) {
      final byte[] responseData =
          responseDataHex == null || responseDataHex.isEmpty()
              ? AfiUtils.EMPTY_OS
              : HexFormat.of().parseHex(responseDataHex);
      final byte[] statusWord = HexFormat.of().parseHex(statusWordHex);

      AfiUtils.incrementCounter(sscMacRsp);
      AfiUtils.incrementCounter(sscMacRsp);

      final byte[] responseDataDo =
          responseData.length == 0
              ? AfiUtils.EMPTY_OS
              : BerTlv.getInstance(0x81L, responseData).getEncoded();
      final byte[] statusWordDo = BerTlv.getInstance(0x99L, statusWord).getEncoded();
      final byte[] macInput = AfiUtils.concatenate(responseDataDo, statusWordDo);
      final byte[] mac =
          kmac.calculateCmac(AfiUtils.concatenate(sscMacRsp, kmac.padIso(macInput)), 8);
      final byte[] macDo = BerTlv.getInstance(0x8EL, mac).getEncoded();

      return Hex.toHexDigits(AfiUtils.concatenate(responseDataDo, statusWordDo, macDo))
          + statusWordHex;
    }
  }
}
