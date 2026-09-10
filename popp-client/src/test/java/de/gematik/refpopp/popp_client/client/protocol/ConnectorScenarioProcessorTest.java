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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.messages.ConnectorScenarioMessage;
import de.gematik.poppcommons.api.messages.ScenarioStep;
import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.refpopp.popp_client.client.session.ClientRequestContext;
import de.gematik.refpopp.popp_client.connector.ConnectorCommunicationServiceWrapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ConnectorScenarioProcessorTest {

  @Mock private ConnectorCommunicationServiceWrapper connectorCommunicationServiceWrapper;
  @Mock private StandardScenarioProcessor standardScenarioProcessor;

  private final ObjectMapper mapper = new ObjectMapper();

  private ConnectorScenarioProcessor sut;

  @BeforeEach
  void setUp() {
    sut =
        new ConnectorScenarioProcessor(
            mapper, connectorCommunicationServiceWrapper, standardScenarioProcessor);
  }

  @Test
  void processUsesConnectorWrapperWhenSessionIsNotMocked() {
    final var signedScenario = "header.payload.signature";
    final var message = new ConnectorScenarioMessage("1.0.0", signedScenario);
    final var context = mock(ClientRequestContext.class);
    when(context.isConnectorMock()).thenReturn(false);
    when(connectorCommunicationServiceWrapper.secureSendApdu(signedScenario))
        .thenReturn(List.of("9000"));

    final var result = sut.process(message, context);

    assertThat(result).containsExactly("9000");
    verify(connectorCommunicationServiceWrapper).secureSendApdu(signedScenario);
    verifyNoInteractions(standardScenarioProcessor);
  }

  @Test
  void processRejectsWhenPayloadBase64Invalid() {
    final var signedScenario = "header.!nv@l!d!!.signature";
    final var message = new ConnectorScenarioMessage("1.0.0", signedScenario);
    final var context = mock(ClientRequestContext.class);
    when(context.isConnectorMock()).thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> sut.process(message, context));
    verifyNoInteractions(connectorCommunicationServiceWrapper, standardScenarioProcessor);
  }

  @Test
  void processUsesStandardScenarioProcessorWhenSessionIsMocked() {
    final var standardScenarioMessage =
        StandardScenarioMessage.builder()
            .version("1.0.0")
            .clientSessionId("session-id")
            .sequenceCounter(0)
            .timeSpan(0)
            .steps(List.of(new ScenarioStep("00A4040000", List.of("9000"))))
            .build();
    final var signedScenario = createSignedScenario(standardScenarioMessage);
    final var message = new ConnectorScenarioMessage("1.0.0", signedScenario);
    final var context = mock(ClientRequestContext.class);
    when(context.isConnectorMock()).thenReturn(true);
    when(standardScenarioProcessor.process(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(context)))
        .thenReturn(List.of("9000"));

    final var result = sut.process(message, context);

    assertThat(result).containsExactly("9000");
    final var captor = ArgumentCaptor.forClass(StandardScenarioMessage.class);
    verify(standardScenarioProcessor)
        .process(captor.capture(), org.mockito.ArgumentMatchers.eq(context));
    assertThat(captor.getValue().getVersion()).isEqualTo("1.0.0");
    assertThat(captor.getValue().getClientSessionId()).isEqualTo("session-id");
    assertThat(captor.getValue().getSteps())
        .containsExactly(new ScenarioStep("00A4040000", List.of("9000")));
    verifyNoInteractions(connectorCommunicationServiceWrapper);
  }

  @Test
  void processRejectsSignedScenarioWithoutThreeJwtParts() {
    final var message = new ConnectorScenarioMessage("1.0.0", "invalid-token");
    final var context = mock(ClientRequestContext.class);
    when(context.isConnectorMock()).thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> sut.process(message, context));
    verifyNoInteractions(connectorCommunicationServiceWrapper, standardScenarioProcessor);
  }

  @Test
  void processUsesConnectorWrapperWhenContextIsNull() {
    final var signedScenario = "header.payload.signature";
    final var message = new ConnectorScenarioMessage("1.0.0", signedScenario);
    when(connectorCommunicationServiceWrapper.secureSendApdu(signedScenario))
        .thenReturn(List.of("9000"));

    final var result = sut.process(message, null);

    assertThat(result).containsExactly("9000");
    verify(connectorCommunicationServiceWrapper).secureSendApdu(signedScenario);
    verifyNoInteractions(standardScenarioProcessor);
  }

  private String createSignedScenario(final StandardScenarioMessage standardScenarioMessage) {
    final var payload =
        """
        {"message":{"type":"StandardScenario","version":"%s","clientSessionId":"%s","sequenceCounter":%d,"timeSpan":%d,"steps":[{"commandApdu":"%s","expectedStatusWords":["%s"]}]}}
        """
            .formatted(
                standardScenarioMessage.getVersion(),
                standardScenarioMessage.getClientSessionId(),
                standardScenarioMessage.getSequenceCounter(),
                standardScenarioMessage.getTimeSpan(),
                standardScenarioMessage.getSteps().getFirst().getCommandApdu(),
                standardScenarioMessage.getSteps().getFirst().getExpectedStatusWords().getFirst())
            .replace("\n", "")
            .trim();
    final var payloadBase64 =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return "header." + payloadBase64 + ".signature";
  }
}
