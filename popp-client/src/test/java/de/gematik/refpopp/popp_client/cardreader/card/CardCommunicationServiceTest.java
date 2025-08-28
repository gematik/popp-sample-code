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
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.openhealth.crypto.key.SecretKey;
import de.gematik.openhealth.smartcard.ExchangeUtilsKt;
import de.gematik.openhealth.smartcard.card.HealthCardScope;
import de.gematik.openhealth.smartcard.card.HealthCardScopeKt;
import de.gematik.openhealth.smartcard.card.PaceKey;
import de.gematik.openhealth.smartcard.card.TrustedChannelScope;
import de.gematik.poppcommons.api.messages.ScenarioStep;
import de.gematik.refpopp.popp_client.cardreader.card.events.CardConnectedEvent;
import de.gematik.refpopp.popp_client.cardreader.card.events.CardRemovedEvent;
import de.gematik.refpopp.popp_client.cardreader.card.events.PaceInitializationCompleteEvent;
import java.util.HexFormat;
import java.util.List;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

class CardCommunicationServiceTest {

  @Mock private Card cardMock;
  @Mock private CardChannel cardChannelMock;
  @Mock private TrustedChannelScope trustedChannelMock;
  @Mock private ResponseAPDU responseAPDUMock;
  @Mock private HealthCardScope healthCardScopeMock;

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
  void getTrustedChannel() {
    // given // when
    cardCommunicationService.setTrustedChannel(trustedChannelMock);

    // then
    assertThat(cardCommunicationService.getTrustedChannel().isPresent()).isTrue();
  }

  @Test
  void handleCardConnectionEventContactless() throws CardException {
    // given
    final var eventPublisherMock = mock(ApplicationEventPublisher.class);
    cardCommunicationService = new CardCommunicationService(eventPublisherMock);
    cardCommunicationService.setCardChannel(cardChannelMock);

    // for testing isContactless():
    when(cardChannelMock.getCard()).thenReturn(cardMock);
    when(cardChannelMock.transmit(any(CommandAPDU.class))).thenReturn(responseAPDUMock);
    when(responseAPDUMock.getSW()).thenReturn(0x6982);

    // for testing initializePACE():
    try (final var healthCardScopeKt = mockStatic(HealthCardScopeKt.class);
        final var exchangeUtilsKt = mockStatic(ExchangeUtilsKt.class)) {
      healthCardScopeKt
          .when(() -> HealthCardScopeKt.healthCardScope(any(OpenHealthCardCommunication.class)))
          .thenReturn(healthCardScopeMock);
      exchangeUtilsKt
          .when(
              () ->
                  ExchangeUtilsKt.establishTrustedChannelBlocking(
                      any(HealthCardScope.class), anyString()))
          .thenReturn(trustedChannelMock);
      when(trustedChannelMock.getPaceKey())
          .thenReturn(
              new PaceKey(new SecretKey("abc".getBytes()), new SecretKey("xyz".getBytes())));

      final var event = new CardConnectedEvent(cardChannelMock, "message");

      // when
      cardCommunicationService.handleCardConnectionEvent(event);

      // then
      assertThat(cardCommunicationService.getTrustedChannel().isPresent()).isTrue();
      verify(eventPublisherMock).publishEvent(any(PaceInitializationCompleteEvent.class));
    }
  }
}
