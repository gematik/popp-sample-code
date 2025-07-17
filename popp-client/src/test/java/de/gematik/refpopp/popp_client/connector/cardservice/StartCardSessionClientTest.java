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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpoint;
import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.ws.conn.cardservice.v8.StartCardSessionResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

class StartCardSessionClientTest {

  private StartCardSessionClient sut;
  private ServiceEndpointProvider serviceEndpointProviderMock;

  @BeforeEach
  void setUp() {
    final var jaxb2MarshallerMock = mock(Jaxb2Marshaller.class);
    serviceEndpointProviderMock = mock(ServiceEndpointProvider.class);
    sut =
        new StartCardSessionClient(
            jaxb2MarshallerMock,
            serviceEndpointProviderMock,
            "http://tempuri.org/SecureSendAPDU",
            false,
            Optional.empty());
  }

  @Test
  void performStartCardSessionReturnsSessionId() {
    // given
    final String expectedSessionId = "sessionId";
    final String handle = "handle";
    final var serviceEndpointMock = mock(ServiceEndpoint.class);
    when(serviceEndpointProviderMock.getCardServiceEndpoint()).thenReturn(serviceEndpointMock);
    when(serviceEndpointMock.getEndpoint()).thenReturn("service.endpoint");
    final var soapResponseMock = mock(StartCardSessionResponse.class);
    when(soapResponseMock.getSessionId()).thenReturn(expectedSessionId);
    final StartCardSessionClient spySut = spy(sut);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(any(), eq("service.endpoint"), eq(StartCardSessionResponse.class));

    // when
    final var actualSessionId = spySut.performStartCardSession(handle);

    // then
    assertNotNull(actualSessionId);
    assertEquals(expectedSessionId, actualSessionId);
  }
}
