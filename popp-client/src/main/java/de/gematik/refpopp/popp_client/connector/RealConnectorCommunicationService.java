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

import de.gematik.refpopp.popp_client.connector.cardservice.SecureSendAPDUClient;
import de.gematik.refpopp.popp_client.connector.cardservice.StartCardSessionClient;
import de.gematik.refpopp.popp_client.connector.cardservice.StopCardSessionClient;
import de.gematik.refpopp.popp_client.connector.eventservice.DetermineCardHandleResponse;
import de.gematik.refpopp.popp_client.connector.eventservice.GetCardsClient;
import de.gematik.ws.conn.connectorcommon.v5.Status;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!mock")
public class RealConnectorCommunicationService implements ConnectorCommunicationService {

  private final GetCardsClient getCardsClient;
  private final StartCardSessionClient startCardSessionClient;
  private final StopCardSessionClient stopCardSessionClient;
  private final SecureSendAPDUClient secureSendAPDUClient;

  public RealConnectorCommunicationService(
      final GetCardsClient getCardsClient,
      final StartCardSessionClient startCardSessionClient,
      final StopCardSessionClient stopCardSessionClient,
      final SecureSendAPDUClient secureSendAPDUClient) {
    this.getCardsClient = getCardsClient;
    this.startCardSessionClient = startCardSessionClient;
    this.stopCardSessionClient = stopCardSessionClient;
    this.secureSendAPDUClient = secureSendAPDUClient;
  }

  @Override
  public String getConnectedEgkCard() {
    final DetermineCardHandleResponse determineCardHandleResponse =
        getCardsClient.performGetCards();
    final List<String> cardHandles = determineCardHandleResponse.getCardHandles();

    return evaluateCardResponse(cardHandles);
  }

  @Override
  public String startCardSession(final String cardHandle) {
    return startCardSessionClient.performStartCardSession(cardHandle);
  }

  @Override
  public Status stopCardSession(final String uuidSessionId) {
    return stopCardSessionClient.performStopCardSession(uuidSessionId);
  }

  @Override
  public List<String> secureSendApdu(final String signedScenario) {
    return secureSendAPDUClient.performSecureSendAPDU(signedScenario);
  }

  private String evaluateCardResponse(final List<String> res) {
    if (res.isEmpty()) {
      throw new IllegalStateException("| Error fetching EGK card response");
    }
    return res.getFirst();
  }
}
