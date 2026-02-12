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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.gematik.refpopp.popp_client.cardreader.card.events.CardConnectedEvent;
import de.gematik.refpopp.popp_client.cardreader.card.events.CardRemovedEvent;
import de.gematik.refpopp.popp_client.cardreader.events.CardReaderConnectedEvent;
import de.gematik.refpopp.popp_client.cardreader.events.CardReaderRemovedEvent;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardTerminal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class CardEventPublisherTest {

  @Test
  void publishReaderConnectedEventPublishesEvent() {
    final var eventPublisher = mock(ApplicationEventPublisher.class);
    final var cardTerminal = mock(CardTerminal.class);
    final var publisher = new CardEventPublisher(eventPublisher);

    publisher.publishReaderConnectedEvent(cardTerminal, "reader up");

    final var captor = ArgumentCaptor.forClass(CardReaderConnectedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().getTerminal()).contains(cardTerminal);
    assertThat(captor.getValue().getMessage()).isEqualTo("reader up");
  }

  @Test
  void publishReaderRemovedEventPublishesEvent() {
    final var eventPublisher = mock(ApplicationEventPublisher.class);
    final var publisher = new CardEventPublisher(eventPublisher);

    publisher.publishReaderRemovedEvent("reader down");

    final var captor = ArgumentCaptor.forClass(CardReaderRemovedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().getMessage()).isEqualTo("reader down");
  }

  @Test
  void publishCardConnectedEventPublishesEvent() {
    final var eventPublisher = mock(ApplicationEventPublisher.class);
    final var cardChannel = mock(CardChannel.class);
    final var publisher = new CardEventPublisher(eventPublisher);

    publisher.publishCardConnectedEvent(cardChannel);

    final var captor = ArgumentCaptor.forClass(CardConnectedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().getCardChannel()).contains(cardChannel);
    assertThat(captor.getValue().getEventMessage()).isEqualTo("Card was connected");
  }

  @Test
  void publishCardRemovedEventPublishesEvent() {
    final var eventPublisher = mock(ApplicationEventPublisher.class);
    final var publisher = new CardEventPublisher(eventPublisher);

    publisher.publishCardRemovedEvent("card removed");

    final var captor = ArgumentCaptor.forClass(CardRemovedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().getEventMessage()).isEqualTo("card removed");
  }
}
