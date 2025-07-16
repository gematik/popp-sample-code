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

package de.gematik.refpopp.popp_client.cardreader.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.smartcardio.CardTerminal;
import org.junit.jupiter.api.Test;

class CardReaderEventTest {

  @Test
  void getTerminalReturnsEmpty() {
    final var sut = new CardReaderEvent(null, "message") {};
    assertThat(sut.getTerminal()).isEmpty();
  }

  @Test
  void getTerminalReturnsExistingTerminal() {
    final var cardTerminal = mock(CardTerminal.class);
    final var sut = new CardReaderEvent(cardTerminal, "message") {};
    assertThat(sut.getTerminal()).isPresent();
    assertThat(sut.getTerminal().get()).isEqualTo(cardTerminal);
  }
}
