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

package de.gematik.refpopp.popp_client.connector.session;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.refpopp.popp_client.client.session.ClientRequestContext;
import de.gematik.refpopp.popp_client.connector.ConnectorCommunicationServiceWrapper;
import java.util.concurrent.CancellationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.ws.soap.client.SoapFaultClientException;

@Component
@Slf4j
@RequiredArgsConstructor
public class ConnectorSessionLifecycle {

  private final ConnectorCommunicationServiceWrapper connectorCommunicationServiceWrapper;

  public String startSession(final String patientId) {
    return connectorCommunicationServiceWrapper.startCardSession(
        connectorCommunicationServiceWrapper.getConnectedEgkCard(patientId));
  }

  public void stopSessionIfRequired(final ClientRequestContext context) {
    final var cardConnectionType = context.getCardConnectionType();
    if (!usesConnectorSession(cardConnectionType)) {
      return;
    }

    final var clientSessionId = context.getClientSessionId();
    try {
      connectorCommunicationServiceWrapper.stopCardSession(clientSessionId);
    } catch (CancellationException e) {
      log.info(
          "| StopCardSession was cancelled, probably because the session is already shutting"
              + " down: {}",
          clientSessionId);
    } catch (SoapFaultClientException exception) {
      final String faultString = exception.getFaultStringOrReason();
      final boolean unknownSession =
          faultString != null && faultString.contains("Unbekannte Session ID");
      if (!unknownSession) {
        throw exception;
      }
      log.info("| Session {} is already closed.", clientSessionId);
    }
  }

  private boolean usesConnectorSession(final CardConnectionType cardConnectionType) {
    return CardConnectionType.CONTACT_CONNECTOR.equals(cardConnectionType)
        || CardConnectionType.CONTACTLESS_CONNECTOR.equals(cardConnectionType);
  }
}
