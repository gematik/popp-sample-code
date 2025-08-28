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

import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.refpopp.popp_client.connector.soap.SoapClient;
import de.gematik.ws.conn.cardservice.v821.SecureSendAPDU;
import de.gematik.ws.conn.cardservice.v821.SecureSendAPDUResponse;
import java.util.List;
import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

@Component
public class SecureSendAPDUClient extends SoapClient {

  private final ServiceEndpointProvider serviceEndpointProvider;

  public SecureSendAPDUClient(
      final Jaxb2Marshaller cardServiceMarshaller,
      final ServiceEndpointProvider serviceEndpointProvider,
      @Value("${connector.soap-services.secure-send-apdu}") final String soapActionSecureSendAPDU,
      @Autowired(required = false) @Qualifier("httpClientWithBC") HttpClient httpClient) {
    super(cardServiceMarshaller, soapActionSecureSendAPDU, httpClient);
    this.serviceEndpointProvider = serviceEndpointProvider;
  }

  public List<String> performSecureSendAPDU(final String signedScenario) {
    final var secureSendAPDU = new SecureSendAPDU();
    secureSendAPDU.setSignedScenario(signedScenario);
    final var soapResponse =
        sendRequest(
            secureSendAPDU,
            serviceEndpointProvider.getCardServiceFullEndpoint(),
            SecureSendAPDUResponse.class);
    return soapResponse.getSignedScenarioResponse().getResponseApduList().getResponseApdu();
  }
}
