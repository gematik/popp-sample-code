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

package de.gematik.refpopp.popp_server.scenario.common.orchestrator;

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.poppcommons.api.messages.PoPPMessage;
import de.gematik.refpopp.popp_server.handler.SessionCommunication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MessageHandlerOrchestrator implements MessageOrchestrator {

  private final MessageHandlerProvider handlerProvider;

  public MessageHandlerOrchestrator(final MessageHandlerProvider handlerProvider) {
    this.handlerProvider = handlerProvider;
  }

  @Override
  public void orchestrate(final PoPPMessage message, final SessionCommunication session) {
    log.debug("| Entering processScenario() with message type {}", message.getType());

    final var messageHandler = handlerProvider.getHandlerFor(message);
    if (messageHandler == null) {
      throw new ScenarioException(
          session.getSessionId(), "Unsupported message type " + message.getType(), "errorCode");
    }

    messageHandler.handle(message, session);

    log.debug("| Exiting processScenario() with message type {}", message.getType());
  }
}
