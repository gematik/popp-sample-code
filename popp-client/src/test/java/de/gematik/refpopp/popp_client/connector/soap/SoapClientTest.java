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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
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
}
