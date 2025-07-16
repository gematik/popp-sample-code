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

package de.gematik.refpopp.popp_client.cardreader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_client.cardreader.card.CardEventPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.TerminalFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReaderAndCardMonitoringTest {

  private ReaderAndCardMonitoring sut;
  private TerminalFactory terminalFactoryMock;
  private CardEventPublisher cardEventPublisherMock;

  @BeforeEach
  void setUp() {
    terminalFactoryMock = mock(TerminalFactory.class);
    cardEventPublisherMock = mock(CardEventPublisher.class);

    sut = new ReaderAndCardMonitoring(terminalFactoryMock, cardEventPublisherMock);
  }

  @Test
  void shouldStartMonitoring() {
    // given
    final var cardExecutorMock = mock(ScheduledExecutorService.class);
    final var cardTerminalMock = mock(CardTerminal.class);
    final var runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

    // when
    sut.startMonitoring(cardExecutorMock, cardTerminalMock);

    // then
    verify(cardExecutorMock)
        .scheduleAtFixedRate(runnableCaptor.capture(), eq(0L), eq(1L), eq(TimeUnit.SECONDS));
    final var task = runnableCaptor.getValue();
    assertNotNull(task);
    task.run();
  }

  @Test
  void shouldMonitorTerminalStatusAndGetConnected() throws CardException {
    // given
    final var cardTerminalsMock = mock(CardTerminals.class);
    final var cardTerminalMock = mock(CardTerminal.class);
    final var cardTerminalsList = new ArrayList<CardTerminal>();
    cardTerminalsList.add(cardTerminalMock);
    sut.setLastTerminalPresent(false);
    when(terminalFactoryMock.terminals()).thenReturn(cardTerminalsMock);
    when(cardTerminalsMock.list()).thenReturn(cardTerminalsList);

    // when
    sut.monitorTerminalStatus();

    // then
    verify(cardTerminalsMock).list();
    assertThat(sut.getTerminal()).isEqualTo(cardTerminalMock);
    assertThat(sut.isLastTerminalPresent()).isTrue();
    verify(cardEventPublisherMock)
        .publishReaderConnectedEvent(cardTerminalMock, "Card reader connected");
  }

  @Test
  void shouldMonitorTerminalStatusAndGetDisconnected() throws CardException {
    // given
    final var cardTerminalsMock = mock(CardTerminals.class);
    sut.setLastTerminalPresent(true);
    when(terminalFactoryMock.terminals()).thenReturn(cardTerminalsMock);
    when(cardTerminalsMock.list()).thenReturn(List.of());

    // when
    sut.monitorTerminalStatus();

    // then
    verify(cardTerminalsMock).list();
    assertThat(sut.getTerminal()).isNull();
    assertThat(sut.getCard()).isNull();
    assertThat(sut.getChannel()).isNull();
    verify(cardEventPublisherMock).publishReaderRemovedEvent("Card reader was removed");
  }

  @Test
  void shouldMonitorTerminalStatusAndGetCardDisconnected() throws CardException {
    // given
    final var cardTerminalsMock = mock(CardTerminals.class);
    sut.setLastTerminalPresent(true);
    final var cardMock = mock(Card.class);
    sut.setCard(cardMock);
    when(terminalFactoryMock.terminals()).thenReturn(cardTerminalsMock);
    when(cardTerminalsMock.list()).thenReturn(List.of());
    when(terminalFactoryMock.terminals()).thenReturn(cardTerminalsMock);
    when(cardTerminalsMock.list()).thenReturn(List.of());

    // when
    sut.monitorTerminalStatus();

    // then
    verify(cardTerminalsMock).list();
    verify(cardMock).disconnect(false);
    verify(cardEventPublisherMock).publishCardRemovedEvent("Card was disconnected");
    assertThat(sut.getTerminal()).isNull();
    assertThat(sut.getCard()).isNull();
    assertThat(sut.getChannel()).isNull();
    verify(cardEventPublisherMock).publishReaderRemovedEvent("Card reader was removed");
  }

  @Test
  void shouldHandleTerminalConnected() throws CardException {
    // given
    final var cardTerminalMock = mock(CardTerminal.class);

    // when
    sut.handleTerminalConnected(cardTerminalMock);

    // then
    verify(cardEventPublisherMock)
        .publishReaderConnectedEvent(cardTerminalMock, "Card reader connected");
    assertThat(sut.getTerminal()).isEqualTo(cardTerminalMock);
  }

  @Test
  void shouldHandleTerminalDisconnected() {
    // given
    sut.setCard(mock(Card.class));

    // when
    sut.handleTerminalDisconnected();

    // then
    verify(cardEventPublisherMock).publishReaderRemovedEvent("Card reader was removed");
    verify(cardEventPublisherMock).publishCardRemovedEvent("Card was disconnected");
    assertThat(sut.getTerminal()).isNull();
    assertThat(sut.getCard()).isNull();
    assertThat(sut.getChannel()).isNull();
  }

  @Test
  void shouldMonitorCardStatusAndHandleCardInserted() throws CardException {
    // given
    final var cardTerminalMock = mock(CardTerminal.class);
    final var cardMock = mock(Card.class);
    final var cardChannelMock = mock(CardChannel.class);
    sut.setTerminal(cardTerminalMock);
    sut.setLastTerminalPresent(true);
    sut.setLastCardPresent(false);
    when(cardTerminalMock.isCardPresent()).thenReturn(true);
    when(cardTerminalMock.connect("T=1")).thenReturn(cardMock);
    when(cardMock.getBasicChannel()).thenReturn(cardChannelMock);

    // when
    sut.monitorCardStatus();

    // then
    verify(cardTerminalMock).connect("T=1");
    verify(cardEventPublisherMock).publishCardConnectedEvent(cardChannelMock);
    assertThat(sut.getCard()).isEqualTo(cardMock);
    assertThat(sut.getChannel()).isEqualTo(cardChannelMock);
  }

  @Test
  void shouldMonitorCardStatusAndHandleCardRemoved() throws CardException {
    // given
    final var cardTerminalMock = mock(CardTerminal.class);
    sut.setTerminal(cardTerminalMock);
    sut.setLastTerminalPresent(true);
    sut.setLastCardPresent(true);
    final var cardMock = mock(Card.class);
    sut.setCard(cardMock);
    when(cardTerminalMock.isCardPresent()).thenReturn(false);

    // when
    sut.monitorCardStatus();

    // then
    verify(cardEventPublisherMock).publishCardRemovedEvent("Card was removed");
    verify(cardMock).disconnect(false);
    assertThat(sut.getCard()).isNull();
    assertThat(sut.getChannel()).isNull();
  }

  @Test
  void shouldShutdown() throws CardException {
    // given
    final var cardMock = mock(Card.class);
    sut.setCard(cardMock);

    // when
    sut.shutdown();

    // then
    verify(cardMock).disconnect(false);
    assertThat(sut.getCard()).isNull();
  }
}
