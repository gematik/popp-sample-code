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

package de.gematik.refpopp.popp_client.client;

import static org.mockito.Mockito.verify;

import de.gematik.refpopp.popp_client.client.events.CommunicationEvent;
import de.gematik.refpopp.popp_client.client.events.TextMessageReceivedEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketCommunicationErrorEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketConnectionClosedEvent;
import de.gematik.refpopp.popp_client.client.events.WebSocketConnectionOpenedEvent;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

class CommunicationEventPublisherTest {

  @Mock private ApplicationEventPublisher eventPublisherMock;
  private AutoCloseable closeable;

  private CommunicationEventPublisher cardEventPublisher;

  @BeforeEach
  void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
    cardEventPublisher = new CommunicationEventPublisher(eventPublisherMock);
  }

  @AfterEach
  void close() throws Exception {
    closeable.close();
  }

  @ParameterizedTest(name = "Publishing event {0}")
  @MethodSource("provideCommunicationEvent")
  void publishEventLogsEnteringAndExiting(final CommunicationEvent event) {
    // given

    // when
    cardEventPublisher.publishEvent(event);

    // then
    verify(eventPublisherMock).publishEvent(event);
  }

  static Stream<CommunicationEvent> provideCommunicationEvent() {
    return Stream.of(
        new TextMessageReceivedEvent("textMessageReceivedEvent"),
        new WebSocketCommunicationErrorEvent(),
        new WebSocketConnectionClosedEvent(),
        new WebSocketConnectionOpenedEvent());
  }
}
