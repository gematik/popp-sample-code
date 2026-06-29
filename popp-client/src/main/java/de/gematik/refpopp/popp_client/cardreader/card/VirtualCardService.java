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
import de.gematik.openhealth.healthcard.HealthCardCommand;
import de.gematik.poppcommons.api.messages.ScenarioStep;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import javax.smartcardio.CommandAPDU;
import javax.xml.parsers.ParserConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

@Service
@Slf4j
public class VirtualCardService {
  private static final VirtualCardImageData EMPTY_CARD_DATA =
      new VirtualCardImageData(null, null, null, null, null);
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
  private static final byte[] EGK_AID = HexFormat.of().parseHex("D2760001448000");
  private static final byte[] DF_ESIGN_AID = HexFormat.of().parseHex("A000000167455349474E");
  private static final byte[] POPP_SERVICE_END_ENTITY_PUBLIC_KEY =
      HexFormat.of()
          .parseHex(
              "048F3EED7A9475CCC776CDC2D748D4B1A217FED1335132572202FBFA5D1F12351B443B32408B22461F369620102BE4F922D1AC6B4D174AC12B0FB30276CBF0E24E");
  private static final BigInteger BRAINPOOL_P256R1_ORDER =
      new BigInteger("A9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A7", 16);
  private static final int BRAINPOOL_P256R1_COORDINATE_LENGTH = 32;
  private static final int MAX_ECDSA_SIGNING_ATTEMPTS = 64;

  public static final String APDU_RESPONSE_OK = "9000";

  public static final String APDU_RESPONSE_RETRIEVE_PUBLIC_KEY_IDENTIFIERS =
      "e0154f07d2760001448000b60a83084445475858870222e0154f07d2760001448000b60a83084445475858120223e0194f07d2760001448000a40e830c000a80276001011699902101e0194f07d2760001448000a40e830c4d6f7270686f414343455353e0164f07d2760001448000b60b83094d6f7270686f564552e0154f07d2760001448000b60a83084445475858860220e0154f07d2760001448000b60a83080000000000000013";
  private static final String APDU_SELECT_MASTER_FILE =
      VirtualCardApduHelper.buildApduHex(
          () -> HealthCardCommand.Companion.selectAid(EGK_AID), false);
  private static final String APDU_READ_VERSION =
      VirtualCardApduHelper.buildApduHex(
          () -> HealthCardCommand.Companion.readSfiWithOffsetAndLength(SFI_VERSION, 0, 256), false);
  private static final String APDU_READ_SUB_CA_CV_CERTIFICATE =
      VirtualCardApduHelper.buildApduHex(
          () ->
              HealthCardCommand.Companion.readSfiWithOffsetAndLength(
                  SFI_SUB_CA_CV_CERTIFICATE, 0, 256),
          false);
  private static final String APDU_READ_END_ENTITY_CV_CERTIFICATE_PREFIX =
      VirtualCardApduHelper.buildApduPrefix(
          () ->
              HealthCardCommand.Companion.readSfiWithOffsetAndLength(
                  SFI_END_ENTITY_CV_CERTIFICATE, 0, 256),
          false);
  private static final String APDU_RETRIEVE_PUBLIC_KEY_IDENTIFIERS =
      VirtualCardApduHelper.buildApduHex(HealthCardCommand.Companion::listPublicKeys, true);
  private static final String APDU_SELECT_PRIVATE_KEY_TRUSTED =
      VirtualCardApduHelper.buildApduHex(
          () ->
              HealthCardCommand.Companion.manageSecEnvSelectPrivateKey(
                  PRIVATE_KEY_REFERENCE, ALGORITHM_ID_TRUSTED_CHANNEL),
          false);
  private static final String APDU_SELECT_PRIVATE_KEY_CONTACTLESS =
      VirtualCardApduHelper.buildApduHex(
          () ->
              HealthCardCommand.Companion.manageSecEnvSelectPrivateKey(
                  PRIVATE_KEY_REFERENCE, ALGORITHM_ID_CONTACTLESS),
          false);
  private static final String APDU_MUTUAL_AUTHENTICATION_STEP_1_PREFIX =
      VirtualCardApduHelper.buildApduPrefix(
          () ->
              HealthCardCommand.Companion.generalAuthenticateMutualAuthenticationStep1(
                  DUMMY_KEY_REFERENCE),
          false);
  private static final String APDU_MUTUAL_AUTHENTICATION_STEP_2_PREFIX =
      VirtualCardApduHelper.buildApduPrefix(
          () ->
              HealthCardCommand.Companion.generalAuthenticateElcStep2(
                  DUMMY_UNCOMPRESSED_PUBLIC_KEY),
          false);
  private static final String APDU_SELECT_DF_ESIGN =
      VirtualCardApduHelper.buildApduHex(
          () -> HealthCardCommand.Companion.selectAid(DF_ESIGN_AID), false);
  private static final String APDU_READ_EF_C_CH_AUT_E256_PREFIX =
      VirtualCardApduHelper.buildApduPrefix(
          () -> HealthCardCommand.Companion.readSfi(SFI_EF_C_CH_AUT_E256), true);
  private static final String APDU_SECURE_READ_EF_C_CH_AUT_E256_PREFIX = "0CB08400";
  private static final String APDU_SECURE_SELECT_DF_ESIGN_PREFIX = "0CA4040C";
  private static final String APDU_INTERNAL_AUTHENTICATE_PREFIX =
      VirtualCardApduHelper.buildApduPrefix(
          () -> HealthCardCommand.Companion.internalAuthenticate(new byte[24]), false);

  private final VirtualCardImageData cardData;
  private final byte[] poppServiceEndEntityPublicKey;
  private final ApduMatcher apduMatcher;

  @Autowired
  public VirtualCardService(
      @Value("${virtual-card.image-file:}") String imageFile,
      final VirtualCardImageLoader imageLoader,
      final ApduMatcher apduMatcher) {
    this.cardData = loadCardData(imageFile, imageLoader);
    this.poppServiceEndEntityPublicKey = loadPoppServiceEndEntityPublicKey(this.cardData);
    this.apduMatcher = apduMatcher;
  }

  VirtualCardService(final String imageFile) {
    this(imageFile, new VirtualCardImageLoader(), new ApduMatcher());
  }

  VirtualCardService(final VirtualCardImageData cardData, final ApduMatcher apduMatcher) {
    this.cardData = cardData == null ? EMPTY_CARD_DATA : cardData;
    this.poppServiceEndEntityPublicKey = loadPoppServiceEndEntityPublicKey(this.cardData);
    this.apduMatcher = apduMatcher;
  }

  private static VirtualCardImageData loadCardData(
      final String imageFile, final VirtualCardImageLoader imageLoader) {
    if (imageFile == null || imageFile.isEmpty()) {
      log.info("| No image file configured for virtual card.");
      return EMPTY_CARD_DATA;
    }

    log.info("| Loading certificates from {}", imageFile);
    try {
      final VirtualCardImageData cardData = imageLoader.load(imageFile);
      log.info("| CV certificate: {}", cardData.cvCertificate());
      log.info("| X.509 certificate: {}", cardData.authCertificate());
      log.info("| Sub-CA CV certificate:  {}", cardData.subCaCvCertificate());
      log.info("| EF.Version2:  {}", cardData.version2());
      return cardData;
    } catch (IOException | SAXException | ParserConfigurationException e) {
      throw new RuntimeException("Error when loading XML card image file", e);
    }
  }

  public boolean isConfigured() {
    return getCvCertificate() != null && getAuthCertificate() != null;
  }

  public String getCvCertificate() {
    return cardData.cvCertificate();
  }

  public String getAuthCertificate() {
    return cardData.authCertificate();
  }

  public List<String> process(final List<ScenarioStep> scenarioStep) {
    return process(scenarioStep, new VirtualCardSessionState());
  }

  public List<String> process(
      final List<ScenarioStep> scenarioStep, final VirtualCardSessionState sessionState) {
    final var responses = new ArrayList<String>();
    for (final var step : scenarioStep) {
      responses.add(process(step, sessionState));
    }
    return responses;
  }

  private String process(
      final ScenarioStep scenarioStep, final VirtualCardSessionState sessionState) {
    final var normalizedCommandApdu =
        VirtualCardPureHelper.normalize(scenarioStep.getCommandApdu());
    log.info("| APDU command: {}", normalizedCommandApdu);

    String responseAPDU;
    String statusWord = APDU_RESPONSE_OK;
    if (apduMatcher.isMutualAuthenticationStep1(
        normalizedCommandApdu, APDU_MUTUAL_AUTHENTICATION_STEP_1_PREFIX)) {
      sessionState.clearSecureMessagingSession();
      responseAPDU = buildEphemeralPublicKeyResponse(sessionState);
    } else if (apduMatcher.isMutualAuthenticationStep2(
        normalizedCommandApdu, APDU_MUTUAL_AUTHENTICATION_STEP_2_PREFIX)) {
      initializeSecureMessagingSession(normalizedCommandApdu, sessionState);
      responseAPDU = "";
    } else if (apduMatcher.isReadEndEntityCvCertificate(
        normalizedCommandApdu, APDU_READ_END_ENTITY_CV_CERTIFICATE_PREFIX)) { // READ CV certificate
      responseAPDU = cardData.cvCertificate();
    } else if (apduMatcher.isReadEfCChAutE256(
        normalizedCommandApdu,
        APDU_READ_EF_C_CH_AUT_E256_PREFIX,
        APDU_SECURE_READ_EF_C_CH_AUT_E256_PREFIX)) { // READ X.509 certificate
      responseAPDU = cardData.authCertificate();
    } else if (apduMatcher.isInternalAuthenticate(
        normalizedCommandApdu, APDU_INTERNAL_AUTHENTICATE_PREFIX)) {
      responseAPDU = signInternalAuthenticationChallenge(normalizedCommandApdu);
    } else {
      responseAPDU = resolveStaticResponse(normalizedCommandApdu);
    }
    if (apduMatcher.isSecureMessagingProtectedCommand(
            normalizedCommandApdu,
            APDU_SECURE_SELECT_DF_ESIGN_PREFIX,
            APDU_SECURE_READ_EF_C_CH_AUT_E256_PREFIX)
        && sessionState.hasSecureMessagingSession()) {
      responseAPDU =
          sessionState.getSecureMessagingSession().protectResponse(responseAPDU, statusWord);
    } else {
      responseAPDU = responseAPDU + statusWord;
    }
    log.info("| APDU response: {}", responseAPDU);
    return responseAPDU;
  }

  private String resolveStaticResponse(final String normalizedCommandApdu) {
    if (normalizedCommandApdu.equals(APDU_SELECT_MASTER_FILE)
        || normalizedCommandApdu.equals(APDU_SELECT_PRIVATE_KEY_TRUSTED)
        || normalizedCommandApdu.equals(APDU_SELECT_PRIVATE_KEY_CONTACTLESS)
        || normalizedCommandApdu.equals(APDU_SELECT_DF_ESIGN)) {
      return "";
    }
    if (normalizedCommandApdu.equals(APDU_READ_VERSION)) {
      return cardData.version2();
    }
    if (normalizedCommandApdu.equals(APDU_READ_SUB_CA_CV_CERTIFICATE)) {
      return cardData.subCaCvCertificate();
    }
    if (normalizedCommandApdu.equals(APDU_RETRIEVE_PUBLIC_KEY_IDENTIFIERS)) {
      return APDU_RESPONSE_RETRIEVE_PUBLIC_KEY_IDENTIFIERS;
    }
    return "";
  }

  private String signInternalAuthenticationChallenge(final String normalizedCommandApdu) {
    final byte[] egkAuthCvcPrivateKey = cardData.egkAuthCvcPrivateKey();
    if (egkAuthCvcPrivateKey.length == 0) {
      throw new IllegalStateException("No CVC private key configured for virtual card.");
    }
    final var commandApdu = new CommandAPDU(HexFormat.of().parseHex(normalizedCommandApdu));
    final byte[] tau = VirtualCardPureHelper.concatenate(commandApdu.getData(), new byte[1]);
    return VirtualCardPureHelper.toHex(signBrainpoolP256r1EcdsaValue(tau));
  }

  private byte[] signBrainpoolP256r1EcdsaValue(final byte[] value) {
    final byte[] egkAuthCvcPrivateKey = cardData.egkAuthCvcPrivateKey();
    final BigInteger privateKey = toPositiveInteger(egkAuthCvcPrivateKey);
    if (privateKey.signum() == 0 || privateKey.compareTo(BRAINPOOL_P256R1_ORDER) >= 0) {
      throw new IllegalStateException("Invalid CVC private key configured for virtual card.");
    }

    final BigInteger z = truncateDigest(value);
    for (int attempt = 0; attempt < MAX_ECDSA_SIGNING_ATTEMPTS; attempt++) {
      try (final var keyPair = Openhealth_cryptoKt.generateBrainpoolP256r1KeyPair()) {
        final var signature =
            tryCreateSignature(privateKey, z, keyPair.privateKey(), keyPair.publicKey());
        if (signature.isPresent()) {
          return signature.get();
        }
      } catch (final CryptoException e) {
        throw new IllegalStateException("Failed to sign CVC authentication challenge", e);
      }
    }
    throw new IllegalStateException("Failed to create a valid CVC ECDSA signature.");
  }

  private Optional<byte[]> tryCreateSignature(
      final BigInteger privateKey,
      final BigInteger z,
      final byte[] ephemeralPrivateKey,
      final byte[] ephemeralPublicKey) {
    final BigInteger k = toPositiveInteger(ephemeralPrivateKey);
    if (!isValidScalar(k)) {
      return Optional.empty();
    }

    final BigInteger r = extractSignatureR(ephemeralPublicKey);
    if (r.signum() == 0) {
      return Optional.empty();
    }

    final BigInteger s =
        k.modInverse(BRAINPOOL_P256R1_ORDER)
            .multiply(z.add(r.multiply(privateKey)))
            .mod(BRAINPOOL_P256R1_ORDER);
    if (s.signum() == 0) {
      return Optional.empty();
    }
    return Optional.of(
        VirtualCardPureHelper.concatenate(
            VirtualCardPureHelper.toFixedLength(r, BRAINPOOL_P256R1_COORDINATE_LENGTH),
            VirtualCardPureHelper.toFixedLength(s, BRAINPOOL_P256R1_COORDINATE_LENGTH)));
  }

  private static boolean isValidScalar(final BigInteger value) {
    return value.signum() != 0 && value.compareTo(BRAINPOOL_P256R1_ORDER) < 0;
  }

  private static BigInteger extractSignatureR(final byte[] publicKey) {
    return toPositiveInteger(
            Arrays.copyOfRange(publicKey, 1, 1 + BRAINPOOL_P256R1_COORDINATE_LENGTH))
        .mod(BRAINPOOL_P256R1_ORDER);
  }

  private static BigInteger truncateDigest(final byte[] value) {
    var digest = toPositiveInteger(value);
    final int excessBits = digest.bitLength() - BRAINPOOL_P256R1_ORDER.bitLength();
    if (excessBits > 0) {
      digest = digest.shiftRight(excessBits);
    }
    return digest;
  }

  private static BigInteger toPositiveInteger(final byte[] value) {
    return new BigInteger(1, value);
  }

  private void initializeSecureMessagingSession(
      final String normalizedCommandApdu, final VirtualCardSessionState sessionState) {
    final byte[] pendingCardEphemeralPrivateKey = sessionState.getPendingCardEphemeralPrivateKey();
    final byte[] egkAuthCvcPrivateKey = cardData.egkAuthCvcPrivateKey();
    if (poppServiceEndEntityPublicKey == null
        || pendingCardEphemeralPrivateKey == null
        || egkAuthCvcPrivateKey.length == 0) {
      log.warn("| Secure messaging session could not be initialized.");
      sessionState.clearSecureMessagingSession();
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
      final byte[] sharedSecret =
          VirtualCardPureHelper.concatenate(sharedSecretStatic, sharedSecretEphemeral);
      sessionState.setSecureMessagingSession(SecureMessagingSession.fromSharedSecret(sharedSecret));
    } catch (Asn1FfiException
        | CryptoException
        | IllegalArgumentException
        | NoSuchAlgorithmException e) {
      log.warn("| Failed to initialize secure messaging session.", e);
      sessionState.clearSecureMessagingSession();
    } finally {
      sessionState.clearPendingCardEphemeralPrivateKey();
    }
  }

  private String buildEphemeralPublicKeyResponse(final VirtualCardSessionState sessionState) {
    try (final var keyPair = Openhealth_cryptoKt.generateBrainpoolP256r1KeyPair()) {
      sessionState.setPendingCardEphemeralPrivateKey(keyPair.privateKey());
      return VirtualCardPureHelper.toHex(
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

  private static byte[] loadPoppServiceEndEntityPublicKey(final VirtualCardImageData cardData) {
    if (cardData.egkAuthCvcPrivateKey().length == 0) {
      return new byte[0];
    }
    return Arrays.copyOf(
        POPP_SERVICE_END_ENTITY_PUBLIC_KEY, POPP_SERVICE_END_ENTITY_PUBLIC_KEY.length);
  }
}
