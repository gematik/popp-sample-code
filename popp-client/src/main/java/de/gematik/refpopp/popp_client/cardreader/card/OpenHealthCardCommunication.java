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

import de.gematik.openhealth.smartcard.card.SmartCardCommunicationScope;
import de.gematik.openhealth.smartcard.command.CardCommandApdu;
import de.gematik.openhealth.smartcard.command.CardResponseApdu;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenHealthCardCommunication implements SmartCardCommunicationScope {

  private final Card card;
  private final ByteBuffer buffer = ByteBuffer.allocate(1024);

  public OpenHealthCardCommunication(final Card Card) {
    this.card = Card;
  }

  @Override
  public boolean getSupportsExtendedLength() {
    return true;
  }

  @Override
  public void transmit(
      final CardCommandApdu cardCommandApdu,
      final Function1<? super CardResponseApdu, Unit> response) {
    buffer.clear();
    try {
      final var count =
          card.getBasicChannel().transmit(ByteBuffer.wrap(cardCommandApdu.getApdu()), buffer);
      final var respApduBytes = Arrays.copyOfRange(buffer.array(), 0, count);
      log.debug("| Received: " + HexFormat.ofDelimiter(" ").formatHex(respApduBytes));
      response.invoke(new CardResponseApdu(respApduBytes));
    } catch (final CardException e) {
      throw new RuntimeException(e);
    }
  }
}
