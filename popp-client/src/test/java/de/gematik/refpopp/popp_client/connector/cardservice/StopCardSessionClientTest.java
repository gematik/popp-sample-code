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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpoint;
import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.ws.conn.cardservice.v821.StopCardSessionResponse;
import de.gematik.ws.conn.connectorcommon.v5.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

class StopCardSessionClientTest {

  private StopCardSessionClient sut;
  private ServiceEndpointProvider serviceEndpointProviderMock;

  @BeforeEach
  void setUp() {

    serviceEndpointProviderMock = mock(ServiceEndpointProvider.class);
    ServiceEndpoint endpointMock = mock(ServiceEndpoint.class);
    when(serviceEndpointProviderMock.getCardServiceEndpoint()).thenReturn(endpointMock);
    when(endpointMock.getVersion()).thenReturn("8.1.2");

    final var jaxb2MarshallerMock = mock(Jaxb2Marshaller.class);
    sut = new StopCardSessionClient(jaxb2MarshallerMock, serviceEndpointProviderMock, null);
  }

  @Test
  void performStopCardSessionReturnsStatus() {
    // given
    final String expectedSessionId = "sessionId";
    when(serviceEndpointProviderMock.getCardServiceFullEndpoint()).thenReturn("service.endpoint");
    final var soapResponseMock = mock(StopCardSessionResponse.class);
    final Status status = new Status();
    status.setResult("OK");
    when(soapResponseMock.getStatus()).thenReturn(status);
    final StopCardSessionClient spySut = spy(sut);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(any(), eq("service.endpoint"), eq(StopCardSessionResponse.class));

    // when
    final var actualStatus = spySut.performStopCardSession(expectedSessionId);

    // then
    assertThat(actualStatus).isEqualTo(status);
  }
}
