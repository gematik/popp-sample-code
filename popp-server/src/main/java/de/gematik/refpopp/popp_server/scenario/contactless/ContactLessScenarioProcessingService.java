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

import de.gematik.refpopp.popp_server.communication.ClientCommunicationService;
import de.gematik.refpopp.popp_server.handler.SessionCommunication;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioMessageFactory;
import de.gematik.refpopp.popp_server.scenario.common.ScenarioTransitionService;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.Scenario;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractScenarioProcessingService;
import de.gematik.refpopp.popp_server.scenario.common.provider.CardScenarioProvider;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ContactLessScenarioProcessingService extends AbstractScenarioProcessingService {

  public static final String PLACEHOLDER_FOR_NONCE = "nonce";

  @Value("${scenario-names.auth-g2}")
  private String scenarioName;

  private final SessionAccessor sessionAccessor;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final HexFormat HEX_FORMAT = HexFormat.of();

  protected ContactLessScenarioProcessingService(
      final SessionAccessor sessionAccessor,
      final ScenarioMessageFactory scenarioMessageFactory,
      final ClientCommunicationService clientCommunicationService,
      final ScenarioTransitionService scenarioTransitionService) {
    super(
        sessionAccessor,
        scenarioMessageFactory,
        clientCommunicationService,
        scenarioTransitionService);
    this.sessionAccessor = sessionAccessor;
  }

  @Override
  public CommunicationMode getSupportedCommunicationMode() {
    return CommunicationMode.CONTACTLESS;
  }

  @Override
  public boolean isLastScenario(final Scenario currentScenario) {
    return currentScenario.name().equalsIgnoreCase(scenarioName);
  }

  @Override
  public void processScenario(
      final SessionCommunication session,
      final Scenario lastScenarioSentToClient,
      final CardScenarioProvider cardScenarioProvider) {
    if (isLastScenario(lastScenarioSentToClient)) {
      processLastScenario(session);
      return;
    }
    final var sessionId = session.getSessionId();
    final var nextScenario =
        getNextScenario(sessionId, lastScenarioSentToClient, cardScenarioProvider);
    final var scenarioWithNonce = createScenarioWithNonce(sessionId, nextScenario);
    sessionAccessor.storeScenario(sessionId, scenarioWithNonce);
    createAndSendMessage(session, scenarioWithNonce);
  }

  private Scenario createScenarioWithNonce(final String sessionId, final Scenario scenario) {
    final String nonce = getNonce(sessionId);
    log.info("| Generated nonce: {}", nonce);

    final List<StepDefinition> stepDefinitions =
        scenario.stepDefinitions().stream()
            .map(
                step -> {
                  final String commandApdu = step.commandApdu();
                  if (commandApdu.contains(PLACEHOLDER_FOR_NONCE)) {
                    final var replacedCommand = commandApdu.replace(PLACEHOLDER_FOR_NONCE, nonce);
                    return new StepDefinition(
                        step.name(),
                        step.description(),
                        replacedCommand,
                        step.expectedStatusWord());
                  }
                  return step;
                })
            .toList();

    return new Scenario(scenario.name(), stepDefinitions);
  }

  private String getNonce(final String sessionId) {
    final byte[] nonce = new byte[24];
    SECURE_RANDOM.nextBytes(nonce);
    sessionAccessor.storeNonce(sessionId, nonce);
    return HEX_FORMAT.formatHex(nonce);
  }
}
