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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioStepCommandResolverTest {

  private SessionAccessor sessionAccessorMock;
  private CardApduFactory cardApduFactoryMock;
  private ScenarioStepCommandResolver sut;

  @BeforeEach
  void setUp() {
    sessionAccessorMock = mock(SessionAccessor.class);
    cardApduFactoryMock = mock(CardApduFactory.class);
    sut = new ScenarioStepCommandResolver(sessionAccessorMock, cardApduFactoryMock);
  }

  @Test
  void resolvesStaticOpenHealthCommandsWithoutConfiguredApdu() {
    final var stepDefinition = new StepDefinition(StepId.SELECT_MASTER_FILE);
    when(cardApduFactoryMock.selectMasterFile()).thenReturn("00a4040c07d2760001448000");

    final var actual = sut.serializeCommandApdu("session-id", stepDefinition);

    assertThat(actual).isEqualTo("00a4040c07d2760001448000");
  }

  @Test
  void resolvesInternalAuthenticateFromStoredNonce() {
    final byte[] nonce = new byte[24];
    final var stepDefinition = new StepDefinition(StepId.INTERNAL_AUTHENTICATION);
    when(sessionAccessorMock.getNonce("session-id")).thenReturn(nonce);
    when(cardApduFactoryMock.internalAuthenticate(nonce)).thenReturn("0088000018nonce00");

    final var actual = sut.serializeCommandApdu("session-id", stepDefinition);

    assertThat(actual).isEqualTo("0088000018nonce00");
  }

  @Test
  void resolvesElcStep2FromStoredCvc() {
    final byte[] cvc = new byte[] {0x01, 0x02, 0x03};
    final var stepDefinition = new StepDefinition(StepId.MUTUAL_AUTHENTICATION_STEP_2);
    when(sessionAccessorMock.retrieveSessionData("session-id", SessionStorageKey.CVC, byte[].class))
        .thenReturn(Optional.of(cvc));
    when(cardApduFactoryMock.generalAuthenticateElcStep2(cvc)).thenReturn("00860000457c...");

    final var actual = sut.serializeCommandApdu("session-id", stepDefinition);

    assertThat(actual).isEqualTo("00860000457c...");
  }

  @Test
  void resolvesContactlessPrivateKeySelectionFromCommunicationMode() {
    final var stepDefinition = new StepDefinition(StepId.SELECT_PRIVATE_KEY);
    when(sessionAccessorMock.getCommunicationModeOrDefaultValue("session-id"))
        .thenReturn(CommunicationMode.CONTACTLESS);
    when(cardApduFactoryMock.selectPrivateKeyForContactless()).thenReturn("002241a406840109800100");

    final var actual = sut.serializeCommandApdu("session-id", stepDefinition);

    assertThat(actual).isEqualTo("002241a406840109800100");
  }

  @Test
  void resolvesTrustedChannelPrivateKeySelectionByDefault() {
    final var stepDefinition = new StepDefinition(StepId.SELECT_PRIVATE_KEY);
    when(sessionAccessorMock.getCommunicationModeOrDefaultValue("session-id"))
        .thenReturn(CommunicationMode.CONTACT);
    when(cardApduFactoryMock.selectPrivateKeyForTrustedChannel())
        .thenReturn("002241a406840109800154");

    final var actual = sut.serializeCommandApdu("session-id", stepDefinition);

    assertThat(actual).isEqualTo("002241a406840109800154");
  }

  @Test
  void resolvesDynamicCardOperationWithoutStepNameLookup() {
    final var stepDefinition = new StepDefinition(StepId.MSE_APDU, "car".getBytes());
    when(cardApduFactoryMock.manageSecEnvSetSignatureKeyReference(any()))
        .thenReturn("002281b60a8303636172");

    final var actual = sut.serializeCommandApdu("session-id", stepDefinition);

    assertThat(actual).isEqualTo("002281b60a8303636172");
  }

  @Test
  void resolvesPsoApduFromConfiguredCommandData() {
    final var commandData = "sign".getBytes();
    final var stepDefinition = new StepDefinition(StepId.PSO_APDU, commandData);
    when(cardApduFactoryMock.psoComputeDigitalSignatureCvc(commandData))
        .thenReturn("002a00be047369676e");

    final var actual = sut.serializeCommandApdu("session-id", stepDefinition);

    assertThat(actual).isEqualTo("002a00be047369676e");
  }

  @Test
  void resolvesMutualAuthenticationStep1() {
    final var stepDefinition = new StepDefinition(StepId.MUTUAL_AUTHENTICATION_STEP_1);
    when(cardApduFactoryMock.generalAuthenticateMutualAuthenticationStep1())
        .thenReturn("10860000107c0e...");

    final var actual = sut.serializeCommandApdu("session-id", stepDefinition);

    assertThat(actual).isEqualTo("10860000107c0e...");
  }

  @Test
  void resolvesDfEsignSelection() {
    final var stepDefinition = new StepDefinition(StepId.SELECT_DF_ESIGN);
    when(cardApduFactoryMock.selectDfEsign()).thenReturn("00a4040c0aa000000167455349474e");

    final var actual = sut.serializeCommandApdu("session-id", stepDefinition);

    assertThat(actual).isEqualTo("00a4040c0aa000000167455349474e");
  }

  @Test
  void resolvesReadEfCChAutE256ForReadEfStep() {
    final var stepDefinition = new StepDefinition(StepId.READ_EF_C_CH_AUT_E256);
    when(cardApduFactoryMock.readEfCChAutE256()).thenReturn("00b08400000000");

    final var actual = sut.serializeCommandApdu("session-id", stepDefinition);

    assertThat(actual).isEqualTo("00b08400000000");
  }

  @Test
  void resolvesReadEfCChAutE256ForReadX509Step() {
    final var stepDefinition = new StepDefinition(StepId.READ_X509);
    when(cardApduFactoryMock.readEfCChAutE256()).thenReturn("00b08400000000");

    final var actual = sut.serializeCommandApdu("session-id", stepDefinition);

    assertThat(actual).isEqualTo("00b08400000000");
  }

  @Test
  void resolvesOtherStaticOpenHealthCommandsWithoutConfiguredApdu() {
    when(cardApduFactoryMock.readVersion()).thenReturn("00b0910000");
    when(cardApduFactoryMock.readSubCaCvCertificate()).thenReturn("00b0870000");
    when(cardApduFactoryMock.readEndEntityCvCertificate()).thenReturn("00b0860000");
    when(cardApduFactoryMock.retrievePublicKeyIdentifiers()).thenReturn("80ca0100000000");

    assertThat(sut.serializeCommandApdu("session-id", new StepDefinition(StepId.READ_VERSION)))
        .isEqualTo("00b0910000");
    assertThat(
            sut.serializeCommandApdu(
                "session-id", new StepDefinition(StepId.READ_SUB_CA_CV_CERTIFICATE)))
        .isEqualTo("00b0870000");
    assertThat(
            sut.serializeCommandApdu(
                "session-id", new StepDefinition(StepId.READ_END_ENTITY_CV_CERTIFICATE)))
        .isEqualTo("00b0860000");
    assertThat(
            sut.serializeCommandApdu(
                "session-id", new StepDefinition(StepId.RETRIEVE_PUBLIC_KEY_IDENTIFIERS)))
        .isEqualTo("80ca0100000000");
  }

  @Test
  void throwsMeaningfulExceptionWhenCvcIsMissingForElcStep2() {
    final var stepDefinition = new StepDefinition(StepId.MUTUAL_AUTHENTICATION_STEP_2);
    when(sessionAccessorMock.retrieveSessionData("session-id", SessionStorageKey.CVC, byte[].class))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.serializeCommandApdu("session-id", stepDefinition))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No CVC found for ELC step 2");
  }
}
