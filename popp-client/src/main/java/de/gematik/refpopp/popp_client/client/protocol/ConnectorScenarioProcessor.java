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

import de.gematik.poppcommons.api.messages.ConnectorScenarioMessage;
import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.refpopp.popp_client.client.session.ClientRequestContext;
import de.gematik.refpopp.popp_client.connector.ConnectorCommunicationServiceWrapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ConnectorScenarioProcessor {

  private final ObjectMapper mapper;
  private final ConnectorCommunicationServiceWrapper connectorCommunicationServiceWrapper;
  private final StandardScenarioProcessor standardScenarioProcessor;

  public List<String> process(
      final ConnectorScenarioMessage connectorScenarioMessage, final ClientRequestContext context) {
    final var signedScenario = connectorScenarioMessage.getSignedScenario();
    if (context != null && context.isConnectorMock()) {
      return useStandardTerminalAsMock(signedScenario, context);
    }
    return connectorCommunicationServiceWrapper.secureSendApdu(signedScenario);
  }

  private List<String> useStandardTerminalAsMock(
      final String signedScenario, final ClientRequestContext context) {
    final String[] tokenParts = signedScenario.split("\\.");
    if (tokenParts.length != 3) {
      throw new IllegalArgumentException("Invalid token format");
    }

    final var payloadJson =
        new String(Base64.getUrlDecoder().decode(tokenParts[1]), StandardCharsets.UTF_8);
    final var claims = mapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
    final var message = mapper.convertValue(claims.get("message"), StandardScenarioMessage.class);
    return standardScenarioProcessor.process(message, context);
  }
}
