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

package de.gematik.refpopp.popp_server.scenario.contactbased.readcvc;

import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.smartcards.g2icc.cos.SecureMessagingConverterSoftware;
import de.gematik.smartcards.g2icc.cvc.Cvc;
import de.gematik.smartcards.sdcom.apdu.CommandApdu;
import de.gematik.smartcards.tlv.BerTlv;
import de.gematik.smartcards.utils.Hex;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CustomApduStepDefinitionFactory {

  @Value("${step-names.mutual-authentication-step-1}")
  private String mutualAuthenticationStep1Name;

  private final CommandApduFactory commandApduFactory;
  private final SecureMessagingConverterSoftware secureMessagingConverterSoftware;

  public CustomApduStepDefinitionFactory(
      final CommandApduFactory commandApduFactory,
      final SecureMessagingConverterSoftware secureMessagingConverterSoftware) {
    this.commandApduFactory = commandApduFactory;
    this.secureMessagingConverterSoftware = secureMessagingConverterSoftware;
  }

  public List<StepDefinition> create(final List<Cvc> cvcList) {
    final var scenarioSteps = new ArrayList<StepDefinition>();
    final var expectedStatusWord = List.of("9000");
    for (int i = cvcList.size(); i-- > 0; ) {
      final var stepMSE = createStepMse(cvcList.get(i), expectedStatusWord);
      scenarioSteps.add(stepMSE);
      final var stepPSO = createStepPso(cvcList.get(i), expectedStatusWord);
      scenarioSteps.add(stepPSO);
    }
    final var firstStepOfMutualAuth = createStepOfMutualAuthentication(expectedStatusWord);
    scenarioSteps.add(firstStepOfMutualAuth);
    return scenarioSteps;
  }

  private StepDefinition createStepPso(final Cvc cvc, final List<String> expectedStatusWord) {
    final var psoAPDU = createPsoApdu(cvc);
    return new StepDefinition(
        "pso-apdu", "PSO APDU", Hex.toHexDigits(psoAPDU.getBytes()), expectedStatusWord);
  }

  private StepDefinition createStepMse(final Cvc cvc, final List<String> expectedStatusWord) {
    final var mseAPDU = createMseApdu(cvc);
    return new StepDefinition(
        "mse-apdu", "MSE APDU", Hex.toHexDigits(mseAPDU.getBytes()), expectedStatusWord);
  }

  private StepDefinition createStepOfMutualAuthentication(final List<String> expectedStatusWord) {
    final var commandApdu = secureMessagingConverterSoftware.getGeneralAuthenticateStep1();
    return new StepDefinition(
        mutualAuthenticationStep1Name,
        "First step of mutual authentication between a PoPP-Service and an eHC",
        Hex.toHexDigits(commandApdu.getBytes()),
        expectedStatusWord);
  }

  private CommandApdu createPsoApdu(final Cvc cvc) {
    return commandApduFactory.createCommandApdu(0x00, 0x2a, 0x00, 0xbe, cvc.getValueField());
  }

  private CommandApdu createMseApdu(final Cvc cvc) {
    final var berTlv = BerTlv.getInstance(0x83, cvc.getCar());
    return commandApduFactory.createCommandApdu(0x00, 0x22, 0x81, 0xb6, berTlv.getEncoded());
  }
}
