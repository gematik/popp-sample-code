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

package de.gematik.refpopp.popp_server.contract;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.ConnectorScenarioMessage;
import de.gematik.poppcommons.api.messages.ErrorMessage;
import de.gematik.poppcommons.api.messages.PoPPMessage;
import de.gematik.poppcommons.api.messages.ScenarioResponseMessage;
import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.poppcommons.api.messages.StartMessage;
import de.gematik.poppcommons.api.messages.TokenMessage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class V1WebSocketMessageContractTest {

  private static final String CLIENT_SESSION_ID = "123e4567-e89b-12d3-a456-426614174000";

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void startContainsClientSessionId() {
    final var json =
        """
        {
          "type": "Start",
          "version": "1.0.0",
          "cardConnectionType": "contact-standard",
          "clientSessionId": "123e4567-e89b-12d3-a456-426614174000"
        }
        """;

    final var message = readAndRoundTrip(json, StartMessage.class);

    assertThat(message.getClientSessionId()).isEqualTo(CLIENT_SESSION_ID);
    assertThat(message.getCardConnectionType()).isEqualTo(CardConnectionType.CONTACT_STANDARD);
  }

  @Test
  void standardScenarioEchoesClientSessionId() {
    final var json =
        """
        {
          "type": "StandardScenario",
          "version": "1.0.0",
          "clientSessionId": "123e4567-e89b-12d3-a456-426614174000",
          "sequenceCounter": 0,
          "timeSpan": 1000,
          "steps": [
            {
              "commandApdu": "00a4040c",
              "expectedStatusWords": ["9000", "6f00"]
            }
          ]
        }
        """;

    final var message = readAndRoundTrip(json, StandardScenarioMessage.class);

    assertThat(message.getClientSessionId()).isEqualTo(CLIENT_SESSION_ID);
    assertThat(message.getSteps()).hasSize(1);
  }

  @Test
  void scenarioResponseHasNoClientSessionId() {
    final var json =
        """
        {
          "type": "ScenarioResponse",
          "steps": ["9000", "abcd9000", "11223344559000", "6a81"]
        }
        """;

    final var message = readAndRoundTrip(json, ScenarioResponseMessage.class);

    assertThat(message.getSteps()).containsExactly("9000", "abcd9000", "11223344559000", "6a81");
  }

  @Test
  void scenarioResponseMayContainNoSteps() {
    final var json =
        """
        {
          "type": "ScenarioResponse",
          "steps": []
        }
        """;

    final var message = readAndRoundTrip(json, ScenarioResponseMessage.class);

    assertThat(message.getSteps()).isEmpty();
  }

  @Test
  void connectorScenarioHasNoOuterClientSessionId() {
    final var json =
        """
        {
          "type": "ConnectorScenario",
          "version": "1.0.0",
          "signedScenario": "eeDDD.ddf.sd"
        }
        """;

    final var message = readAndRoundTrip(json, ConnectorScenarioMessage.class);

    assertThat(message.getSignedScenario()).isEqualTo("eeDDD.ddf.sd");
  }

  @Test
  void tokenHasOnlyContractFields() {
    final var json =
        """
        {
          "type": "Token",
          "token": "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature"
        }
        """;

    final var message = readAndRoundTrip(json, TokenMessage.class);

    assertThat(message.getToken()).isEqualTo("eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature");
  }

  @Test
  void errorHasOnlyContractFields() {
    final var json =
        """
        {
          "type": "Error",
          "errorCode": "79511"
        }
        """;

    final var message = readAndRoundTrip(json, ErrorMessage.class);

    assertThat(message.getErrorCode()).isEqualTo("79511");
    assertThat(message.getErrorDetail()).isNull();
  }

  private <T extends PoPPMessage> T readAndRoundTrip(final String json, final Class<T> type) {
    final var message = mapper.readValue(json, PoPPMessage.class);
    assertThat(message).isInstanceOf(type);
    assertThat(mapper.readTree(mapper.writeValueAsString(message)))
        .isEqualTo(mapper.readTree(json));
    return type.cast(message);
  }
}
