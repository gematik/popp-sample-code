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

import de.gematik.openhealth.healthcard.*;
import de.gematik.poppcommons.api.messages.ScenarioStep;
import de.gematik.refpopp.popp_client.cardreader.card.events.CardConnectedEvent;
import de.gematik.refpopp.popp_client.cardreader.card.events.CardRemovedEvent;
import de.gematik.refpopp.popp_client.cardreader.card.events.PaceInitializationCompleteEvent;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Setter
@Slf4j
public class CardCommunicationService {

  private static final int APDU_RESPONSE_CODE_SECURITY_CONDITION_NOT_SATISFIED = 0x6982;
  private static final String CARD_ACCESS_NUMBER = "123123";
  private static final HexFormat HEX_FORMAT = HexFormat.of();
  private CardChannel cardChannel;
  private SecureChannel secureChannel;
  private final ApplicationEventPublisher eventPublisher;

  public CardCommunicationService(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
    log.debug("| Entering CardService()");
    log.debug("| Exiting CardService()");
  }

  @EventListener
  public void handleCardRemovedEvent(final CardRemovedEvent event) {
    log.debug("| Entering handleCardRemovedEvent()");
    cardChannel = null;
    secureChannel = null;
    log.debug("| Exiting handleCardRemovedEvent()");
  }

  @EventListener
  public void handleCardConnectionEvent(final CardConnectedEvent event) {
    log.debug("| Entering handleCardConnectionEvent()");
    this.cardChannel = event.getCardChannel().orElse(null);

    if (isContactless()) {
      log.info("| Detected contactless reader - Initialize PACE");
      initializePACE();
      eventPublisher.publishEvent(new PaceInitializationCompleteEvent());

    } else {
      log.info("| Detected contact-based reader - No PACE necessary");
    }

    log.debug("| Exiting handleCardConnectionEvent()");
  }

  public String process(final ScenarioStep scenarioStep) {
    final var normalizedCommandApdu = normalize(scenarioStep.getCommandApdu());
    log.info("| APDU command: {}", normalizedCommandApdu);
    final var commandApdu = new CommandAPDU(HEX_FORMAT.parseHex(normalizedCommandApdu));
    final var responseAPDU = sendApdu(commandApdu);
    final var actualStatusWord = Integer.toHexString(responseAPDU.getSW());

    final var responseData = responseAPDU.getData();
    if (responseData.length > 0) {
      log.info("| APDU Response data: {}", HEX_FORMAT.formatHex(responseAPDU.getData()));
    }

    checkStatusWord(scenarioStep.getExpectedStatusWords(), actualStatusWord);

    return HEX_FORMAT.formatHex(responseAPDU.getBytes());
  }

  public List<String> process(final List<ScenarioStep> scenarioStep) {
    final var responses = new ArrayList<String>();
    for (final var step : scenarioStep) {
      responses.add(process(step));
    }

    return responses;
  }

  private static void checkStatusWord(
      final List<String> expectedStatusWords, final String actualStatusWord) {

    if (expectedStatusWords.contains(actualStatusWord)) {
      log.info("| Status word is as expected");
    } else {
      log.error("| Status word {} is not as expected {}", actualStatusWord, expectedStatusWords);
      throw new IllegalStateException("Status word is not as expected");
    }
  }

  private String normalize(final String s) {
    return s.replaceAll("\\s+", "");
  }

  public Optional<CardChannel> getCardChannel() {
    if (cardChannel == null) {
      return Optional.empty();
    }
    return Optional.of(cardChannel);
  }

  public Optional<SecureChannel> getSecureChannel() {
    if (secureChannel == null) {
      return Optional.empty();
    }
    return Optional.of(secureChannel);
  }

  private ResponseAPDU sendApdu(final CommandAPDU commandAPDU) {
    final ResponseAPDU responseAPDU;
    if (secureChannel != null) {
      responseAPDU = transmitSecure(commandAPDU);
    } else {
      responseAPDU = transmitStandard(commandAPDU);
    }

    final var statusWord = Integer.toHexString(responseAPDU.getSW());
    log.info("| APDU Response status word: {}", statusWord);

    return responseAPDU;
  }

  private ResponseAPDU transmitStandard(final CommandAPDU commandAPDU) {
    final ResponseAPDU responseAPDU;
    try {
      responseAPDU =
          getCardChannel()
              .orElseThrow(() -> new IllegalStateException("| No card inserted"))
              .transmit(commandAPDU);
    } catch (final CardException e) {
      throw new IllegalStateException(e);
    }
    return responseAPDU;
  }

  private ResponseAPDU transmitSecure(final CommandAPDU commandAPDU) {
    try {
      final var cmd = CommandApdu.Companion.fromBytes(commandAPDU.getBytes());
      final var response = secureChannel.transmit(cmd);
      return new ResponseAPDU(response.toBytes());
    } catch (final ApduException | SecureChannelException e) {
      throw new IllegalStateException(e);
    }
  }

  protected void initializePACE() {
    try {
      final var cardChannel =
          new de.gematik.openhealth.healthcard.CardChannel() {
            public boolean supportsExtendedLength() {
              return true;
            }

            public ResponseApdu transmit(CommandApdu commandApdu) {

              final var response =
                  transmitStandard(new CommandAPDU(commandApduToBytes(commandApdu)));
              try {
                return responseApduFromBytes(response.getBytes());
              } catch (ApduException e) {
                throw new IllegalStateException(e);
              }
            }
          };

      final var cardAccessNumber = createCardAccessNumber();
      secureChannel = establishSecureChannel(cardChannel, cardAccessNumber);
    } catch (final SecureChannelException e) {
      throw new IllegalStateException(e);
    }
  }

  protected CardAccessNumber createCardAccessNumber() throws SecureChannelException {
    return createCardAccessNumberFromDigits(CARD_ACCESS_NUMBER);
  }

  protected SecureChannel establishSecureChannel(
      final de.gematik.openhealth.healthcard.CardChannel cardChannel,
      final CardAccessNumber cardAccessNumber)
      throws SecureChannelException {
    return doEstablishSecureChannel(cardChannel, cardAccessNumber);
  }

  protected CardAccessNumber createCardAccessNumberFromDigits(final String digits)
      throws SecureChannelException {
    return CardAccessNumber.Companion.fromDigits(digits);
  }

  protected SecureChannel doEstablishSecureChannel(
      final de.gematik.openhealth.healthcard.CardChannel cardChannel,
      final CardAccessNumber cardAccessNumber)
      throws SecureChannelException {
    return HealthcardKt.establishSecureChannel(cardChannel, cardAccessNumber);
  }

  protected byte[] commandApduToBytes(final CommandApdu commandApdu) {
    return commandApdu.toBytes();
  }

  protected ResponseApdu responseApduFromBytes(final byte[] bytes) throws ApduException {
    return ResponseApdu.Companion.fromBytes(bytes);
  }

  protected boolean isContactless() {
    // perform some card operation that is only allowed with PACE on contactless readers
    final CommandAPDU commandAPDU = new CommandAPDU(0x00, 0xB0, 0x87, 0x00, 0x100);
    final ResponseAPDU responseAPDU;
    try {
      responseAPDU =
          getCardChannel()
              .orElseThrow(() -> new IllegalStateException("| No card inserted"))
              .transmit(commandAPDU);
    } catch (final CardException e) {
      throw new IllegalStateException(e);
    }

    if (responseAPDU == null) {
      return false;
    } else {
      return responseAPDU.getSW() == APDU_RESPONSE_CODE_SECURITY_CONDITION_NOT_SATISFIED;
    }
  }
}
