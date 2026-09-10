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

package de.gematik.refpopp.popp_server.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestScopedSessionCommunicationTest {

  private SessionCommunication delegate;
  private RequestScopedSessionCommunication sut;

  @BeforeEach
  void setUp() {
    // given
    delegate = mock(SessionCommunication.class);
    sut = new RequestScopedSessionCommunication(delegate, "transport::client");
  }

  @Test
  void sendMessageDelegatesToUnderlyingCommunication() {
    // given
    final Object message = "hello";

    // when
    sut.sendMessage(message);

    // then
    verify(delegate).sendMessage(message);
  }

  @Test
  void getSessionIdReturnsLogicalSessionId() {
    // when
    final var sessionId = sut.getSessionId();

    // then
    assertThat(sessionId).isEqualTo("transport::client");
  }

  @Test
  void getTransportSessionIdDelegatesToUnderlyingCommunication() {
    // given
    when(delegate.getTransportSessionId()).thenReturn("transportId");

    // when
    final var transportId = sut.getTransportSessionId();

    // then
    assertThat(transportId).isEqualTo("transportId");
    verify(delegate).getTransportSessionId();
  }

  @Test
  void closeSessionDelegatesToUnderlyingCommunication() {
    // when
    sut.closeSession();

    // then
    verify(delegate).closeSession();
  }
}
