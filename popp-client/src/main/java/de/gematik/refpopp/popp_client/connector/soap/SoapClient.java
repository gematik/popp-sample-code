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

import java.util.Optional;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.client.core.SoapActionCallback;
import org.springframework.ws.transport.http.SimpleHttpComponents5MessageSender;

public class SoapClient extends WebServiceGatewaySupport {

  private final String soapAction;
  private boolean mtomEnabled;
  private boolean hostnameValidationIsEnabled;
  private Optional<SSLContext> sslContext;

  public SoapClient(
      final Jaxb2Marshaller marshaller,
      final String soapAction,
      boolean hostnameValidationIsEnabled,
      Optional<SSLContext> sslContext) {
    setMarshaller(marshaller);
    setUnmarshaller(marshaller);
    this.soapAction = soapAction;
    this.hostnameValidationIsEnabled = hostnameValidationIsEnabled;
    this.sslContext = sslContext;
  }

  public <T> T sendRequest(
      final Object request, final String serviceEndpoint, final Class<T> responseType) {
    getJaxb2Marshaller().setMtomEnabled(mtomEnabled);
    final WebServiceTemplate webServiceTemplate = getWebServiceTemplate();
    webServiceTemplate.setInterceptors(new ClientInterceptor[] {new SoapClientInterceptor()});
    if (sslContext.isPresent()) {
      TlsSocketStrategy tlsSocketStrategy =
          ClientTlsStrategyBuilder.create()
              .setSslContext(sslContext.get())
              .setHostnameVerifier(
                  hostnameValidationIsEnabled
                      ? new DefaultHostnameVerifier()
                      : NoopHostnameVerifier.INSTANCE)
              .buildClassic();

      PoolingHttpClientConnectionManager connectionManager =
          PoolingHttpClientConnectionManagerBuilder.create()
              .setTlsSocketStrategy(tlsSocketStrategy)
              .build();

      CloseableHttpClient httpClient =
          HttpClients.custom()
              .setConnectionManager(connectionManager)
              // In order to prevent an I/O error: Content-Length header already present
              .addRequestInterceptorFirst(
                  (httpRequest, entity, context) -> httpRequest.removeHeaders("Content-Length"))
              .build();

      SimpleHttpComponents5MessageSender messageSender =
          new SimpleHttpComponents5MessageSender(httpClient);
      webServiceTemplate.setMessageSender(messageSender);
    }
    final T response =
        responseType.cast(
            webServiceTemplate.marshalSendAndReceive(
                serviceEndpoint, request, new SoapActionCallback(soapAction)));
    mtomEnabled = false;
    return response;
  }

  private Jaxb2Marshaller getJaxb2Marshaller() {
    return ((Jaxb2Marshaller) getMarshaller());
  }
}
