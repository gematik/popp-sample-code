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
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

/** Sends a <i>GetCards</i> request to the connector. */
@Component
@Lazy
@Slf4j
public class GetCardsClient extends SoapClient {

  private final Context context;
  private final ServiceEndpointProvider serviceEndpointProvider;

  public GetCardsClient(
      final Jaxb2Marshaller eventServiceMarshaller,
      final Context context,
      final ServiceEndpointProvider serviceEndpointProvider,
      @Autowired(required = false) @Qualifier("httpClientWithBC") HttpClient httpClient) {
    super(
        eventServiceMarshaller,
        (Supplier<String>) () -> buildSoapAction(serviceEndpointProvider, SoapActions.GET_CARDS),
        httpClient);
    this.context = context;
    this.serviceEndpointProvider = serviceEndpointProvider;
  }

  public DetermineCardHandleResponse performGetCards() {
    final GetCards soapRequest = createSoapRequestObject();
    String endpoint = serviceEndpointProvider.getEventServiceFullEndpoint();
    log.info("Sending GetCards request to connector at {}", endpoint);
    final GetCardsResponse soapResponse =
        sendRequest(soapRequest, endpoint, GetCardsResponse.class);
    final DetermineCardHandleResponse determineCardHandleResponse =
        new DetermineCardHandleResponse();

    final List<String> cardHandles =
        soapResponse.getCards().getCard().stream()
            .filter(card -> CardTypeType.EGK.equals(card.getCardType()))
            .map(CardInfoType::getCardHandle)
            .toList();

    determineCardHandleResponse.setCardHandles(cardHandles);
    return determineCardHandleResponse;
  }

  private GetCards createSoapRequestObject() {
    return createGetCards();
  }

  private GetCards createGetCards() {
    final ContextType contextType = getContextType();
    final GetCards getCards = new GetCards();
    getCards.setContext(contextType);

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
