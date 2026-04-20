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

package de.gematik.refpopp.popp_server.scenario.contactbased;

import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioId;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ContactBasedScenariosProvider extends AbstractCardScenarios {

  ContactBasedScenariosProvider(final List<Scenario> scenarios) {
    super(scenarios);
  }

  public ContactBasedScenariosProvider() {
    this(
        List.of(
            Scenario.of(
                ScenarioId.READ_CVC,
                StepId.READ_SUB_CA_CV_CERTIFICATE,
                StepId.READ_END_ENTITY_CV_CERTIFICATE,
                StepId.RETRIEVE_PUBLIC_KEY_IDENTIFIERS),
            Scenario.of(ScenarioId.TRUSTED_CHANNEL_STEP_1, StepId.SELECT_PRIVATE_KEY),
            Scenario.of(
                ScenarioId.READ_X509,
                StepId.MUTUAL_AUTHENTICATION_STEP_2,
                StepId.SELECT_DF_ESIGN,
                StepId.READ_EF_C_CH_AUT_E256)));
  }

  @Override
  public CommunicationMode getSupportedCommunicationMode() {
    return CommunicationMode.CONTACT;
  }
}
