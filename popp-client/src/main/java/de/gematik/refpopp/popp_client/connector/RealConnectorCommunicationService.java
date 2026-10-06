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
import de.gematik.refpopp.popp_client.connector.cardservice.VerifyPinClient;
import de.gematik.refpopp.popp_client.connector.certificateservice.ReadCardCertificateClient;
import de.gematik.refpopp.popp_client.connector.eventservice.DetermineCardHandleResponse;
import de.gematik.refpopp.popp_client.connector.eventservice.GetCardsClient;
import de.gematik.refpopp.popp_client.connector.signatureservice.ExternalAuthenticateClient;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import de.gematik.ws.conn.cardservicecommon.v2.PinResponseType;
import de.gematik.ws.conn.connectorcommon.v5.Status;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Coordinates SOAP client calls to a real TI connector.
 *
 * <p>The service retrieves eGK and SMC-B card handles, manages card sessions, exchanges signed
 * scenario APDUs, and performs the certificate and authentication operations required for SMC-B
 * authentication.
 */
@Slf4j
@Component
public class RealConnectorCommunicationService {

  private final GetCardsClient getCardsClient;
  private final StartCardSessionClient startCardSessionClient;
  private final StopCardSessionClient stopCardSessionClient;
  private final SecureSendAPDUClient secureSendAPDUClient;
  private final VerifyPinClient verifyPinClient;
  private final ReadCardCertificateClient readCardCertificateClient;
  private final ExternalAuthenticateClient externalAuthenticateClient;

  public RealConnectorCommunicationService(
      final GetCardsClient getCardsClient,
      final StartCardSessionClient startCardSessionClient,
      final StopCardSessionClient stopCardSessionClient,
      final SecureSendAPDUClient secureSendAPDUClient,
      final VerifyPinClient verifyPinClient,
      final ReadCardCertificateClient readCardCertificateClient,
      final ExternalAuthenticateClient externalAuthenticateClient) {
    this.getCardsClient = getCardsClient;
    this.startCardSessionClient = startCardSessionClient;
    this.stopCardSessionClient = stopCardSessionClient;
    this.secureSendAPDUClient = secureSendAPDUClient;
    this.verifyPinClient = verifyPinClient;
    this.readCardCertificateClient = readCardCertificateClient;
    this.externalAuthenticateClient = externalAuthenticateClient;
  }

  public String getConnectedEgkCard(String patientId) {
    final DetermineCardHandleResponse determineCardHandleResponse =
        getCardsClient.performGetCards(patientId, CardTypeType.EGK);
    final var cardHandles = determineCardHandleResponse.getCardHandles();

    return evaluateCardResponse(cardHandles);
  }

  public String getConnectedSmcbCard() {
    final DetermineCardHandleResponse determineCardHandleResponse =
        getCardsClient.performGetCards("", CardTypeType.SMC_B);
    final var cardHandles = determineCardHandleResponse.getCardHandles();

    return evaluateCardResponse(cardHandles);
  }

  public String startCardSession(final String cardHandle) {
    return startCardSessionClient.performStartCardSession(cardHandle);
  }

  public Status stopCardSession(final String uuidSessionId) {
    return stopCardSessionClient.performStopCardSession(uuidSessionId);
  }

  public List<String> secureSendApdu(final String signedScenario) {
    return secureSendAPDUClient.performSecureSendAPDU(signedScenario);
  }

  public PinResponseType verifyPin(final String cardHandle) {
    return verifyPinClient.performVerifyPin(cardHandle);
  }

  public byte[] readCardCertificate(final String cardHandle) {
    return readCardCertificateClient.performReadCardCertificate(cardHandle);
  }

  public byte[] externalAuthenticate(final String base64Challenge, final String cardHandle) {
    return externalAuthenticateClient.performExternalAuthenticate(base64Challenge, cardHandle);
  }

  private String evaluateCardResponse(final List<String> res) {
    if (res.isEmpty()) {
      throw new IllegalStateException("| Error fetching GetCards response");
    }
    return res.getFirst();
  }
}
