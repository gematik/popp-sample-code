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
/*

package de.gematik.refpopp.popp_client.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_client.connector.ConnectorCommunicationService;
import de.gematik.refpopp.popp_client.connector.RealConnectorCommunicationService;
import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import de.gematik.ws.conn.cardservice.v8.Cards;
import de.gematik.ws.conn.cardservice.wsdl.v8.CardService;
import de.gematik.ws.conn.cardservice.wsdl.v8.CardServicePortType;
import de.gematik.ws.conn.cardservice.wsdl.v8.FaultMessage;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import de.gematik.ws.conn.connectorcommon.v5.Status;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.v7.GetCards;
import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import de.gematik.ws.conn.eventservice.wsdl.v7.EventService;
import de.gematik.ws.conn.eventservice.wsdl.v7.EventServicePortType;
import java.math.BigInteger;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.Assert;

public class ConnectorCommunicationServiceTest {

  private ConnectorCommunicationService sut;
  private EventService eventServiceMock;
  private EventServicePortType eventServicePortTypeMock;

  private CardService cardServiceMock;
  private CardServicePortType cardServicePortTypeMock;

  String clientSystemId = "Client-System";
  String workplaceId = "Arbeitsplatz_01";
  String principalId = "Client007";

  String expectedCardHandle = "58366430-60ad-417b-ac8c-2a99467c6cf6";
  String sessionId = "1584a1b3-773f-485a-bd09-2f5fa7c64dbd";

  Status stopCardSessionStatusSuccess;
  Status stopCardSessionStatusFailure;

  GetCardsResponse getCardsResponse;
  GetCardsResponse getCardsResponseEmptyList;
  GetCards cardsToFetch;
  CardInfoType cardInfoType;
  Cards cards;
  Cards cardsEmptyList;
  ContextType context;

  @SneakyThrows
  @BeforeEach
  void setUp() {

    stopCardSessionStatusSuccess = new Status();
    stopCardSessionStatusSuccess.setResult("OK");

    stopCardSessionStatusFailure = new Status();
    stopCardSessionStatusFailure.setResult("ERROR");

    eventServiceMock = mock(EventService.class);
    eventServicePortTypeMock = mock(EventServicePortType.class);

    cardServiceMock = mock(CardService.class);
    cardServicePortTypeMock = mock(CardServicePortType.class);

    sut =
        new RealConnectorCommunicationService(eventServiceMock, cardServiceMock);

    context = new ContextType();
    context.setClientSystemId(clientSystemId);
    context.setWorkplaceId(workplaceId);
    context.setMandantId(principalId);

    cardsToFetch = new GetCards();
    cardsToFetch.setCardType(CardTypeType.EGK);
    cardsToFetch.setContext(context);

    cards = new Cards();
    cardsEmptyList = new Cards();

    cardInfoType = new CardInfoType();
    cardInfoType.setCardHandle(expectedCardHandle);
    cardInfoType.setCtId("Terminal-ID-1");
    cardInfoType.setKvnr("X110458650");
    cardInfoType.setCardHolderName("Annamaria Dominique-Michelle Øz");
    cardInfoType.setCardType(CardTypeType.EGK);
    cardInfoType.setSlotId(BigInteger.ONE);

    cards.getCard().add(cardInfoType);

    getCardsResponse = new GetCardsResponse();
    getCardsResponse.setStatus(new Status());
    getCardsResponse.getStatus().setResult("OK");
    getCardsResponse.setCards(cards);

    getCardsResponseEmptyList = new GetCardsResponse();
    getCardsResponseEmptyList.setStatus(new Status());
    getCardsResponseEmptyList.getStatus().setResult("OK");
    getCardsResponseEmptyList.setCards(cardsEmptyList);

    when(eventServiceMock.getEventServicePort()).thenReturn(eventServicePortTypeMock);
    when(eventServicePortTypeMock.getCards(any())).thenReturn(getCardsResponse);
    when(cardServiceMock.getCardServicePort()).thenReturn(cardServicePortTypeMock);
  }

  @SneakyThrows
  @Test
  public void testStopCardConnectionWithInvalidSessionId() {
    when(cardServicePortTypeMock.stopCardSession(argThat(id -> !id.equals(sessionId))))
        .thenReturn(stopCardSessionStatusFailure);

    assertDoesNotThrow(
        () -> {
          var result = sut.stopCardSession("SomeInvalidSessionId");
          assertTrue(result.isPresent());
          assertEquals("ERROR", result.get().getResult());
        });
  }

  @SneakyThrows
  @Test
  public void testStopCardConnectionWithException() {
    when(cardServicePortTypeMock.stopCardSession(any())).thenThrow(FaultMessage.class);

    assertThrows(FaultMessage.class, () -> sut.stopCardSession("SomeInvalidSessionId"));
  }

  @SneakyThrows
  @Test
  public void testStopCardConnectionWithValidSessionId() {
    when(cardServicePortTypeMock.stopCardSession(sessionId))
        .thenReturn(stopCardSessionStatusSuccess);

    assertDoesNotThrow(
        () -> {
          var result = sut.stopCardSession(sessionId);
          assertTrue(result.isPresent());
          assertEquals("OK", result.get().getResult());
        });
  }

  @SneakyThrows
  @Test
  public void testStartCardConnectionWithEmptyResult() {
    when(cardServicePortTypeMock.startCardSession(any(), eq(expectedCardHandle)))
        .thenReturn(sessionId);

    assertDoesNotThrow(
        () -> {
          var uuidSessionId = sut.startCardConnection(context, "InvalidCardHandle");
          assertTrue(uuidSessionId.isEmpty());
        });
  }

  @Test
  public void testStartCardConnectionWithInvalidCardHandle() {
    assertDoesNotThrow(
        () -> {
          var sessionId = sut.startCardConnection(new ContextType(), "Invalid-Card-Handle");
        });
  }

  @Test
  public void testEvaluateCardResponse() {
    final var result = sut.evaluateCardResponse(getCardsResponse);
    assertNotNull(result);
    assertTrue(result.isPresent());
    assertEquals(expectedCardHandle, result.get());
  }

  @Test
  public void testEvaluateCardResponseWithEmptyList() {
    final var result = sut.evaluateCardResponse(getCardsResponseEmptyList);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  public void testConnectorServiceGetEGKCards() {
    assertDoesNotThrow(
        () -> {
          final var result = sut.getConnectedEGKCard(principalId, clientSystemId, workplaceId);
          assertTrue(result.isPresent(), "Expected card info.");
          assertEquals(expectedCardHandle, result.get());
        });
  }

  @SneakyThrows
  @Test
  public void testConnectorServiceGetEGKCardsThrowsException() {
    when(eventServicePortTypeMock.getCards(any()))
        .thenThrow(de.gematik.ws.conn.eventservice.wsdl.v7.FaultMessage.class);
    assertThrows(
        de.gematik.ws.conn.cardservice.wsdl.v8.FaultMessage.class,
        () -> {
          sut.getConnectedEGKCard(principalId, clientSystemId, workplaceId);
        });
  }

  @Test
  public void testGetCardsOnEventServicePort() {

    assertDoesNotThrow(
        () -> {
          final var result = eventServicePortTypeMock.getCards(cardsToFetch);

          assertNotNull(result, "getCards returned null");
          assertEquals(1, result.getCards().getCard().size());
          assertEquals(expectedCardHandle, result.getCards().getCard().getFirst().getCardHandle());
        });
  }

  @Test
  public void testConnectedEKGCards() {

    final ContextType localContext = new ContextType();
    localContext.setClientSystemId(clientSystemId);
    localContext.setWorkplaceId(workplaceId);
    localContext.setMandantId(principalId);

    final var cf = new GetCards();
    cf.setCardType(CardTypeType.EGK);
    cf.setContext(localContext);

    final var eventServicePort = eventServiceMock.getEventServicePort();

    assertDoesNotThrow(
        () -> {
          final var rs = eventServicePort.getCards(cf);
          Assert.notNull(rs, "GetCards returned null");
          assertFalse(rs.getCards().getCard().isEmpty());

          final var localCardHandle =
              Optional.of(rs.getCards().getCard().getFirst().getCardHandle());

          assertEquals(expectedCardHandle, localCardHandle.get());
        });
  }
}
*/
