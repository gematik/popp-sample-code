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

package de.gematik.refpopp.popp_client.connector.signatureservice;

import static de.gematik.refpopp.popp_client.configuration.helper.SoapActionVersionHelper.buildSoapAction;

import de.gematik.refpopp.popp_client.connector.Context;
import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.refpopp.popp_client.connector.soap.SoapActions;
import de.gematik.refpopp.popp_client.connector.soap.SoapClient;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.signatureservice.v7.BinaryDocumentType;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticate;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticateResponse;
import java.util.Base64;
import oasis.names.tc.dss._1_0.core.schema.Base64Data;
import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

/**
 * SOAP client for the connector's {@code ExternalAuthenticate} operation.
 *
 * <p>It sends a Base64-encoded authentication challenge to the specified card in the configured
 * connector context and returns the resulting signature.
 */
@Component
@Lazy
public class ExternalAuthenticateClient extends SoapClient {

  private final Context context;
  private final ServiceEndpointProvider serviceEndpointProvider;

  public ExternalAuthenticateClient(
      Jaxb2Marshaller signatureServiceMarshaller,
      final Context context,
      final ServiceEndpointProvider serviceEndpointProvider,
      @Autowired(required = false) @Qualifier("httpClientWithBC") HttpClient httpClient) {
    super(
        signatureServiceMarshaller,
        () -> buildSoapAction(serviceEndpointProvider, SoapActions.EXTERNAL_AUTHENTICATE),
        httpClient);
    this.serviceEndpointProvider = serviceEndpointProvider;
    this.context = context;
  }

  public byte[] performExternalAuthenticate(final String base64Challenge, final String handle) {
    final ExternalAuthenticate externalAuthenticate = createSoapRequest(base64Challenge, handle);
    final var soapResponse =
        sendRequest(
            externalAuthenticate,
            serviceEndpointProvider.getAuthSignatureServiceFullEndpoint(),
            ExternalAuthenticateResponse.class);
    return soapResponse.getSignatureObject().getBase64Signature().getValue();
  }

  private ExternalAuthenticate createSoapRequest(final String base64Challenge, String handle) {
    final var externalAuthenticate = new ExternalAuthenticate();
    externalAuthenticate.setCardHandle(handle);

    final ContextType contextType = new ContextType();
    contextType.setClientSystemId(context.getClientSystemId());
    contextType.setMandantId(context.getMandantId());
    contextType.setWorkplaceId(context.getWorkplaceId());
    externalAuthenticate.setContext(contextType);

    var hash = Base64.getDecoder().decode(base64Challenge);
    Base64Data data = new Base64Data();
    data.setValue(hash);
    data.setMimeType("application/octet-stream");
    var type = new BinaryDocumentType();
    type.setBase64Data(data);
    externalAuthenticate.setBinaryString(type);
    return externalAuthenticate;
  }
}
