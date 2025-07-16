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

import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.refpopp.popp_client.connector.soap.SoapClient;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.v7.GetCards;
import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

/** Sends a GetCards request to the connector. */
@Component
@Slf4j
public class GetCardsClient extends SoapClient {

  private final Context context;
  private final ServiceEndpointProvider serviceEndpointProvider;

  public GetCardsClient(
      final Jaxb2Marshaller eventServiceMarshaller,
      final Context context,
      final ServiceEndpointProvider serviceEndpointProvider,
      @Value("${connector.soap-services.get-cards}") final String soapActionGetCards,
      @Value("${connector.secure.hostname-validation}") final boolean hostnameValidationIsEnabled,
      Optional<SSLContext> sslContext) {
    super(eventServiceMarshaller, soapActionGetCards, hostnameValidationIsEnabled, sslContext);
    this.context = context;
    this.serviceEndpointProvider = serviceEndpointProvider;
  }

  public DetermineCardHandleResponse performGetCards() {
    final GetCards soapRequest = createSoapRequestObject();
    final GetCardsResponse soapResponse =
        sendRequest(
            soapRequest,
            serviceEndpointProvider.getEventServiceFullEndpoint(),
            GetCardsResponse.class);
    final var determineCardHandleResponse = new DetermineCardHandleResponse();

    final List<String> cardHandles = new ArrayList<>();
    soapResponse.getCards().getCard().forEach(card -> cardHandles.add(card.getCardHandle()));

    determineCardHandleResponse.setCardHandles(cardHandles);
    return determineCardHandleResponse;
  }

  private GetCards createSoapRequestObject() {
    return createGetCards();
  }

  private GetCards createGetCards() {
    final var contextType = getContextType();
    final var getCards = new GetCards();
    getCards.setContext(contextType);

    return getCards;
  }

  private ContextType getContextType() {
    final var contextType = new ContextType();
    contextType.setClientSystemId(context.getClientSystemId());
    contextType.setMandantId(context.getMandantId());
    contextType.setWorkplaceId(context.getWorkplaceId());

    return contextType;
  }
}
