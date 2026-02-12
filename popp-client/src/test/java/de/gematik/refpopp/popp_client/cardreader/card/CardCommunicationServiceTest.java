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

package de.gematik.refpopp.popp_client.cardreader.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.gematik.openhealth.healthcard.CardAccessNumber;
import de.gematik.openhealth.healthcard.CommandApdu;
import de.gematik.openhealth.healthcard.NoHandle;
import de.gematik.openhealth.healthcard.ResponseApdu;
import de.gematik.openhealth.healthcard.SecureChannel;
import de.gematik.openhealth.healthcard.SecureChannelException;
import de.gematik.openhealth.healthcard.SecureChannelException.Transport;
import de.gematik.poppcommons.api.messages.ScenarioStep;
import de.gematik.refpopp.popp_client.cardreader.card.events.CardConnectedEvent;
import de.gematik.refpopp.popp_client.cardreader.card.events.CardRemovedEvent;
import de.gematik.refpopp.popp_client.cardreader.card.events.PaceInitializationCompleteEvent;
import java.util.HexFormat;
import java.util.List;
import javax.smartcardio.*;
import kotlin.jvm.internal.DefaultConstructorMarker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

class CardCommunicationServiceTest {

  @Mock private Card cardMock;
  @Mock private CardChannel cardChannelMock;
  @Mock private SecureChannel secureChannelMock;
  @Mock private CardAccessNumber cardAccessNumberMock;
  @Mock private ResponseAPDU responseAPDUMock;

  private AutoCloseable closeable;

  private CardCommunicationService cardCommunicationService;

  @BeforeEach
  void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
    final var eventPublisherMock = mock(ApplicationEventPublisher.class);
    cardCommunicationService = new CardCommunicationService(eventPublisherMock);
    cardCommunicationService.setCardChannel(cardChannelMock);
  }

  @AfterEach
  void close() throws Exception {
    closeable.close();
  }

  @Test
  void handleCardConnectionEventSetsInsertedCard() throws CardException {
    // given
    final var event = new CardConnectedEvent(cardChannelMock, "message");

    when(cardChannelMock.getCard()).thenReturn(cardMock);
    when(cardChannelMock.transmit(any(CommandAPDU.class))).thenReturn(responseAPDUMock);
    when(responseAPDUMock.getSW()).thenReturn(0x9000);

    // when
    cardCommunicationService.handleCardConnectionEvent(event);

    // then
    assertThat(cardCommunicationService.getCardChannel().isPresent()).isTrue();
  }

  @Test
  void handleCardRemovedEventClearsInsertedCard() {
    // given

    // when
    cardCommunicationService.handleCardRemovedEvent(new CardRemovedEvent(cardMock, "message"));

    // then
    assertThat(cardCommunicationService.getCardChannel().isPresent()).isFalse();
  }

  @Test
  void handleCardRemovedEventClearsSecureChannel() {
    cardCommunicationService.setSecureChannel(secureChannelMock);

    cardCommunicationService.handleCardRemovedEvent(new CardRemovedEvent(cardMock, "message"));

    assertThat(cardCommunicationService.getSecureChannel()).isEmpty();
  }

  @Test
  void processReturnsResponseApdu() throws Exception {
    // given
    final var scenarioStep = new ScenarioStep("00A4040000", List.of("9000"));
    cardCommunicationService.setCardChannel(cardChannelMock);
    when(cardChannelMock.transmit(any(CommandAPDU.class))).thenReturn(responseAPDUMock);
    when(responseAPDUMock.getBytes()).thenReturn(new byte[] {0x04, 0x00});
    when(responseAPDUMock.getSW()).thenReturn(36864);
    when(responseAPDUMock.getData()).thenReturn(new byte[] {0x04, 0x00});

    // when
    final var statusWordAndData = cardCommunicationService.process(scenarioStep);

    // then
    assertThat(statusWordAndData).isEqualTo(HexFormat.of().formatHex(new byte[] {0x04, 0x00}));
    verify(cardChannelMock).transmit(any(CommandAPDU.class));
  }

  @Test
  void processThrowsExceptionWhenStatusWordIsNotExpected() throws CardException {
    // given
    final var scenarioStep = new ScenarioStep("00A4040000", List.of("9000"));
    cardCommunicationService.setCardChannel(cardChannelMock);
    when(cardChannelMock.transmit(any(CommandAPDU.class))).thenReturn(responseAPDUMock);
    when(responseAPDUMock.getData()).thenReturn(new byte[] {0x04, 0x00});
    when(responseAPDUMock.getBytes()).thenReturn(new byte[] {0x04, 0x00});
    when(responseAPDUMock.getSW()).thenReturn(12345);

    // when // then
    assertThrows(IllegalStateException.class, () -> cardCommunicationService.process(scenarioStep));
  }

  @Test
  void processReturnsListOfResponseApdu() throws CardException {
    // given
    final var scenarioStep1 = new ScenarioStep("00A4040000", List.of("9000"));
    final var scenarioStep2 = new ScenarioStep("00B2010C00", List.of("9000"));
    final var scenarioSteps = List.of(scenarioStep1, scenarioStep2);
    cardCommunicationService.setCardChannel(cardChannelMock);
    when(cardChannelMock.transmit(any(CommandAPDU.class))).thenReturn(responseAPDUMock);
    when(responseAPDUMock.getData()).thenReturn(new byte[] {0x04, 0x00});
    when(responseAPDUMock.getBytes()).thenReturn(new byte[] {0x04, 0x00});
    when(responseAPDUMock.getSW()).thenReturn(36864);

    // when
    final var statusWordAndData = cardCommunicationService.process(scenarioSteps);

    // then
    assertThat(statusWordAndData).containsExactly("0400", "0400");
    verify(cardChannelMock, times(2)).transmit(any(CommandAPDU.class));
  }

  @Test
  void processUsesSecureChannelWhenAvailable() throws Exception {
    final var scenarioStep = new ScenarioStep("00 A4 04 00 00", List.of("9000"));
    final var responseApduMock = mock(ResponseApdu.class);
    cardCommunicationService.setSecureChannel(secureChannelMock);
    when(secureChannelMock.transmit(any(CommandApdu.class))).thenReturn(responseApduMock);
    when(responseApduMock.toBytes()).thenReturn(new byte[] {(byte) 0x90, 0x00});

    final var statusWordAndData = cardCommunicationService.process(scenarioStep);

    assertThat(statusWordAndData).isEqualTo("9000");
    verify(cardChannelMock, never()).transmit(any(CommandAPDU.class));
  }

  @Test
  void processThrowsWhenSecureChannelFails() throws Exception {
    final var scenarioStep = new ScenarioStep("00A4040000", List.of("9000"));
    cardCommunicationService.setSecureChannel(secureChannelMock);
    when(secureChannelMock.transmit(any(CommandApdu.class)))
        .thenThrow(newTransportException("secure channel error"));

    assertThrows(IllegalStateException.class, () -> cardCommunicationService.process(scenarioStep));
  }

  @Test
  void throwsExceptionWhenNoCardInserted() {
    // given
    final var scenarioStep = new ScenarioStep("00A4040000", List.of("9000"));
    cardCommunicationService.setCardChannel(null);

    // when
    final var exception =
        assertThrows(
            IllegalStateException.class, () -> cardCommunicationService.process(scenarioStep));

    // then
    assertThat(exception.getMessage()).isEqualTo("| No card inserted");
  }

  @Test
  void startThrowsExceptionWhenSendingAPDUs() throws CardException {
    // given
    final var scenarioStep = new ScenarioStep("00A4040000", List.of("9000"));
    cardCommunicationService.setCardChannel(cardChannelMock);
    when(cardChannelMock.transmit(any(CommandAPDU.class)))
        .thenThrow(new CardException("No card inserted"));

    // when // then
    assertThrows(IllegalStateException.class, () -> cardCommunicationService.process(scenarioStep));
  }

  @Test
  void getSecureChannel() {
    // given // when
    cardCommunicationService.setSecureChannel(secureChannelMock);

    // then
    assertThat(cardCommunicationService.getSecureChannel().isPresent()).isTrue();
  }

  @Test
  void isContactlessReturnsFalseWhenTransmitReturnsNull() throws CardException {
    when(cardChannelMock.getCard()).thenReturn(cardMock);
    when(cardChannelMock.transmit(any(CommandAPDU.class))).thenReturn(null);

    assertThat(cardCommunicationService.isContactless()).isFalse();
  }

  @Test
  void initializePACEWrapsSecureChannelException() throws SecureChannelException {
    final var eventPublisherMock = mock(ApplicationEventPublisher.class);
    cardCommunicationService = spy(new CardCommunicationService(eventPublisherMock));
    doThrow(newTransportException("bad can"))
        .when(cardCommunicationService)
        .createCardAccessNumberFromDigits(any());

    assertThrows(IllegalStateException.class, () -> cardCommunicationService.initializePACE());
  }

  @Test
  void handleCardConnectionEventContactless() throws CardException, SecureChannelException {
    // given
    final var eventPublisherMock = mock(ApplicationEventPublisher.class);
    cardCommunicationService = spy(new CardCommunicationService(eventPublisherMock));
    cardCommunicationService.setCardChannel(cardChannelMock);

    // for testing isContactless():
    when(cardChannelMock.getCard()).thenReturn(cardMock);
    when(cardChannelMock.transmit(any(CommandAPDU.class))).thenReturn(responseAPDUMock);
    when(responseAPDUMock.getSW()).thenReturn(0x6982);

    doReturn(cardAccessNumberMock)
        .when(cardCommunicationService)
        .createCardAccessNumberFromDigits(any());
    doReturn(secureChannelMock)
        .when(cardCommunicationService)
        .doEstablishSecureChannel(any(), eq(cardAccessNumberMock));

    // when
    cardCommunicationService.handleCardConnectionEvent(
        new CardConnectedEvent(cardChannelMock, "message"));

    // then
    assertThat(cardCommunicationService.getSecureChannel()).contains(secureChannelMock);
    verify(eventPublisherMock).publishEvent(any(PaceInitializationCompleteEvent.class));
    verify(cardCommunicationService).createCardAccessNumber();
    verify(cardCommunicationService).establishSecureChannel(any(), eq(cardAccessNumberMock));
  }

  @Test
  void handleCardConnectionEventContactBasedDoesNotInitializePace()
      throws CardException, SecureChannelException {
    // given
    final var eventPublisherMock = mock(ApplicationEventPublisher.class);
    cardCommunicationService = spy(new CardCommunicationService(eventPublisherMock));
    cardCommunicationService.setCardChannel(cardChannelMock);

    // for testing isContactless():
    when(cardChannelMock.getCard()).thenReturn(cardMock);
    when(cardChannelMock.transmit(any(CommandAPDU.class))).thenReturn(responseAPDUMock);
    when(responseAPDUMock.getSW()).thenReturn(0x9000);

    // when
    cardCommunicationService.handleCardConnectionEvent(
        new CardConnectedEvent(cardChannelMock, "message"));

    // then
    assertThat(cardCommunicationService.getSecureChannel()).isEmpty();
    verify(eventPublisherMock, never()).publishEvent(any(PaceInitializationCompleteEvent.class));
    verify(cardCommunicationService, never()).createCardAccessNumber();
    verify(cardCommunicationService, never()).establishSecureChannel(any(), any());
  }

  @Test
  void initializePACEUsesInternalChannelAndSecureChannelSetup() throws Exception {
    final var eventPublisherMock = mock(ApplicationEventPublisher.class);
    final var responseApduMock = mock(ResponseApdu.class);
    final var service =
        new CardCommunicationService(eventPublisherMock) {
          @Override
          protected CardAccessNumber createCardAccessNumberFromDigits(final String digits) {
            return new CardAccessNumber(NoHandle.INSTANCE);
          }

          @Override
          protected SecureChannel doEstablishSecureChannel(
              final de.gematik.openhealth.healthcard.CardChannel cardChannel,
              final CardAccessNumber cardAccessNumber) {
            cardChannel.transmit(new CommandApdu(NoHandle.INSTANCE));
            return secureChannelMock;
          }

          @Override
          protected byte[] commandApduToBytes(final CommandApdu commandApdu) {
            return new byte[] {0x00, (byte) 0xA4, 0x00, 0x00};
          }

          @Override
          protected ResponseApdu responseApduFromBytes(final byte[] bytes) {
            return responseApduMock;
          }
        };

    service.setCardChannel(cardChannelMock);
    when(cardChannelMock.transmit(any(CommandAPDU.class))).thenReturn(responseAPDUMock);
    when(responseAPDUMock.getBytes()).thenReturn(new byte[] {(byte) 0x90, 0x00});

    service.initializePACE();

    assertThat(service.getSecureChannel()).contains(secureChannelMock);
    verify(cardChannelMock).transmit(any(CommandAPDU.class));
  }

  private SecureChannelException newTransportException(final String message) {
    try {
      final var ctor =
          Transport.class.getDeclaredConstructor(
              int.class, String.class, DefaultConstructorMarker.class);
      ctor.setAccessible(true);
      return ctor.newInstance(1, message, null);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
