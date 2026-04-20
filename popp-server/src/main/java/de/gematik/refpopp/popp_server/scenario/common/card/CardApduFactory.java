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

import de.gematik.openhealth.crypto.CryptoException;
import de.gematik.openhealth.crypto.Openhealth_cryptoKt;
import de.gematik.openhealth.healthcard.ApduException;
import de.gematik.openhealth.healthcard.CommandBuilderException;
import de.gematik.openhealth.healthcard.HealthCardCommand;
import de.gematik.refpopp.popp_server.certificates.CertificateProviderService;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CardApduFactory {

  private static final HexFormat HEX_FORMAT = HexFormat.of();
  private static final int SFI_VERSION = 0x11;
  private static final int SFI_SUB_CA_CV_CERTIFICATE = 0x07;
  private static final int SFI_END_ENTITY_CV_CERTIFICATE = 0x06;
  private static final int SFI_EF_C_CH_AUT_E256 = 0x04;
  private static final byte PRIVATE_KEY_REFERENCE = 0x09;
  private static final byte ALGORITHM_ID_TRUSTED_CHANNEL = 0x54;
  private static final byte ALGORITHM_ID_CONTACTLESS = 0x00;
  private static final byte[] EGK_AID = HEX_FORMAT.parseHex("D2760001448000");
  private static final byte[] DF_ESIGN_AID = HEX_FORMAT.parseHex("A000000167455349474E");
  private final byte[] trustedChannelKeyReference;
  private final ElcEphemeralPublicKeyGenerator elcEphemeralPublicKeyGenerator;

  @Autowired
  public CardApduFactory(final CertificateProviderService certificateProviderService) {
    this(
        certificateProviderService.getCvEndEntityCertificate().getChrObject().getValue(),
        Openhealth_cryptoKt::generateElcEphemeralPublicKey);
  }

  CardApduFactory(final byte[] trustedChannelKeyReference) {
    this(trustedChannelKeyReference, Openhealth_cryptoKt::generateElcEphemeralPublicKey);
  }

  CardApduFactory(
      final byte[] trustedChannelKeyReference,
      final ElcEphemeralPublicKeyGenerator elcEphemeralPublicKeyGenerator) {
    this.trustedChannelKeyReference = trustedChannelKeyReference.clone();
    this.elcEphemeralPublicKeyGenerator = elcEphemeralPublicKeyGenerator;
  }

  public String selectMasterFile() {
    return toHex(command(() -> HealthCardCommand.Companion.selectAid(EGK_AID)), false);
  }

  public String readVersion() {
    return toHex(command(() -> HealthCardCommand.Companion.readSfi(SFI_VERSION)), false);
  }

  public String readSubCaCvCertificate() {
    return toHex(
        command(() -> HealthCardCommand.Companion.readSfi(SFI_SUB_CA_CV_CERTIFICATE)), false);
  }

  public String readEndEntityCvCertificate() {
    return toHex(
        command(() -> HealthCardCommand.Companion.readSfi(SFI_END_ENTITY_CV_CERTIFICATE)), false);
  }

  public String retrievePublicKeyIdentifiers() {
    return toHex(command(HealthCardCommand.Companion::listPublicKeys), true);
  }

  public String selectPrivateKeyForTrustedChannel() {
    return toHex(
        command(
            () ->
                HealthCardCommand.Companion.manageSecEnvSelectPrivateKey(
                    PRIVATE_KEY_REFERENCE, ALGORITHM_ID_TRUSTED_CHANNEL)),
        false);
  }

  public String selectPrivateKeyForContactless() {
    return toHex(
        command(
            () ->
                HealthCardCommand.Companion.manageSecEnvSelectPrivateKey(
                    PRIVATE_KEY_REFERENCE, ALGORITHM_ID_CONTACTLESS)),
        false);
  }

  public String manageSecEnvSetSignatureKeyReference(final byte[] keyReference) {
    return toHex(
        command(
            () -> HealthCardCommand.Companion.manageSecEnvSetSignatureKeyReference(keyReference)),
        false);
  }

  public String psoComputeDigitalSignatureCvc(final byte[] cvcValueField) {
    return toHex(
        command(() -> HealthCardCommand.Companion.psoComputeDigitalSignatureCvc(cvcValueField)),
        false);
  }

  public String generalAuthenticateMutualAuthenticationStep1() {
    return toHex(
        command(
            () ->
                HealthCardCommand.Companion.generalAuthenticateMutualAuthenticationStep1(
                    trustedChannelKeyReference)),
        false);
  }

  public String generalAuthenticateElcStep2(final byte[] cvc) {
    try {
      final var ephemeralPublicKey = elcEphemeralPublicKeyGenerator.generate(cvc);
      return toHex(
          command(
              () -> HealthCardCommand.Companion.generalAuthenticateElcStep2(ephemeralPublicKey)),
          false);
    } catch (final CryptoException e) {
      throw new IllegalStateException(e);
    }
  }

  public String selectDfEsign() {
    return toHex(command(() -> HealthCardCommand.Companion.selectAid(DF_ESIGN_AID)), false);
  }

  public String readEfCChAutE256() {
    return toHex(command(() -> HealthCardCommand.Companion.readSfi(SFI_EF_C_CH_AUT_E256)), true);
  }

  public String internalAuthenticate(final byte[] nonce) {
    return toHex(command(() -> HealthCardCommand.Companion.internalAuthenticate(nonce)), false);
  }

  private String toHex(final HealthCardCommand command, final boolean useExtendedLength) {
    try {
      return HEX_FORMAT.formatHex(
          command.toApdu(useExtendedLength).toVec().cloneAsNonzeroizingVec());
    } catch (final ApduException e) {
      throw new IllegalStateException(e);
    }
  }

  private HealthCardCommand command(final ThrowingSupplier<HealthCardCommand> supplier) {
    try {
      return supplier.get();
    } catch (final CommandBuilderException e) {
      throw new IllegalStateException(e);
    }
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws CommandBuilderException;
  }

  @FunctionalInterface
  interface ElcEphemeralPublicKeyGenerator {
    byte[] generate(byte[] cvc) throws CryptoException;
  }
}
