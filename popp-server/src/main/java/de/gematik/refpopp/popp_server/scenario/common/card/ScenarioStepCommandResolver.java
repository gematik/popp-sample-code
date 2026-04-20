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

import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ScenarioStepCommandResolver {
  private final SessionAccessor sessionAccessor;
  private final CardApduFactory cardApduFactory;

  public ScenarioStepCommandResolver(
      final SessionAccessor sessionAccessor, final CardApduFactory cardApduFactory) {
    this.sessionAccessor = sessionAccessor;
    this.cardApduFactory = cardApduFactory;
  }

  public String serializeCommandApdu(final String sessionId, final StepDefinition step) {
    return resolveCommandApdu(sessionId, step);
  }

  private String resolveCommandApdu(final String sessionId, final StepDefinition step) {
    final var stepId =
        Objects.requireNonNull(step.stepId(), "No step configured for scenario step");
    return switch (stepId) {
      case SELECT_MASTER_FILE -> cardApduFactory.selectMasterFile();
      case READ_VERSION -> cardApduFactory.readVersion();
      case READ_SUB_CA_CV_CERTIFICATE -> cardApduFactory.readSubCaCvCertificate();
      case READ_END_ENTITY_CV_CERTIFICATE -> cardApduFactory.readEndEntityCvCertificate();
      case RETRIEVE_PUBLIC_KEY_IDENTIFIERS -> cardApduFactory.retrievePublicKeyIdentifiers();
      case SELECT_PRIVATE_KEY ->
          getCommunicationMode(sessionId) == CommunicationMode.CONTACTLESS
              ? cardApduFactory.selectPrivateKeyForContactless()
              : cardApduFactory.selectPrivateKeyForTrustedChannel();
      case MUTUAL_AUTHENTICATION_STEP_2 ->
          cardApduFactory.generalAuthenticateElcStep2(getRequiredCvc(sessionId));
      case SELECT_DF_ESIGN -> cardApduFactory.selectDfEsign();
      case READ_EF_C_CH_AUT_E256, READ_X509 -> cardApduFactory.readEfCChAutE256();
      case INTERNAL_AUTHENTICATION ->
          cardApduFactory.internalAuthenticate(sessionAccessor.getNonce(sessionId));
      case MUTUAL_AUTHENTICATION_STEP_1 ->
          cardApduFactory.generalAuthenticateMutualAuthenticationStep1();
      case MSE_APDU ->
          cardApduFactory.manageSecEnvSetSignatureKeyReference(requiredCommandData(step));
      case PSO_APDU -> cardApduFactory.psoComputeDigitalSignatureCvc(requiredCommandData(step));
    };
  }

  private byte[] requiredCommandData(final StepDefinition step) {
    final var commandData = step.commandData();
    if (commandData == null || commandData.length == 0) {
      throw new IllegalStateException("No command data found for step " + step.stepId().value());
    }
    return commandData;
  }

  private byte[] getRequiredCvc(final String sessionId) {
    return sessionAccessor
        .retrieveSessionData(sessionId, SessionStorageKey.CVC, byte[].class)
        .orElseThrow(() -> new IllegalStateException("No CVC found for ELC step 2"));
  }

  private CommunicationMode getCommunicationMode(final String sessionId) {
    return sessionAccessor.getCommunicationModeOrDefaultValue(sessionId);
  }
}
