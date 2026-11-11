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

package de.gematik.refpopp.popp_client.client.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.refpopp.popp_client.connector.ConnectorCommunicationServiceWrapper;
import de.gematik.refpopp.popp_client.connector.session.ConnectorSessionLifecycle;
import de.gematik.refpopp.popp_client.connector.session.ConnectorSessionLifecycle.ConnectorSession;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.ws.soap.client.SoapFaultClientException;

@ExtendWith(MockitoExtension.class)
class ConnectorSessionLifecycleTest {

  @Mock private ConnectorCommunicationServiceWrapper connectorCommunicationServiceWrapper;

  private ConnectorSessionLifecycle sut;

  @BeforeEach
  void setUp() {
    sut = new ConnectorSessionLifecycle(connectorCommunicationServiceWrapper);
  }

  @Test
  void startSessionUsesCardHandleFromConnectedEgkCard() {
    when(connectorCommunicationServiceWrapper.getConnectedEgkCard("kvnr"))
        .thenReturn("card-handle");
    when(connectorCommunicationServiceWrapper.startCardSession("card-handle"))
        .thenReturn("session-id");

    final var result = sut.startSession("kvnr");

    assertThat(result.getSessionId()).isEqualTo("session-id");
    verify(connectorCommunicationServiceWrapper).getConnectedEgkCard("kvnr");
    verify(connectorCommunicationServiceWrapper).startCardSession("card-handle");
    verify(connectorCommunicationServiceWrapper, never()).stopCardSession("session-id");
    result.close();
  }

  @Test
  void failedCardLookupDoesNotStopSession() {
    final var failure = new IllegalStateException("card lookup failed");
    when(connectorCommunicationServiceWrapper.getConnectedEgkCard("kvnr")).thenThrow(failure);

    assertThatThrownBy(() -> sut.startSession("kvnr")).isSameAs(failure);

    verify(connectorCommunicationServiceWrapper, never()).startCardSession(any());
    verify(connectorCommunicationServiceWrapper, never()).stopCardSession(any());
  }

  @Test
  void failedSessionStartDoesNotStopSession() {
    final var failure = new IllegalStateException("session start failed");
    when(connectorCommunicationServiceWrapper.getConnectedEgkCard("kvnr"))
        .thenReturn("card-handle");
    when(connectorCommunicationServiceWrapper.startCardSession("card-handle")).thenThrow(failure);

    assertThatThrownBy(() -> sut.startSession("kvnr")).isSameAs(failure);

    verify(connectorCommunicationServiceWrapper, never()).stopCardSession(any());
  }

  @Test
  void closeStopsSessionOnlyOnce() {
    final var session = startSession();

    session.close();
    session.close();

    verify(connectorCommunicationServiceWrapper).stopCardSession("session-id");
  }

  @Test
  void concurrentCloseStopsSessionOnlyOnce() throws Exception {
    final var session = startSession();
    final var start = new CountDownLatch(1);
    try (final var executor = Executors.newFixedThreadPool(2)) {
      final var first =
          executor.submit(
              () -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                session.close();
                return null;
              });
      final var second =
          executor.submit(
              () -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                session.close();
                return null;
              });
      start.countDown();
      first.get(5, TimeUnit.SECONDS);
      second.get(5, TimeUnit.SECONDS);
    }

    verify(connectorCommunicationServiceWrapper).stopCardSession("session-id");
  }

  @Test
  void closeIgnoresCancellationException() {
    final var session = startSession();
    doThrow(new CancellationException("cancelled"))
        .when(connectorCommunicationServiceWrapper)
        .stopCardSession("session-id");

    assertThatCode(session::close).doesNotThrowAnyException();
    session.close();

    verify(connectorCommunicationServiceWrapper).stopCardSession("session-id");
  }

  @Test
  void closeIgnoresUnknownSessionSoapFault() {
    final var session = startSession();
    final SoapFaultClientException exception = mock(SoapFaultClientException.class);
    when(exception.getFaultStringOrReason()).thenReturn("Unbekannte Session ID");
    doThrow(exception).when(connectorCommunicationServiceWrapper).stopCardSession("session-id");

    assertThatCode(session::close).doesNotThrowAnyException();
    session.close();

    verify(connectorCommunicationServiceWrapper).stopCardSession("session-id");
  }

  @Test
  void closeRethrowsUnexpectedSoapFaultWithoutRetrying() {
    final var session = startSession();
    final SoapFaultClientException exception = mock(SoapFaultClientException.class);
    when(exception.getFaultStringOrReason()).thenReturn("Some other fault");
    doThrow(exception).when(connectorCommunicationServiceWrapper).stopCardSession("session-id");

    assertThatThrownBy(session::close).isSameAs(exception);
    session.close();

    verify(connectorCommunicationServiceWrapper).stopCardSession("session-id");
  }

  @Test
  void closeRethrowsSoapFaultWithoutReason() {
    final var session = startSession();
    final var exception = mock(SoapFaultClientException.class);
    doThrow(exception).when(connectorCommunicationServiceWrapper).stopCardSession("session-id");

    assertThatThrownBy(session::close).isSameAs(exception);
  }

  @Test
  void closeLogsUnexpectedFailureWithSessionIdAndCause() {
    final var session = startSession();
    final var failure = new IllegalStateException("connector unavailable");
    doThrow(failure).when(connectorCommunicationServiceWrapper).stopCardSession("session-id");
    final var logger = (Logger) LoggerFactory.getLogger(ConnectorSessionLifecycle.class);
    final var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThatThrownBy(session::close).isSameAs(failure);

      assertThat(appender.list)
          .anySatisfy(
              event -> {
                assertThat(event.getFormattedMessage()).contains("session-id");
                assertThat(event.getThrowableProxy().getMessage())
                    .isEqualTo("connector unavailable");
              });
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void closeAllowsConnectorCallOnInterruptedThreadAndRestoresInterruptFlag() {
    final var session = startSession();
    doAnswer(
            invocation -> {
              assertThat(Thread.currentThread().isInterrupted()).isFalse();
              return null;
            })
        .when(connectorCommunicationServiceWrapper)
        .stopCardSession("session-id");
    try {
      Thread.currentThread().interrupt();

      session.close();

      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      verify(connectorCommunicationServiceWrapper).stopCardSession("session-id");
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void closeRestoresInterruptFlagEvenWhenCleanupFails() {
    final var session = startSession();
    final var failure = new IllegalStateException("cleanup failed");
    doThrow(failure).when(connectorCommunicationServiceWrapper).stopCardSession("session-id");
    try {
      Thread.currentThread().interrupt();

      assertThatThrownBy(session::close).isSameAs(failure);

      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  private ConnectorSession startSession() {
    when(connectorCommunicationServiceWrapper.getConnectedEgkCard("kvnr"))
        .thenReturn("card-handle");
    when(connectorCommunicationServiceWrapper.startCardSession("card-handle"))
        .thenReturn("session-id");
    return sut.startSession("kvnr");
  }
}
