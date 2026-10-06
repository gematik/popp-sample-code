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

package de.gematik.refpopp.popp_client.connector.cardservice;

import static de.gematik.refpopp.popp_client.configuration.helper.SoapActionVersionHelper.buildSoapAction;

import de.gematik.refpopp.popp_client.connector.Context;
import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.refpopp.popp_client.connector.soap.SoapActions;
import de.gematik.refpopp.popp_client.connector.soap.SoapClient;
import de.gematik.ws.conn.cardservice.v821.VerifyPin;
import de.gematik.ws.conn.cardservicecommon.v2.PinResponseType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import jakarta.xml.bind.JAXBElement;
import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

/**
 * SOAP client for the connector's {@code VerifyPin} operation.
 *
 * <p>It requests verification of the SMC-B PIN for a card handle in the configured connector
 * context and returns the connector's PIN response.
 */
@Component
@Lazy
public class VerifyPinClient extends SoapClient {

  private final Context context;
  private final ServiceEndpointProvider serviceEndpointProvider;

  public VerifyPinClient(
      final Jaxb2Marshaller cardServiceMarshaller,
      final Context context,
      final ServiceEndpointProvider serviceEndpointProvider,
      @Autowired(required = false) @Qualifier("httpClientWithBC") HttpClient httpClient) {
    super(
        cardServiceMarshaller,
        () -> buildSoapAction(serviceEndpointProvider, SoapActions.VERIFY_PIN),
        httpClient);
    this.serviceEndpointProvider = serviceEndpointProvider;
    this.context = context;
  }

  public PinResponseType performVerifyPin(final String handle) {
    final VerifyPin verifyPin = createSoapRequest(handle);
    final var soapResponse =
        sendRequest(
            verifyPin, serviceEndpointProvider.getCardServiceFullEndpoint(), JAXBElement.class);
    return (PinResponseType) soapResponse.getValue();
  }

  private VerifyPin createSoapRequest(String handle) {
    final var verifyPin = new VerifyPin();
    verifyPin.setCardHandle(handle);

    final ContextType contextType = new ContextType();
    contextType.setClientSystemId(context.getClientSystemId());
    contextType.setMandantId(context.getMandantId());
    contextType.setWorkplaceId(context.getWorkplaceId());
    verifyPin.setContext(contextType);

    verifyPin.setPinTyp("PIN.SMC");
    return verifyPin;
  }
}
