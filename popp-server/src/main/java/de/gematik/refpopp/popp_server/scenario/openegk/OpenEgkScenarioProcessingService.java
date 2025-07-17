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

package de.gematik.refpopp.popp_server.scenario.openegk;

import de.gematik.refpopp.popp_server.communication.ClientCommunicationService;
import de.gematik.refpopp.popp_server.handler.SessionCommunication;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioMessageFactory;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioTransitionService;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractScenarioProcessingService;
import de.gematik.refpopp.popp_server.scenario.common.provider.CardScenarioProvider;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import org.springframework.stereotype.Service;

@Service
public class OpenEgkScenarioProcessingService extends AbstractScenarioProcessingService {

  protected OpenEgkScenarioProcessingService(
      final SessionAccessor sessionAccessor,
      final ScenarioMessageFactory scenarioMessageFactory,
      final ClientCommunicationService clientCommunicationService,
      final ScenarioTransitionService scenarioTransitionService) {
    super(
        sessionAccessor,
        scenarioMessageFactory,
        clientCommunicationService,
        scenarioTransitionService);
  }

  @Override
  public CommunicationMode getSupportedCommunicationMode() {
    return CommunicationMode.UNDEFINED;
  }

  @Override
  public boolean isLastScenario(final Scenario currentScenario) {
    return false;
  }

  @Override
  public void processScenario(
      final SessionCommunication session,
      final Scenario lastScenarioSentToClient,
      final CardScenarioProvider cardScenarioProvider) {
    createAndSendMessage(session, cardScenarioProvider.getScenarios().getFirst());
  }
}
