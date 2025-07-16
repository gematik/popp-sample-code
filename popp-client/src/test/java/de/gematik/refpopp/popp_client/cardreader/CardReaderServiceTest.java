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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.TerminalFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CardReaderServiceTest {

  @Mock private TerminalFactory terminalFactoryMock;

  @Mock private ScheduledExecutorService cardExecutorMock;

  @Mock private ReaderAndCardMonitoring readerAndCardMonitoring;

  @InjectMocks private CardReaderService sut;

  private AutoCloseable autoCloseable;

  @BeforeEach
  void setUp() {
    autoCloseable = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void close() throws Exception {
    autoCloseable.close();
  }

  @Test
  void shouldStartCheckForCardReader() throws Exception {
    // given
    final var spy = spy(sut);
    final var cardTerminalMock = mock(CardTerminal.class);
    doReturn(cardTerminalMock).when(spy).initializeCardTerminal();
    final var runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    when(cardExecutorMock.submit(any(Runnable.class))).thenReturn(null);

    // when
    spy.startCheckForCardReader();

    // then
    verify(cardExecutorMock).submit(runnableCaptor.capture());
    final var submittedTask = runnableCaptor.getValue();
    assertNotNull(submittedTask);
    submittedTask.run();
    verify(readerAndCardMonitoring)
        .startMonitoring(any(ScheduledExecutorService.class), any(CardTerminal.class));
  }

  @Test
  void shouldStartCheckForCardReaderWithException() throws Exception {
    // given
    final var runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    final var cardReaderServiceSpy = spy(sut);
    doThrow(new RuntimeException("Initialization failed"))
        .when(cardReaderServiceSpy)
        .initializeCardTerminal();

    // when
    cardReaderServiceSpy.startCheckForCardReader();

    // then
    verify(cardExecutorMock).submit(runnableCaptor.capture());
    final var submittedTask = runnableCaptor.getValue();
    assertNotNull(submittedTask);
    submittedTask.run();
    verify(cardReaderServiceSpy).initializeCardTerminal();
    verify(readerAndCardMonitoring, never())
        .startMonitoring(any(ScheduledExecutorService.class), any(CardTerminal.class));
  }

  @Test
  void shouldInitializeCardTerminalSuccessfully() throws Exception {
    // given
    final var cardTerminalsMock = mock(CardTerminals.class);
    final var cardTerminalMock = mock(CardTerminal.class);
    when(terminalFactoryMock.terminals()).thenReturn(cardTerminalsMock);
    when(cardTerminalsMock.list()).thenReturn(List.of(cardTerminalMock));

    // when
    final var result = sut.initializeCardTerminal();

    // then
    verify(terminalFactoryMock, times(1)).terminals();
    assertThat(result).isEqualTo(cardTerminalMock);
  }

  @Test
  void shouldRetryInitializeCardTerminalIfNoTerminalsPresent() throws Exception {
    // given
    final var terminals = new ArrayList<CardTerminal>();
    final var cardTerminalsMock = mock(CardTerminals.class);
    final var cardTerminalMock = mock(CardTerminal.class);
    when(terminalFactoryMock.terminals()).thenReturn(cardTerminalsMock);
    when(cardTerminalsMock.list()).thenReturn(terminals, List.of(cardTerminalMock));

    // when
    final var result = sut.initializeCardTerminal();

    // then
    verify(terminalFactoryMock, times(2)).terminals();
    assertThat(result).isEqualTo(cardTerminalMock);
  }

  @Test
  void shouldRetryInitializeCardTerminalIfExceptionIsThrown() throws Exception {
    // given
    final var cardTerminalsMock = mock(CardTerminals.class);
    final var cardTerminalMock = mock(CardTerminal.class);
    when(terminalFactoryMock.terminals()).thenReturn(cardTerminalsMock);
    when(cardTerminalsMock.list())
        .thenThrow(new CardException("First call exception"))
        .thenReturn(List.of(cardTerminalMock));

    // when
    final var result = sut.initializeCardTerminal();

    // then
    verify(terminalFactoryMock, times(2)).terminals();
    assertThat(result).isEqualTo(cardTerminalMock);
  }

  @Test
  void shouldShutdownExecutorService() throws InterruptedException, CardException {
    // given
    when(cardExecutorMock.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);

    // when
    sut.shutdown();

    // then
    verify(cardExecutorMock).shutdownNow();
    verify(cardExecutorMock).awaitTermination(5, TimeUnit.SECONDS);
  }
}
