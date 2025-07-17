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

import de.gematik.refpopp.popp_client.cardreader.card.events.CardConnectedEvent;
import de.gematik.refpopp.popp_client.cardreader.card.events.CardRemovedEvent;
import de.gematik.refpopp.popp_client.cardreader.events.CardReaderConnectedEvent;
import de.gematik.refpopp.popp_client.cardreader.events.CardReaderRemovedEvent;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardTerminal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class CardEventPublisher {

  private final ApplicationEventPublisher eventPublisher;

  public CardEventPublisher(final ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  public void publishReaderConnectedEvent(final CardTerminal cardTerminal, final String message) {
    final var event = new CardReaderConnectedEvent(cardTerminal, message);
    eventPublisher.publishEvent(event);
  }

  public void publishReaderRemovedEvent(final String message) {
    final var event = new CardReaderRemovedEvent(message);
    eventPublisher.publishEvent(event);
  }

  public void publishCardConnectedEvent(final CardChannel cardChannel) {
    final var event = new CardConnectedEvent(cardChannel, "Card was connected");
    eventPublisher.publishEvent(event);
  }

  public void publishCardRemovedEvent(final String message) {
    final var event = new CardRemovedEvent(this, message);
    eventPublisher.publishEvent(event);
  }
}
