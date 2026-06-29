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
import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpoint;
import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import de.gematik.ws.conn.eventservice.v7.GetCards;
import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

class GetCardsClientTest {

  private GetCardsClient sut;
  private ServiceEndpointProvider serviceEndpointProviderMock;
  private Context contextMock;
  private Jaxb2Marshaller eventServiceMarshallerMock;

  @BeforeEach
  void setUp() {
    serviceEndpointProviderMock = mock(ServiceEndpointProvider.class);
    ServiceEndpoint endpointMock = mock(ServiceEndpoint.class);
    when(serviceEndpointProviderMock.getCardServiceEndpoint()).thenReturn(endpointMock);
    when(endpointMock.getVersion()).thenReturn("7.2.0");

    eventServiceMarshallerMock = mock(Jaxb2Marshaller.class);
    contextMock = mock(Context.class);
    sut =
        new GetCardsClient(
            eventServiceMarshallerMock, contextMock, serviceEndpointProviderMock, null, "ct-id", 1);
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
  void performGetCardsReturnsEmptyListWhenNoCardsPresent() {
    // given
    final var soapResponseMock = mock(GetCardsResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getEventServiceFullEndpoint()).thenReturn("service.endpoint");
    when(soapResponseMock.getCards().getCard()).thenReturn(List.of());
    final GetCardsClient spySut = spy(sut);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(any(), anyString(), eq(GetCardsResponse.class));

    // when
    final var actualResponse = spySut.performGetCards();

    // then
    assertThat(actualResponse).isNotNull();
    assertThat(actualResponse.getCardHandles()).isEmpty();
  }

  @Test
  void performGetCardsWithMultipleCardsReturnsAllHandles() {
    // given
    final var soapResponseMock = mock(GetCardsResponse.class, RETURNS_DEEP_STUBS);
    final var cardInfoType1 = new CardInfoType();
    cardInfoType1.setCardHandle("handle1");
    final var cardInfoType2 = new CardInfoType();
    cardInfoType2.setCardHandle("handle2");
    when(serviceEndpointProviderMock.getEventServiceFullEndpoint()).thenReturn("service.endpoint");
    when(soapResponseMock.getCards().getCard()).thenReturn(List.of(cardInfoType1, cardInfoType2));
    final GetCardsClient spySut = spy(sut);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(any(), anyString(), eq(GetCardsResponse.class));

    // when
    final var actualResponse = spySut.performGetCards();

    // then
    assertThat(actualResponse.getCardHandles()).containsExactly("handle1", "handle2");
  }

  @Test
  void performGetCardsWithNonBlankCtIdSetsCtIdInRequest() {
    // given
    final var soapResponseMock = mock(GetCardsResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getEventServiceFullEndpoint()).thenReturn("service.endpoint");
    when(soapResponseMock.getCards().getCard()).thenReturn(List.of());
    final GetCardsClient spySut = spy(sut);
    final ArgumentCaptor<GetCards> requestCaptor = ArgumentCaptor.forClass(GetCards.class);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(requestCaptor.capture(), anyString(), eq(GetCardsResponse.class));

    // when
    spySut.performGetCards();

    // then
    assertThat(requestCaptor.getValue().getCtId()).isEqualTo("ct-id");
  }

  @Test
  void performGetCardsWithBlankCtIdDoesNotSetCtIdInRequest() {
    // given
    final GetCardsClient sutWithBlankCtId =
        new GetCardsClient(
            eventServiceMarshallerMock, contextMock, serviceEndpointProviderMock, null, "", 1);
    final var soapResponseMock = mock(GetCardsResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getEventServiceFullEndpoint()).thenReturn("service.endpoint");
    when(soapResponseMock.getCards().getCard()).thenReturn(List.of());
    final GetCardsClient spySut = spy(sutWithBlankCtId);
    final ArgumentCaptor<GetCards> requestCaptor = ArgumentCaptor.forClass(GetCards.class);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(requestCaptor.capture(), anyString(), eq(GetCardsResponse.class));

    // when
    spySut.performGetCards();

    // then
    assertThat(requestCaptor.getValue().getCtId()).isNull();
  }

  @Test
  void performGetCardsWithCtIdAndSlotSetsSlotIdInRequest() {
    // given
    final var soapResponseMock = mock(GetCardsResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getEventServiceFullEndpoint()).thenReturn("service.endpoint");
    when(soapResponseMock.getCards().getCard()).thenReturn(List.of());
    final GetCardsClient spySut = spy(sut);
    final ArgumentCaptor<GetCards> requestCaptor = ArgumentCaptor.forClass(GetCards.class);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(requestCaptor.capture(), anyString(), eq(GetCardsResponse.class));

    // when
    spySut.performGetCards();

    // then
    assertThat(requestCaptor.getValue().getSlotId()).isEqualTo(BigInteger.ONE);
  }

  @Test
  void performGetCardsWithBlankCtIdDoesNotSetSlotIdInRequest() {
    // given
    final GetCardsClient sutWithBlankCtId =
        new GetCardsClient(
            eventServiceMarshallerMock, contextMock, serviceEndpointProviderMock, null, "", 1);
    final var soapResponseMock = mock(GetCardsResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getEventServiceFullEndpoint()).thenReturn("service.endpoint");
    when(soapResponseMock.getCards().getCard()).thenReturn(List.of());
    final GetCardsClient spySut = spy(sutWithBlankCtId);
    final ArgumentCaptor<GetCards> requestCaptor = ArgumentCaptor.forClass(GetCards.class);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(requestCaptor.capture(), anyString(), eq(GetCardsResponse.class));

    // when
    spySut.performGetCards();

    // then
    assertThat(requestCaptor.getValue().getSlotId()).isNull();
  }

  @Test
  void performGetCardsWithCtIdAndNullSlotDoesNotSetSlotIdInRequest() {
    // given
    final GetCardsClient sutWithNullSlot =
        new GetCardsClient(
            eventServiceMarshallerMock,
            contextMock,
            serviceEndpointProviderMock,
            null,
            "ct-id",
            null);
    final var soapResponseMock = mock(GetCardsResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getEventServiceFullEndpoint()).thenReturn("service.endpoint");
    when(soapResponseMock.getCards().getCard()).thenReturn(List.of());
    final GetCardsClient spySut = spy(sutWithNullSlot);
    final ArgumentCaptor<GetCards> requestCaptor = ArgumentCaptor.forClass(GetCards.class);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(requestCaptor.capture(), anyString(), eq(GetCardsResponse.class));

    // when
    spySut.performGetCards();

    // then
    assertThat(requestCaptor.getValue().getSlotId()).isNull();
  }

  @Test
  void performGetCardsWithCtIdAndZeroSlotDoesNotSetSlotIdInRequest() {
    // given
    final GetCardsClient sutWithZeroSlot =
        new GetCardsClient(
            eventServiceMarshallerMock, contextMock, serviceEndpointProviderMock, null, "ct-id", 0);
    final var soapResponseMock = mock(GetCardsResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getEventServiceFullEndpoint()).thenReturn("service.endpoint");
    when(soapResponseMock.getCards().getCard()).thenReturn(List.of());
    final GetCardsClient spySut = spy(sutWithZeroSlot);
    final ArgumentCaptor<GetCards> requestCaptor = ArgumentCaptor.forClass(GetCards.class);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(requestCaptor.capture(), anyString(), eq(GetCardsResponse.class));

    // when
    spySut.performGetCards();

    // then
    assertThat(requestCaptor.getValue().getSlotId()).isNull();
  }

  @Test
  void performGetCardsWithCtIdAndNegativeSlotDoesNotSetSlotIdInRequest() {
    // given
    final GetCardsClient sutWithNegativeSlot =
        new GetCardsClient(
            eventServiceMarshallerMock,
            contextMock,
            serviceEndpointProviderMock,
            null,
            "ct-id",
            -1);
    final var soapResponseMock = mock(GetCardsResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getEventServiceFullEndpoint()).thenReturn("service.endpoint");
    when(soapResponseMock.getCards().getCard()).thenReturn(List.of());
    final GetCardsClient spySut = spy(sutWithNegativeSlot);
    final ArgumentCaptor<GetCards> requestCaptor = ArgumentCaptor.forClass(GetCards.class);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(requestCaptor.capture(), anyString(), eq(GetCardsResponse.class));

    // when
    spySut.performGetCards();

    // then
    assertThat(requestCaptor.getValue().getSlotId()).isNull();
  }

  @Test
  void performGetCardsSetsEgkCardTypeInRequest() {
    // given
    final var soapResponseMock = mock(GetCardsResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getEventServiceFullEndpoint()).thenReturn("service.endpoint");
    when(soapResponseMock.getCards().getCard()).thenReturn(List.of());
    final GetCardsClient spySut = spy(sut);
    final ArgumentCaptor<GetCards> requestCaptor = ArgumentCaptor.forClass(GetCards.class);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(requestCaptor.capture(), anyString(), eq(GetCardsResponse.class));

    // when
    spySut.performGetCards();

    // then
    assertThat(requestCaptor.getValue().getCardType()).isEqualTo(CardTypeType.EGK);
  }

  @Test
  void performGetCardsSetsContextFromContextConfiguration() {
    // given
    when(contextMock.getClientSystemId()).thenReturn("clientId");
    when(contextMock.getMandantId()).thenReturn("mandantId");
    when(contextMock.getWorkplaceId()).thenReturn("workplaceId");
    final GetCardsClient sutWithContext =
        new GetCardsClient(
            eventServiceMarshallerMock, contextMock, serviceEndpointProviderMock, null, "", null);
    final var soapResponseMock = mock(GetCardsResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getEventServiceFullEndpoint()).thenReturn("service.endpoint");
    when(soapResponseMock.getCards().getCard()).thenReturn(List.of());
    final GetCardsClient spySut = spy(sutWithContext);
    final ArgumentCaptor<GetCards> requestCaptor = ArgumentCaptor.forClass(GetCards.class);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(requestCaptor.capture(), anyString(), eq(GetCardsResponse.class));

    // when
    spySut.performGetCards();

    // then
    final var actualContext = requestCaptor.getValue().getContext();
    assertThat(actualContext.getClientSystemId()).isEqualTo("clientId");
    assertThat(actualContext.getMandantId()).isEqualTo("mandantId");
    assertThat(actualContext.getWorkplaceId()).isEqualTo("workplaceId");
  }

  @Test
  void performGetCardsUsesEndpointFromServiceEndpointProvider() {
    // given
    final var soapResponseMock = mock(GetCardsResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getEventServiceFullEndpoint())
        .thenReturn("https://konnektor.example/event");
    when(soapResponseMock.getCards().getCard()).thenReturn(List.of());
    final GetCardsClient spySut = spy(sut);
    final ArgumentCaptor<String> endpointCaptor = ArgumentCaptor.forClass(String.class);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(any(), endpointCaptor.capture(), eq(GetCardsResponse.class));

    // when
    spySut.performGetCards();

    // then
    assertThat(endpointCaptor.getValue()).isEqualTo("https://konnektor.example/event");
  }
}
