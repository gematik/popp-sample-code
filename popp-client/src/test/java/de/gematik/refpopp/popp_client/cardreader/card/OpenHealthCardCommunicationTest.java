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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.openhealth.smartcard.command.CardCommandApdu;
import de.gematik.openhealth.smartcard.command.CardResponseApdu;
import java.nio.ByteBuffer;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class OpenHealthCardCommunicationTest {

  @Mock private Card cardMock;
  @Mock private CardChannel cardChannelMock;

  private AutoCloseable closeable;

  private OpenHealthCardCommunication openHealthCardCommunication;

  @BeforeEach
  void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
    openHealthCardCommunication = new OpenHealthCardCommunication(cardMock);
  }

  @AfterEach
  void close() throws Exception {
    closeable.close();
  }

  @Test
  void getSupportsExtendedLength() {
    // given // when // then
    assertThat(openHealthCardCommunication.getSupportsExtendedLength()).isTrue();
  }

  @Test
  void transmit() throws CardException {
    // given
    final var commandAPDU = new CommandAPDU(0x00, 0xB0, 0x87, 0x00, 0x100);
    final var cmd = CardCommandApdu.Companion.ofApdu(commandAPDU.getBytes());

    when(cardMock.getBasicChannel()).thenReturn(cardChannelMock);
    when(cardChannelMock.transmit(any(ByteBuffer.class), any(ByteBuffer.class))).thenReturn(2);

    // when
    openHealthCardCommunication.transmit(
        cmd,
        new Function1<CardResponseApdu, Unit>() {
          @Override
          public Unit invoke(final CardResponseApdu cardResponseApdu) {
            return Unit.INSTANCE;
          }
        });

    // then
    verify(cardMock).getBasicChannel();
    verify(cardChannelMock).transmit(any(ByteBuffer.class), any(ByteBuffer.class));
  }
}
