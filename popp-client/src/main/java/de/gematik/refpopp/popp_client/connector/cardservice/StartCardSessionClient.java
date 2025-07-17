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
import de.gematik.ws.conn.cardservice.v8.StartCardSession;
import de.gematik.ws.conn.cardservice.v8.StartCardSessionResponse;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

@Component
public class StartCardSessionClient extends SoapClient {

  private final ServiceEndpointProvider serviceEndpointProvider;

  public StartCardSessionClient(
      final Jaxb2Marshaller cardServiceMarshaller,
      final ServiceEndpointProvider serviceEndpointProvider,
      @Value("${connector.soap-services.start-card-session}")
          final String soapActionStartCardSession,
      @Value("${connector.secure.hostname-validation}") boolean hostnameValidationIsEnabled,
      final Optional<SSLContext> sslContext) {
    super(
        cardServiceMarshaller, soapActionStartCardSession, hostnameValidationIsEnabled, sslContext);
    this.serviceEndpointProvider = serviceEndpointProvider;
  }

  public String performStartCardSession(final String handle) {
    final var startCardSession = new StartCardSession();
    startCardSession.setCardHandle(handle);
    final var soapResponse =
        sendRequest(
            startCardSession,
            serviceEndpointProvider.getCardServiceEndpoint().getEndpoint(),
            StartCardSessionResponse.class);
    return soapResponse.getSessionId();
  }
}
