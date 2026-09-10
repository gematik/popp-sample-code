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

package de.gematik.refpopp.popp_client.client.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.messages.ScenarioStep;
import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.refpopp.popp_client.cardreader.card.CardCommunicationService;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardService;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardSessionState;
import de.gematik.refpopp.popp_client.client.session.ClientRequestContext;
import de.gematik.refpopp.popp_client.client.session.CommunicationSessionRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StandardScenarioProcessorTest {

  @Mock private CardCommunicationService cardCommunicationService;
  @Mock private VirtualCardService virtualCardService;
  @Mock private CommunicationSessionRegistry sessionRegistry;

  private StandardScenarioProcessor sut;

  @BeforeEach
  void setUp() {
    sut =
        new StandardScenarioProcessor(
            cardCommunicationService, virtualCardService, sessionRegistry);
  }

  @Test
  void processUsesCardCommunicationServiceWhenSessionIsNotVirtual() {
    final var steps = List.of(new ScenarioStep("00A4040000", List.of("9000")));
    final var message =
        StandardScenarioMessage.builder()
            .version("1.0.0")
            .clientSessionId("session-id")
            .sequenceCounter(0)
            .timeSpan(0)
            .steps(steps)
            .build();
    final var context = new ClientRequestContext(null);
    context.setVirtualCard(false);
    when(cardCommunicationService.process(steps)).thenReturn(List.of("9000"));

    final var result = sut.process(message, context);

    assertThat(result).containsExactly("9000");
    verify(cardCommunicationService).process(steps);
    verifyNoInteractions(virtualCardService, sessionRegistry);
  }

  @Test
  void processUsesDefaultVirtualCardServiceWhenSessionHasNoClientSessionId() {
    final var steps = List.of(new ScenarioStep("00A4040000", List.of("9000")));
    final var message =
        StandardScenarioMessage.builder()
            .version("1.0.0")
            .clientSessionId("session-id")
            .sequenceCounter(0)
            .timeSpan(0)
            .steps(steps)
            .build();
    final var context = new ClientRequestContext(null);
    context.setVirtualCard(true);
    when(virtualCardService.isConfigured()).thenReturn(true);
    when(virtualCardService.process(steps)).thenReturn(List.of("9000"));

    final var result = sut.process(message, context);

    assertThat(result).containsExactly("9000");
    verify(virtualCardService).isConfigured();
    verify(virtualCardService).process(steps);
    verifyNoInteractions(cardCommunicationService, sessionRegistry);
  }

  @Test
  void processUsesSessionVirtualCardServiceWithSessionStateWhenClientSessionIdExists() {
    final var steps = List.of(new ScenarioStep("00A4040000", List.of("9000")));
    final var message =
        StandardScenarioMessage.builder()
            .version("1.0.0")
            .clientSessionId("session-id")
            .sequenceCounter(0)
            .timeSpan(0)
            .steps(steps)
            .build();
    final var context = new ClientRequestContext("session-id");
    context.setVirtualCard(true);
    final var sessionState = new VirtualCardSessionState();
    final var sessionVirtualCardService = mock(VirtualCardService.class);
    when(sessionRegistry.getVirtualCardServiceOrDefault("session-id", virtualCardService))
        .thenReturn(sessionVirtualCardService);
    when(sessionVirtualCardService.isConfigured()).thenReturn(true);
    when(sessionRegistry.getOrCreateVirtualCardSessionState("session-id")).thenReturn(sessionState);
    when(sessionVirtualCardService.process(steps, sessionState)).thenReturn(List.of("9000"));

    final var result = sut.process(message, context);

    assertThat(result).containsExactly("9000");
    final var sessionStateCaptor = ArgumentCaptor.forClass(VirtualCardSessionState.class);
    verify(sessionRegistry).getVirtualCardServiceOrDefault("session-id", virtualCardService);
    verify(sessionRegistry).getOrCreateVirtualCardSessionState("session-id");
    verify(sessionVirtualCardService).isConfigured();
    verify(sessionVirtualCardService).process(eq(steps), sessionStateCaptor.capture());
    assertThat(sessionStateCaptor.getValue()).isSameAs(sessionState);
    verifyNoInteractions(cardCommunicationService);
  }

  @Test
  void processRejectsUnconfiguredVirtualCardService() {
    final var steps = List.of(new ScenarioStep("00A4040000", List.of("9000")));
    final var message =
        StandardScenarioMessage.builder()
            .version("1.0.0")
            .clientSessionId("session-id")
            .sequenceCounter(0)
            .timeSpan(0)
            .steps(steps)
            .build();
    final var context = new ClientRequestContext(null);
    context.setVirtualCard(true);
    when(virtualCardService.isConfigured()).thenReturn(false);

    assertThatThrownBy(() -> sut.process(message, context))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No image file configured for virtual card.");

    verify(virtualCardService).isConfigured();
    verifyNoInteractions(cardCommunicationService, sessionRegistry);
  }
}
