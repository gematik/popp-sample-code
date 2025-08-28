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

package de.gematik.refpopp.popp_client.connector.eventservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_client.connector.Context;
import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

class GetCardsClientTest {

  private GetCardsClient sut;
  private ServiceEndpointProvider serviceEndpointProviderMock;

  @BeforeEach
  void setUp() {
    serviceEndpointProviderMock = mock(ServiceEndpointProvider.class);
    final Jaxb2Marshaller eventServiceMarshallerMock = mock(Jaxb2Marshaller.class);
    final Context contextMock = mock(Context.class);
    sut =
        new GetCardsClient(
            eventServiceMarshallerMock,
            contextMock,
            serviceEndpointProviderMock,
            "http://tempuri.org/GetCards",
            null);
  }

  @Test
  void performGetCardsAndCheckCardHandleExists() {
    // given
    final var expectedCardHandles = List.of("cardHandle1");
    final var soapResponseMock = mock(GetCardsResponse.class, RETURNS_DEEP_STUBS);
    final var cardInfoType = new CardInfoType();
    cardInfoType.setCardType(CardTypeType.EGK);
    cardInfoType.setCardHandle("cardHandle1");
    when(serviceEndpointProviderMock.getEventServiceFullEndpoint()).thenReturn("service.endpoint");
    when(soapResponseMock.getCards().getCard()).thenReturn(List.of(cardInfoType));
    final GetCardsClient spySut = spy(sut);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(any(), anyString(), eq(GetCardsResponse.class));

    // when
    final var actualResponse = spySut.performGetCards();

    // then
    assertThat(actualResponse).isNotNull();
    assertThat(actualResponse.getCardHandles()).containsExactly(expectedCardHandles.getFirst());
    verify(serviceEndpointProviderMock).getEventServiceFullEndpoint();
  }

  @Test
  void performGetCardsWithTwoCardsAndCheckOnlyEgkIsReturned() {
    // given
    final var expectedCardHandles = List.of("egkCardHandle");
    final var soapResponseMock = mock(GetCardsResponse.class, RETURNS_DEEP_STUBS);
    final var cardInfoType1 = new CardInfoType();
    cardInfoType1.setCardType(CardTypeType.SMC_KT);
    cardInfoType1.setCardHandle("smcKtCardHandle");
    final var cardInfoType2 = new CardInfoType();
    cardInfoType2.setCardType(CardTypeType.EGK);
    cardInfoType2.setCardHandle("egkCardHandle");
    when(serviceEndpointProviderMock.getEventServiceFullEndpoint()).thenReturn("service.endpoint");
    when(soapResponseMock.getCards().getCard()).thenReturn(List.of(cardInfoType1, cardInfoType2));
    final GetCardsClient spySut = spy(sut);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(any(), anyString(), eq(GetCardsResponse.class));

    // when
    final var actualResponse = spySut.performGetCards();

    // then
    assertThat(actualResponse).isNotNull();
    assertThat(actualResponse.getCardHandles()).containsExactly(expectedCardHandles.getFirst());
    verify(serviceEndpointProviderMock).getEventServiceFullEndpoint();
  }
}
