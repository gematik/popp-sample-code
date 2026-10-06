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

package de.gematik.refpopp.popp_client.connector.eventservice;

import static de.gematik.refpopp.popp_client.configuration.helper.SoapActionVersionHelper.buildSoapAction;

import de.gematik.refpopp.popp_client.connector.Context;
import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.refpopp.popp_client.connector.soap.SoapActions;
import de.gematik.refpopp.popp_client.connector.soap.SoapClient;
import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.v7.GetCards;
import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import java.math.BigInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

/**
 * SOAP client for the connector's {@code GetCards} operation.
 *
 * <p>It requests cards of a specified type in the configured connector context, optionally limits
 * the query to a configured card-terminal slot, and filters eGK results by the supplied patient
 * identifier.
 */
@Component
@Lazy
@Slf4j
public class GetCardsClient extends SoapClient {

  private final Context context;
  private final ServiceEndpointProvider serviceEndpointProvider;
  private final String ctId;

  private final Integer ctSlot;

  public GetCardsClient(
      final Jaxb2Marshaller eventServiceMarshaller,
      final Context context,
      final ServiceEndpointProvider serviceEndpointProvider,
      @Autowired(required = false) @Qualifier("httpClientWithBC") HttpClient httpClient,
      @Value("${connector.terminal-configuration.ct-id:}") String ctId,
      @Value("${connector.terminal-configuration.ct-slot:}") Integer slot) {
    super(
        eventServiceMarshaller,
        () -> buildSoapAction(serviceEndpointProvider, SoapActions.GET_CARDS),
        httpClient);
    this.context = context;
    this.serviceEndpointProvider = serviceEndpointProvider;
    this.ctId = ctId;
    this.ctSlot = slot;
  }

  public DetermineCardHandleResponse performGetCards(String patientId, CardTypeType cardType) {
    final var getCardsRequest = createSoapRequestObject(cardType);
    var endpoint = serviceEndpointProvider.getEventServiceFullEndpoint();
    log.info("Sending GetCards request to connector at {}", endpoint);
    final GetCardsResponse soapResponse =
        sendRequest(getCardsRequest, endpoint, GetCardsResponse.class);
    final var determineCardHandleResponse = new DetermineCardHandleResponse();

    boolean filterByPatientId = patientId != null && !patientId.isBlank();

    final var cardHandles =
        soapResponse.getCards().getCard().stream()
            .filter(
                cardInfoType -> {
                  if (filterByPatientId) {
                    return cardInfoType.getKvnr() != null
                        && cardInfoType.getKvnr().equalsIgnoreCase(patientId);
                  }
                  return true;
                })
            .map(CardInfoType::getCardHandle)
            .toList();

    determineCardHandleResponse.setCardHandles(cardHandles);
    return determineCardHandleResponse;
  }

  private GetCards createSoapRequestObject(CardTypeType cardType) {
    return createGetCards(cardType);
  }

  private GetCards createGetCards(CardTypeType cardType) {
    final ContextType contextType = getContextType();
    final GetCards getCards = new GetCards();
    getCards.setContext(contextType);
    if (!ctId.isBlank()) {
      getCards.setCtId(ctId);
      if (ctSlot != null && ctSlot > 0) {
        getCards.setSlotId(BigInteger.valueOf(ctSlot));
      }
    }
    getCards.setCardType(cardType);

    return getCards;
  }

  private ContextType getContextType() {
    final ContextType contextType = new ContextType();
    contextType.setClientSystemId(context.getClientSystemId());
    contextType.setMandantId(context.getMandantId());
    contextType.setWorkplaceId(context.getWorkplaceId());

    return contextType;
  }
}
