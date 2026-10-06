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

package de.gematik.refpopp.popp_client.connector.certificateservice;

import static de.gematik.refpopp.popp_client.configuration.helper.SoapActionVersionHelper.buildSoapAction;

import de.gematik.refpopp.popp_client.connector.Context;
import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.refpopp.popp_client.connector.soap.SoapActions;
import de.gematik.refpopp.popp_client.connector.soap.SoapClient;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificate;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificate.CertRefList;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificateResponse;
import de.gematik.ws.conn.certificateservicecommon.v2.CertRefEnum;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

/**
 * SOAP client for the connector's {@code ReadCardCertificate} operation.
 *
 * <p>It requests the C.AUT certificate for a card handle in the configured connector context and
 * returns the certificate's X.509 data.
 */
@Component
@Lazy
public class ReadCardCertificateClient extends SoapClient {

  private final Context context;
  private final ServiceEndpointProvider serviceEndpointProvider;

  public ReadCardCertificateClient(
      Jaxb2Marshaller certificateServiceMarshaller,
      final Context context,
      final ServiceEndpointProvider serviceEndpointProvider,
      @Autowired(required = false) @Qualifier("httpClientWithBC") HttpClient httpClient) {
    super(
        certificateServiceMarshaller,
        () -> buildSoapAction(serviceEndpointProvider, SoapActions.READ_CARD_CERTIFICATE),
        httpClient);
    this.serviceEndpointProvider = serviceEndpointProvider;
    this.context = context;
  }

  public byte[] performReadCardCertificate(final String handle) {
    final ReadCardCertificate readCardCertificate = createSoapRequest(handle);
    final var soapResponse =
        sendRequest(
            readCardCertificate,
            serviceEndpointProvider.getCertificateServiceFullEndpoint(),
            ReadCardCertificateResponse.class);
    return soapResponse
        .getX509DataInfoList()
        .getX509DataInfo()
        .getFirst()
        .getX509Data()
        .getX509Certificate();
  }

  private ReadCardCertificate createSoapRequest(String handle) {
    final var readCardCertificate = new ReadCardCertificate();
    readCardCertificate.setCardHandle(handle);

    final ContextType contextType = new ContextType();
    contextType.setClientSystemId(context.getClientSystemId());
    contextType.setMandantId(context.getMandantId());
    contextType.setWorkplaceId(context.getWorkplaceId());
    readCardCertificate.setContext(contextType);

    CertRefList certRefList = new CertRefList();
    certRefList.getCertRef().add(CertRefEnum.C_AUT);
    readCardCertificate.setCertRefList(certRefList);

    return readCardCertificate;
  }
}
