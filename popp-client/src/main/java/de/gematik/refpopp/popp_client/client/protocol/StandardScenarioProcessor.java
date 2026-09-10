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

package de.gematik.refpopp.popp_client.client.protocol;

import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.refpopp.popp_client.cardreader.card.CardCommunicationService;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardService;
import de.gematik.refpopp.popp_client.client.session.ClientRequestContext;
import de.gematik.refpopp.popp_client.client.session.CommunicationSessionRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StandardScenarioProcessor {

  private final CardCommunicationService cardCommunicationService;
  private final VirtualCardService virtualCardService;
  private final CommunicationSessionRegistry sessionRegistry;

  public List<String> process(
      final StandardScenarioMessage standardScenarioMessage, final ClientRequestContext context) {
    final var steps = standardScenarioMessage.getSteps();
    if (context == null || !context.isVirtualCard()) {
      return cardCommunicationService.process(steps);
    }

    final var clientSessionId = context.getClientSessionId();
    final var sessionVirtualCardService =
        clientSessionId == null
            ? virtualCardService
            : sessionRegistry.getVirtualCardServiceOrDefault(clientSessionId, virtualCardService);

    if (!sessionVirtualCardService.isConfigured()) {
      throw new IllegalStateException("No image file configured for virtual card.");
    }

    if (clientSessionId == null) {
      return sessionVirtualCardService.process(steps);
    }

    final var sessionState = sessionRegistry.getOrCreateVirtualCardSessionState(clientSessionId);
    return sessionVirtualCardService.process(steps, sessionState);
  }
}
