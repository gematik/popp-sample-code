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

package de.gematik.refpopp.popp_server.scenario.contactless;

import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioId;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ContactLessScenariosProvider extends AbstractCardScenarios {

  public ContactLessScenariosProvider() {
    super(
        List.of(
            Scenario.of(
                ScenarioId.AUTH_G2,
                StepId.READ_SUB_CA_CV_CERTIFICATE,
                StepId.READ_END_ENTITY_CV_CERTIFICATE,
                StepId.SELECT_DF_ESIGN,
                StepId.SELECT_PRIVATE_KEY,
                StepId.READ_X509,
                StepId.INTERNAL_AUTHENTICATION)));
  }

  @Override
  public CommunicationMode getSupportedCommunicationMode() {
    return CommunicationMode.CONTACTLESS;
  }
}
