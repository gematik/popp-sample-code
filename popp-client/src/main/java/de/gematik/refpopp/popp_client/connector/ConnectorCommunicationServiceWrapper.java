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

package de.gematik.refpopp.popp_client.connector;

import de.gematik.refpopp.popp_client.client.ClientServerCommunicationService;
import de.gematik.ws.conn.connectorcommon.v5.Status;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectorCommunicationServiceWrapper {
  public static final String CONNECTOR_MOCK = "connectorMock";
  private final ClientServerCommunicationService clientServerCommunicationService;
  @Lazy private final RealConnectorCommunicationService realConnectorCommunicationService;
  private final MockConnectorCommunicationService mockConnectorCommunicationService;

  private boolean isMock() {
    try {
      final var sslSession = clientServerCommunicationService.getSSLSession();
      return (boolean) sslSession.getValue(CONNECTOR_MOCK);
    } catch (Exception e) {
      // No SSL session or mock attribute not found
      return false;
    }
  }

  public String getConnectedEgkCard() {
    if (isMock()) {
      return mockConnectorCommunicationService.getConnectedEgkCard();
    }

    return realConnectorCommunicationService.getConnectedEgkCard();
  }

  public String startCardSession(final String cardHandle) {
    if (isMock()) {
      return mockConnectorCommunicationService.startCardSession(cardHandle);
    }

    return realConnectorCommunicationService.startCardSession(cardHandle);
  }

  public Status stopCardSession(final String uuidSessionId) {
    if (isMock()) {
      return mockConnectorCommunicationService.stopCardSession(uuidSessionId);
    }

    return realConnectorCommunicationService.stopCardSession(uuidSessionId);
  }

  public List<String> secureSendApdu(final String signedScenario) {
    if (isMock()) {
      return mockConnectorCommunicationService.secureSendApdu(signedScenario);
    }

    return realConnectorCommunicationService.secureSendApdu(signedScenario);
  }
}
