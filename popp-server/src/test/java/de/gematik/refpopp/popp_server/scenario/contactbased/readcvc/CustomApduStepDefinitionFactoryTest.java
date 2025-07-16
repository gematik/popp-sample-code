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

package de.gematik.refpopp.popp_server.scenario.contactbased.readcvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.smartcards.g2icc.cos.SecureMessagingConverterSoftware;
import de.gematik.smartcards.g2icc.cvc.Cvc;
import de.gematik.smartcards.sdcom.apdu.CommandApdu;
import de.gematik.smartcards.tlv.BerTlv;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class CustomApduStepDefinitionFactoryTest {

  private CustomApduStepDefinitionFactory sut;
  private CommandApduFactory commandApduFactoryMock;
  private SecureMessagingConverterSoftware secureMessagingConverterSoftwareMock;

  @BeforeEach
  void setUp() {
    commandApduFactoryMock = mock(CommandApduFactory.class);
    secureMessagingConverterSoftwareMock = mock(SecureMessagingConverterSoftware.class);
    sut =
        new CustomApduStepDefinitionFactory(
            commandApduFactoryMock, secureMessagingConverterSoftwareMock);
  }

  @Test
  void createReturnsStatesWithCustomApdus() {
    // given
    final var commandApduMock = mock(CommandApdu.class);
    final var cvc1Mock = mock(Cvc.class);
    final var cvc2Mock = mock(Cvc.class);
    final var berTlvMock = mock(BerTlv.class);
    final var cvcList = List.of(cvc1Mock, cvc2Mock);
    when(berTlvMock.getEncoded()).thenReturn("encoded".getBytes());
    when(cvc1Mock.getCar()).thenReturn("car1");
    when(cvc1Mock.getValueField()).thenReturn("value1".getBytes());
    when(cvc2Mock.getCar()).thenReturn("car2");
    when(cvc2Mock.getValueField()).thenReturn("value2".getBytes());
    when(commandApduFactoryMock.createCommandApdu(
            anyInt(), anyInt(), anyInt(), anyInt(), any(byte[].class)))
        .thenReturn(commandApduMock);
    when(secureMessagingConverterSoftwareMock.getGeneralAuthenticateStep1())
        .thenReturn(commandApduMock);
    when(commandApduMock.getBytes()).thenReturn("bytes".getBytes());

    try (final MockedStatic<BerTlv> berTlv = mockStatic(BerTlv.class)) {
      berTlv.when(() -> BerTlv.getInstance(anyLong(), anyString())).thenReturn(berTlvMock);

      // when
      final var states = sut.create(cvcList);

      // then
      assertThat(states).hasSize(5);
      verify(commandApduFactoryMock, times(4))
          .createCommandApdu(anyInt(), anyInt(), anyInt(), anyInt(), any(byte[].class));
      verify(secureMessagingConverterSoftwareMock).getGeneralAuthenticateStep1();
    }
  }
}
