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

import de.gematik.refpopp.popp_client.connector.ConnectorServicesFactory;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.client.core.SoapActionCallback;
import org.springframework.ws.transport.http.SimpleHttpComponents5MessageSender;

/**
 * Base class for connector SOAP clients.
 *
 * <p>Configures JAXB marshalling, SOAP action handling, request interception, and the optional
 * Bouncy Castle-enabled HTTP client. The SOAP action is resolved lazily and cached for subsequent
 * requests.
 *
 * <p>Bei einem Verbindungsproblem ({@link WebServiceIOException}, Dienst nicht erreichbar) führt
 * der Client einen Refresh auf alle Endpunktinformationen des Dienstverzeichnisdienstes durch
 * (TIP1-A_4967) und verwirft die gecachte SOAP-Action; der Fehler geht danach unverändert an den
 * Aufrufer.
 */
@Slf4j
public class SoapClient extends WebServiceGatewaySupport {

  private final Supplier<String> soapActionSupplier;
  private final HttpClient httpClient;
  private String soapActionCached;
  private boolean mtomEnabled;
  private ConnectorServicesFactory connectorServicesFactory;

  public SoapClient(
      final Jaxb2Marshaller marshaller,
      final Supplier<String> soapActionSupplier,
      HttpClient httpClient) {
    setMarshaller(marshaller);
    setUnmarshaller(marshaller);
    this.soapActionSupplier = soapActionSupplier;
    this.httpClient = httpClient;
  }

  @Autowired(required = false)
  public void setConnectorServicesFactory(final ConnectorServicesFactory connectorServicesFactory) {
    this.connectorServicesFactory = connectorServicesFactory;
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
    final T response;
    try {
      response =
          responseType.cast(
              webServiceTemplate.marshalSendAndReceive(
                  serviceEndpoint, request, new SoapActionCallback(getSoapAction())));
    } catch (final WebServiceIOException e) {
      refreshEndpoints(serviceEndpoint, e);
      throw e;
    }
    mtomEnabled = false;
    return response;
  }

  /** TIP1-A_4967: Refresh auf alle Endpunktinformationen, wenn der Dienst nicht erreichbar ist. */
  private void refreshEndpoints(final String serviceEndpoint, final WebServiceIOException cause) {
    synchronized (this) {
      soapActionCached = null;
    }
    if (connectorServicesFactory == null) {
      return;
    }
    log.warn(
        "Dienst '{}' nicht erreichbar ({}), lade connector.sds neu (TIP1-A_4967)",
        serviceEndpoint,
        cause.getMessage());
    try {
      connectorServicesFactory.refresh();
    } catch (final RuntimeException e) {
      log.warn("Refresh von connector.sds fehlgeschlagen, nächster Aufruf lädt erneut", e);
    }
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
