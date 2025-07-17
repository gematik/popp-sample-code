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

import de.gematik.refpopp.popp_client.cardreader.card.CardEventPublisher;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReaderAndCardMonitoring implements Monitoring {

  private final TerminalFactory terminalFactory;
  private final CardEventPublisher cardEventPublisher;

  @Getter @Setter private boolean lastTerminalPresent = true;
  @Getter @Setter private CardTerminal terminal;
  @Getter @Setter private Card card;
  @Getter @Setter private CardChannel channel;
  @Getter @Setter private boolean lastCardPresent = false;

  public ReaderAndCardMonitoring(
      final TerminalFactory terminalFactory, final CardEventPublisher cardEventPublisher) {
    this.terminalFactory = terminalFactory;
    this.cardEventPublisher = cardEventPublisher;
  }

  @Override
  public void startMonitoring(
      final ScheduledExecutorService cardExecutor, final CardTerminal terminal) {
    log.debug("| Entering startCardMonitoring()");
    this.terminal = terminal;
    cardExecutor.scheduleAtFixedRate(
        () -> {
          try {
            monitorTerminalStatus();
            monitorCardStatus();
          } catch (final Exception e) {
            log.info("| Error monitoring terminal and card status: {}", e.getMessage());
            lastCardPresent = false;
            lastTerminalPresent = false;
          }
        },
        0,
        1,
        TimeUnit.SECONDS);
    log.debug("| Exiting startCardMonitoring()");
  }

  void monitorTerminalStatus() {
    final var availableTerminal = findAvailableTerminal();
    final boolean currentTerminalPresent = availableTerminal.isPresent();

    if (currentTerminalPresent != lastTerminalPresent) {
      if (currentTerminalPresent) {
        handleTerminalConnected(availableTerminal.get());
      } else {
        handleTerminalDisconnected();
      }
      lastTerminalPresent = currentTerminalPresent;
    }
  }

  void monitorCardStatus() throws CardException {
    if (lastTerminalPresent) {
      final boolean currentCardPresent = terminal.isCardPresent();
      if (currentCardPresent != lastCardPresent) {
        if (currentCardPresent) {
          handleCardInserted();
        } else {
          handleCardRemoved();
        }
        lastCardPresent = currentCardPresent;
      }
    }
  }

  void handleTerminalConnected(final CardTerminal newTerminal) {
    log.debug("| Entering handleTerminalConnected()");
    this.terminal = newTerminal;
    lastCardPresent = false;
    cardEventPublisher.publishReaderConnectedEvent(terminal, "Card reader connected");
    log.debug("| Exiting handleTerminalConnected()");
  }

  void handleTerminalDisconnected() {
    log.debug("| Entering handleTerminalDisconnected()");
    cardEventPublisher.publishReaderRemovedEvent("Card reader was removed");
    if (card != null) {
      try {
        card.disconnect(false);
        cardEventPublisher.publishCardRemovedEvent("Card was disconnected");
      } catch (final CardException e) {
        log.error("| Error disconnecting card: {}", e.getMessage());
      }
    }
    terminal = null;
    card = null;
    channel = null;
    log.debug("| Exiting handleTerminalDisconnected()");
  }

  private Optional<CardTerminal> findAvailableTerminal() {
    try {
      final var terminals = terminalFactory.terminals().list();
      return terminals.isEmpty() ? Optional.empty() : Optional.of(terminals.getFirst());
    } catch (final CardException e) {
      log.error("| Error getting terminals: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private void handleCardInserted() {
    log.debug("| Entering handleCardInserted()");
    try {
      card =
          terminal.connect(
              "T=1"); // actually "*" should be used instead of "T=1" but works as it is
      channel = card.getBasicChannel();

      lastCardPresent = true;
      cardEventPublisher.publishCardConnectedEvent(channel);
      log.info("| Connected to card");
    } catch (final CardException e) {
      log.error("| Error connecting to card: {}", e.getMessage());
    }
    log.debug("| Exiting handleCardInserted()");
  }

  private void handleCardRemoved() {
    log.debug("| Entering handleCardRemoved()");
    if (card != null) {
      try {
        card.disconnect(false);
        log.info("| Card removed");
        cardEventPublisher.publishCardRemovedEvent("Card was removed");
      } catch (final CardException e) {
        log.info("| Error disconnecting card: {}", e.getMessage());
      }
      card = null;
    }
    channel = null;
    log.debug("| Exiting handleCardRemoved()");
  }

  @PreDestroy
  void shutdown() {
    log.debug("| Entering shutdown()");

    if (card != null) {
      try {
        card.disconnect(false);
        card = null;
      } catch (final CardException e) {
        log.info("| Error disconnecting card: {}", e.getMessage());
      }
    }
    log.debug("| Exiting shutdown()");
  }
}
