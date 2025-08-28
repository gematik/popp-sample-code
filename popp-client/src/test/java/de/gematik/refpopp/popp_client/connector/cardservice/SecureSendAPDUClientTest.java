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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.ws.conn.cardservice.v821.SecureSendAPDUResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

class SecureSendAPDUClientTest {

  private SecureSendAPDUClient sut;
  private ServiceEndpointProvider serviceEndpointProviderMock;

  @BeforeEach
  void setUp() {
    final var jaxb2MarshallerMock = mock(Jaxb2Marshaller.class);
    serviceEndpointProviderMock = mock(ServiceEndpointProvider.class);
    sut =
        new SecureSendAPDUClient(
            jaxb2MarshallerMock,
            serviceEndpointProviderMock,
            "http://tempuri.org/SecureSendAPDU",
            null);
  }

  @Test
  void performSecureSendAPDUReturnsAPDUs() {
    // given
    when(serviceEndpointProviderMock.getCardServiceFullEndpoint()).thenReturn("service.endpoint");

    final SecureSendAPDUResponse responseMock =
        mock(SecureSendAPDUResponse.class, RETURNS_DEEP_STUBS);
    final List<String> expectedApdus = List.of("APDU1", "APDU2");

    when(responseMock.getSignedScenarioResponse().getResponseApduList().getResponseApdu())
        .thenReturn(expectedApdus);

    final SecureSendAPDUClient spySut = spy(sut);
    doReturn(responseMock)
        .when(spySut)
        .sendRequest(any(), eq("service.endpoint"), eq(SecureSendAPDUResponse.class));

    // when
    final List<String> actualApdus = spySut.performSecureSendAPDU("signedScenario");

    // then
    assertThat(actualApdus).isNotNull().isEqualTo(expectedApdus);
  }
}
