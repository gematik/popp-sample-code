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

package de.gematik.refpopp.popp_client.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_client.connector.cardservice.SecureSendAPDUClient;
import de.gematik.refpopp.popp_client.connector.cardservice.StartCardSessionClient;
import de.gematik.refpopp.popp_client.connector.cardservice.StopCardSessionClient;
import de.gematik.refpopp.popp_client.connector.eventservice.DetermineCardHandleResponse;
import de.gematik.refpopp.popp_client.connector.eventservice.GetCardsClient;
import de.gematik.ws.conn.connectorcommon.v5.Status;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RealConnectorCommunicationServiceTest {

  private RealConnectorCommunicationService sut;
  private GetCardsClient getCardsClientMock;
  private StartCardSessionClient startCardSessionClientMock;
  private StopCardSessionClient stopCardSessionClientMock;
  private SecureSendAPDUClient secureSendAPDUClientMock;

  @BeforeEach
  void setUp() {
    getCardsClientMock = mock(GetCardsClient.class);
    startCardSessionClientMock = mock(StartCardSessionClient.class);
    stopCardSessionClientMock = mock(StopCardSessionClient.class);
    secureSendAPDUClientMock = mock(SecureSendAPDUClient.class);
    sut =
        new RealConnectorCommunicationService(
            getCardsClientMock,
            startCardSessionClientMock,
            stopCardSessionClientMock,
            secureSendAPDUClientMock);
  }

  @Test
  void getConnectedEgkCardReturnsHandle() {
    // given
    final var determineCardHandleResponse = new DetermineCardHandleResponse();
    determineCardHandleResponse.setCardHandles(List.of("1234567890"));
    when(getCardsClientMock.performGetCards()).thenReturn(determineCardHandleResponse);

    // when
    final var cardHandle = sut.getConnectedEgkCard();

    // then
    assertThat(cardHandle).isNotNull().isEqualTo("1234567890");
  }

  @Test
  void getConnectedEgkCardThrowsExceptionWhenNoCardHandle() {
    // given
    final var determineCardHandleResponse = new DetermineCardHandleResponse();
    determineCardHandleResponse.setCardHandles(List.of());
    when(getCardsClientMock.performGetCards()).thenReturn(determineCardHandleResponse);

    // when / then
    assertThatThrownBy(() -> sut.getConnectedEgkCard())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Error fetching EGK card response");
  }

  @Test
  void startCardSessionReturnsSessionId() {
    // given
    final var cardHandle = "1234567890";
    final var sessionId = "sessionId";
    when(startCardSessionClientMock.performStartCardSession(cardHandle)).thenReturn(sessionId);

    // when
    final var result = sut.startCardSession(cardHandle);

    // then
    assertThat(result).isEqualTo(sessionId);
    verify(startCardSessionClientMock).performStartCardSession(cardHandle);
  }

  @Test
  void stopCardSessionReturnsStatus() {
    // given
    final var sessionId = "sessionId";
    final var status = mock(Status.class);
    when(stopCardSessionClientMock.performStopCardSession(sessionId)).thenReturn(status);

    // when
    final var result = sut.stopCardSession(sessionId);

    // then
    assertThat(result).isEqualTo(status);
    verify(stopCardSessionClientMock).performStopCardSession(sessionId);
  }

  @Test
  void secureSendAPDUReturnsAPDUs() {
    // given
    final var signedScenario = "signedScenario";
    final var apdus = List.of("APDU1", "APDU2");
    when(secureSendAPDUClientMock.performSecureSendAPDU(signedScenario)).thenReturn(apdus);

    // when
    final var result = sut.secureSendApdu(signedScenario);

    // then
    assertThat(result).isEqualTo(apdus);
    verify(secureSendAPDUClientMock).performSecureSendAPDU(signedScenario);
  }
}
