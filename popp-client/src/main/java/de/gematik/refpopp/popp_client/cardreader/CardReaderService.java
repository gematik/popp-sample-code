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

import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CardReaderService implements CardReader {

  private static final long POLL_PERIOD_MILLIS = 1000L;
  private final Monitoring readerAndCardMonitoring;
  private final TerminalFactory terminalFactory;
  private final ScheduledExecutorService cardExecutor;
  private final String cardReaderName;

  public CardReaderService(
      final Monitoring readerAndCardMonitoring,
      final TerminalFactory terminalFactory,
      final ScheduledExecutorService scheduledExecutorService,
      final String cardReaderName) {
    this.readerAndCardMonitoring = readerAndCardMonitoring;
    log.debug("| Entering CardReaderService()");
    this.terminalFactory = terminalFactory;
    this.cardExecutor = scheduledExecutorService;
    this.cardReaderName = cardReaderName;
    log.debug("| Exiting CardReaderService()");
  }

  @Override
  public void startCheckForCardReader() {
    log.debug("| Entering startCheckForCardReader()");
    cardExecutor.submit(
        () -> {
          try {
            final var terminal = initializeCardTerminal();
            readerAndCardMonitoring.startMonitoring(cardExecutor, terminal);
          } catch (final Exception e) {
            log.error("| Error initializing card reader: {}", e.getMessage());
          }
        });
    log.debug("| Exiting startCheckForCardReader()");
  }

  CardTerminal initializeCardTerminal() throws Exception {
    log.debug("| Entering initializeCardTerminal()");
    final CardTerminal cardTerminal;

    while (true) {
      final var availableTerminal = findAvailableTerminal();
      if (availableTerminal.isPresent()) {
        cardTerminal = availableTerminal.get();
        log.info("| Card-Terminal found: {}", cardTerminal.getName());
        break;
      } else {
        log.info("| No Card-Terminal found, retrying in {} second", POLL_PERIOD_MILLIS / 1000);
      }

      Thread.sleep(POLL_PERIOD_MILLIS);
    }

    log.debug("| Exiting initializeCardTerminal()");

    return cardTerminal;
  }

  private Optional<CardTerminal> findAvailableTerminal() {
    try {
      final var terminals = terminalFactory.terminals().list();

      for (final CardTerminal item : terminals) {
        if (item.getName() != null
            && cardReaderName != null
            && item.getName().contains(cardReaderName)) {
          return Optional.of(item);
        }
      }

      return terminals.isEmpty() ? Optional.empty() : Optional.of(terminals.getFirst());
    } catch (final CardException e) {
      log.error("| Error getting terminals: {}", e.getMessage());
      return Optional.empty();
    }
  }

  @PreDestroy
  void shutdown() {
    log.debug("| Entering shutdown()");
    cardExecutor.shutdownNow();
    try {
      if (!cardExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        log.info("| Could not terminate cardExecutor");
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    log.debug("| Exiting shutdown()");
  }
}
