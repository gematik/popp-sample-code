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

package de.gematik.refpopp.popp_server.configuration;

import de.gematik.refpopp.popp_server.handler.WebSocketHandler;
import de.gematik.refpopp.popp_server.scenario.common.orchestrator.MessageHandlerOrchestrator;
import de.gematik.refpopp.popp_server.scenario.common.orchestrator.MessageOrchestrator;
import de.gematik.refpopp.popp_server.scenario.common.provider.CardScenarioProvider;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSocket
@Slf4j
public class WebSocketConfig implements WebSocketConfigurer {

  private final SessionContainer sessionContainer;
  private final MessageOrchestrator egkOrchestrator;
  private final ObjectMapper objectMapper;

  private final CardScenarioProvider scenarioProvider;

  /** Number of worker threads processing incoming WebSocket messages concurrently. */
  @Value("${popp-server.message-processing.pool-size:16}")
  private int messageProcessingPoolSize;

  /** Max time (ms) an outbound send may take before the session is closed as too slow. */
  @Value("${popp-server.message-processing.send-time-limit-ms:20000}")
  private int sendTimeLimitMs;

  /** Max buffered outbound bytes per connection before the session is closed. */
  @Value("${popp-server.message-processing.send-buffer-size-limit-bytes:524288}")
  private int sendBufferSizeLimit;

  public WebSocketConfig(
      final SessionContainer sessionContainer,
      final MessageHandlerOrchestrator egkOrchestrator,
      final ObjectMapper objectMapper,
      final CardScenarioProvider scenarioProvider) {
    this.sessionContainer = sessionContainer;
    this.egkOrchestrator = egkOrchestrator;
    this.objectMapper = objectMapper;
    this.scenarioProvider = scenarioProvider;
  }

  @Override
  public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {
    log.debug("| Entering registerWebSocketHandlers()");
    registry.addHandler(webSocketHandler(), "/popp/practitioner/api/v1/token-generation-ehc");
    log.debug("| Exiting registerWebSocketHandlers()");
  }

  @Bean(destroyMethod = "shutdown")
  WebSocketHandler webSocketHandler() {
    return new WebSocketHandler(
        sessionContainer,
        egkOrchestrator,
        objectMapper,
        scenarioProvider,
        messageProcessingPoolSize,
        sendTimeLimitMs,
        sendBufferSizeLimit);
  }
}
