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

package de.gematik.refpopp.popp_server.scenario.common.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.messages.ScenarioResponseMessage;
import de.gematik.poppcommons.api.messages.StartMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageHandlerProviderTest {

  private MessageHandlerProvider sut;

  @BeforeEach
  void setUp() {
    final var startMessageHandlerMock = mock(StartMessageHandler.class);
    when(startMessageHandlerMock.getMessageType()).thenReturn(StartMessage.class);
    final var scenarioResponseMessageHandlerMock = mock(ScenarioResponseMessageHandler.class);
    when(scenarioResponseMessageHandlerMock.getMessageType())
        .thenReturn(ScenarioResponseMessage.class);
    sut =
        new MessageHandlerProvider(
            List.of(startMessageHandlerMock, scenarioResponseMessageHandlerMock));
  }

  @Test
  void getHandlerForStartMessage() {
    // given
    final var startMessage = StartMessage.builder().build();

    // when
    final var handler = sut.getHandlerFor(startMessage);

    // then
    assertThat(handler).isNotNull().isInstanceOf(StartMessageHandler.class);
  }

  @Test
  void getHandlerForScenarioResponseMessage() {
    // given
    final var scenarioResponseMessage = new ScenarioResponseMessage(List.of());

    // when
    final var handler = sut.getHandlerFor(scenarioResponseMessage);

    // then
    assertThat(handler).isNotNull().isInstanceOf(ScenarioResponseMessageHandler.class);
  }
}
