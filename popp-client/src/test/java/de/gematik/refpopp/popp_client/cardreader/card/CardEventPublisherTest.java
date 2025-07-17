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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

import de.gematik.refpopp.popp_client.cardreader.card.events.CardConnectedEvent;
import de.gematik.refpopp.popp_client.cardreader.card.events.CardRemovedEvent;
import de.gematik.refpopp.popp_client.cardreader.events.CardReaderConnectedEvent;
import de.gematik.refpopp.popp_client.cardreader.events.CardReaderRemovedEvent;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

class CardEventPublisherTest {

  private CardEventPublisher cardEventPublisher;

  @Mock private ApplicationEventPublisher eventPublisherMock;
  @Mock private CardChannel cardChannelMock;
  @Mock private CardTerminal cardTerminalMock;

  private AutoCloseable closeable;

  @BeforeEach
  void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
    cardEventPublisher = new CardEventPublisher(eventPublisherMock);
  }

  @AfterEach
  void close() throws Exception {
    closeable.close();
  }

  @Test
  void publishReaderConnectedEventPublishesEvent() {
    // given

    // when
    cardEventPublisher.publishReaderConnectedEvent(cardTerminalMock, "Reader connected");

    // then
    verify(eventPublisherMock).publishEvent(any(CardReaderConnectedEvent.class));
  }

  @Test
  void publishReaderRemovedEventPublishesEvent() {
    // given

    // when
    cardEventPublisher.publishReaderRemovedEvent("Reader removed");

    // then
    verify(eventPublisherMock).publishEvent(any(CardReaderRemovedEvent.class));
  }

  @Test
  void publishCardConnectedEventPublishesEvent() {
    // given

    // when
    cardEventPublisher.publishCardConnectedEvent(cardChannelMock);

    // then
    verify(eventPublisherMock).publishEvent(any(CardConnectedEvent.class));
  }

  @Test
  void publishCardRemovedEventPublishesEvent() {
    // given

    // when
    cardEventPublisher.publishCardRemovedEvent("Card removed");

    // then
    verify(eventPublisherMock).publishEvent(any(CardRemovedEvent.class));
  }
}
