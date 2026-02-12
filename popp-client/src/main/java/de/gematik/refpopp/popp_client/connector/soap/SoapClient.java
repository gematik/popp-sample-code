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

package de.gematik.refpopp.popp_client.connector.soap;

import java.util.function.Supplier;
import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.client.core.SoapActionCallback;
import org.springframework.ws.transport.http.SimpleHttpComponents5MessageSender;

public class SoapClient extends WebServiceGatewaySupport {

  private final Supplier<String> soapActionSupplier;
  private final HttpClient httpClient;
  private String soapActionCached;
  private boolean mtomEnabled;

  public SoapClient(
      final Jaxb2Marshaller marshaller,
      final Supplier<String> soapActionSupplier,
      HttpClient httpClient) {
    setMarshaller(marshaller);
    setUnmarshaller(marshaller);
    this.soapActionSupplier = soapActionSupplier;
    this.httpClient = httpClient;
  }

  public <T> T sendRequest(
      final Object request, final String serviceEndpoint, final Class<T> responseType) {
    getJaxb2Marshaller().setMtomEnabled(mtomEnabled);
    final WebServiceTemplate webServiceTemplate = getWebServiceTemplate();
    webServiceTemplate.setInterceptors(new ClientInterceptor[] {new SoapClientInterceptor()});
    if (httpClient != null) {
      SimpleHttpComponents5MessageSender messageSender =
          new SimpleHttpComponents5MessageSender(httpClient);
      webServiceTemplate.setMessageSender(messageSender);
    }
    final T response =
        responseType.cast(
            webServiceTemplate.marshalSendAndReceive(
                serviceEndpoint, request, new SoapActionCallback(getSoapAction())));
    mtomEnabled = false;
    return response;
  }

  protected synchronized String getSoapAction() {
    if (soapActionCached == null && soapActionSupplier != null) {
      soapActionCached = soapActionSupplier.get();
    }
    return soapActionCached;
  }

  private Jaxb2Marshaller getJaxb2Marshaller() {
    return ((Jaxb2Marshaller) getMarshaller());
  }
}
