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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_client.connector.ConnectorServicesFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.WebServiceTransportException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapBody;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.soap.client.core.SoapActionCallback;

class SoapClientTest {

  private SoapClient sut;
  private Jaxb2Marshaller marshallerMock;

  @BeforeEach
  void setUp() {
    marshallerMock = mock(Jaxb2Marshaller.class);
    final String soapAction = "http://tempuri.org/SoapAction";
    sut = new SoapClient(marshallerMock, () -> soapAction, HttpClients.createDefault());
  }

  @Test
  void sendRequestReturnsResponse() {
    // given
    final WebServiceTemplate webServiceTemplateMock = mock(WebServiceTemplate.class);
    sut.setWebServiceTemplate(webServiceTemplateMock);
    when(webServiceTemplateMock.getMarshaller()).thenReturn(marshallerMock);
    when(webServiceTemplateMock.getUnmarshaller()).thenReturn(marshallerMock);

    final var request = "testRequest";
    final var endpoint = "http://test.endpoint";
    final var expectedResponse = "testResponse";

    when(webServiceTemplateMock.marshalSendAndReceive(
            eq(endpoint), eq(request), any(SoapActionCallback.class)))
        .thenReturn(expectedResponse);

    // when
    final var actualResponse = sut.sendRequest(request, endpoint, String.class);

    // then
    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(marshallerMock).setMtomEnabled(false);
    verify(webServiceTemplateMock)
        .setInterceptors(
            argThat(
                interceptors ->
                    interceptors != null
                        && interceptors.length == 1
                        && interceptors[0] instanceof SoapClientInterceptor));
  }

  @Test
  void giveSslContextAndCheckItIsSet() {
    // given
    HttpClient httpClient = mock(HttpClient.class);
    final String soapAction = "http://tempuri.org/SoapAction";
    SoapClient sslSut = new SoapClient(marshallerMock, () -> soapAction, httpClient);

    final WebServiceTemplate webServiceTemplateMock = mock(WebServiceTemplate.class);
    sslSut.setWebServiceTemplate(webServiceTemplateMock);
    when(webServiceTemplateMock.getMarshaller()).thenReturn(marshallerMock);
    when(webServiceTemplateMock.getUnmarshaller()).thenReturn(marshallerMock);

    final var request = "testRequest";
    final var endpoint = "http://test.endpoint";
    final var expectedResponse = "testResponse";

    when(webServiceTemplateMock.marshalSendAndReceive(
            eq(endpoint), eq(request), any(SoapActionCallback.class)))
        .thenReturn(expectedResponse);

    // when
    final var actualResponse = sslSut.sendRequest(request, endpoint, String.class);

    // then
    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(webServiceTemplateMock).setMessageSender(notNull());
  }

  @Test
  @DisplayName(
      "TIP1-A_4967: Ist der Dienst nicht erreichbar, folgt ein Refresh auf alle"
          + " Endpunktinformationen")
  void connectionProblemShouldRefreshEndpoints() {
    final var soapActionCalls = new AtomicInteger();
    final var refreshingSut =
        new SoapClient(
            marshallerMock,
            () -> "http://tempuri.org/SoapAction/" + soapActionCalls.incrementAndGet(),
            null);
    final var connectorServicesFactoryMock = mock(ConnectorServicesFactory.class);
    refreshingSut.setConnectorServicesFactory(connectorServicesFactoryMock);
    final WebServiceTemplate webServiceTemplateMock = webServiceTemplateMock(refreshingSut);
    when(webServiceTemplateMock.marshalSendAndReceive(
            eq("http://test.endpoint"), eq("request"), any(SoapActionCallback.class)))
        .thenThrow(new WebServiceIOException("Connection refused"))
        .thenReturn("response");

    assertThatThrownBy(
            () -> refreshingSut.sendRequest("request", "http://test.endpoint", String.class))
        .isInstanceOf(WebServiceIOException.class);
    final var response = refreshingSut.sendRequest("request", "http://test.endpoint", String.class);

    assertThat(response).isEqualTo("response");
    verify(connectorServicesFactoryMock, times(1)).refresh();
    assertThat(soapActionCalls).hasValue(2);
  }

  @Test
  @DisplayName("TIP1-A_4967: Ein fehlgeschlagener Refresh ersetzt den Verbindungsfehler nicht")
  void failedRefreshShouldKeepConnectionProblem() {
    final var connectorServicesFactoryMock = mock(ConnectorServicesFactory.class);
    doThrow(new IllegalStateException("Failed to load connector.sds"))
        .when(connectorServicesFactoryMock)
        .refresh();
    sut.setConnectorServicesFactory(connectorServicesFactoryMock);
    final WebServiceTemplate webServiceTemplateMock = webServiceTemplateMock(sut);
    when(webServiceTemplateMock.marshalSendAndReceive(
            eq("http://test.endpoint"), eq("request"), any(SoapActionCallback.class)))
        .thenThrow(new WebServiceIOException("Connection refused"));

    assertThatThrownBy(() -> sut.sendRequest("request", "http://test.endpoint", String.class))
        .isInstanceOf(WebServiceIOException.class)
        .hasMessage("Connection refused");
    verify(connectorServicesFactoryMock).refresh();
  }

  @Test
  @DisplayName(
      "TIP1-A_4967: Antwortet der Dienst mit einem HTTP-Fehler ohne SOAP-Fault, folgt ein"
          + " Refresh")
  void transportErrorShouldRefreshEndpoints() {
    final var connectorServicesFactoryMock = mock(ConnectorServicesFactory.class);
    sut.setConnectorServicesFactory(connectorServicesFactoryMock);
    final WebServiceTemplate webServiceTemplateMock = webServiceTemplateMock(sut);
    when(webServiceTemplateMock.marshalSendAndReceive(
            eq("http://test.endpoint"), eq("request"), any(SoapActionCallback.class)))
        .thenThrow(new WebServiceTransportException("Not Found [404]"));

    assertThatThrownBy(() -> sut.sendRequest("request", "http://test.endpoint", String.class))
        .isInstanceOf(WebServiceTransportException.class);
    verify(connectorServicesFactoryMock).refresh();
  }

  @Test
  @DisplayName("TIP1-A_4967: Ein SOAP-Fault des erreichbaren Dienstes löst keinen Refresh aus")
  void soapFaultShouldNotRefreshEndpoints() {
    final var connectorServicesFactoryMock = mock(ConnectorServicesFactory.class);
    sut.setConnectorServicesFactory(connectorServicesFactoryMock);
    final WebServiceTemplate webServiceTemplateMock = webServiceTemplateMock(sut);
    final SoapMessage faultMessageMock = mock(SoapMessage.class);
    final SoapBody faultBodyMock = mock(SoapBody.class);
    final SoapFault faultMock = mock(SoapFault.class);
    when(faultMessageMock.getSoapBody()).thenReturn(faultBodyMock);
    when(faultBodyMock.getFault()).thenReturn(faultMock);
    when(faultMock.getFaultStringOrReason()).thenReturn("Unbekannte Session ID");
    final var soapFault = new SoapFaultClientException(faultMessageMock);
    when(webServiceTemplateMock.marshalSendAndReceive(
            eq("http://test.endpoint"), eq("request"), any(SoapActionCallback.class)))
        .thenThrow(soapFault);

    assertThatThrownBy(() -> sut.sendRequest("request", "http://test.endpoint", String.class))
        .isInstanceOf(SoapFaultClientException.class);
    verify(connectorServicesFactoryMock, never()).refresh();
  }

  private WebServiceTemplate webServiceTemplateMock(final SoapClient client) {
    final WebServiceTemplate webServiceTemplateMock = mock(WebServiceTemplate.class);
    client.setWebServiceTemplate(webServiceTemplateMock);
    when(webServiceTemplateMock.getMarshaller()).thenReturn(marshallerMock);
    when(webServiceTemplateMock.getUnmarshaller()).thenReturn(marshallerMock);
    return webServiceTemplateMock;
  }
}
