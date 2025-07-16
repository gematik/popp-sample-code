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

package de.gematik.refpopp.popp_client.configuration;

import de.gematik.refpopp.popp_client.client.CommunicationEventPublisher;
import de.gematik.refpopp.popp_client.client.SecureWebSocketClient;
import java.net.URI;
import org.java_websocket.client.WebSocketClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class WebSocketConfig {

  private final CommunicationEventPublisher eventPublisher;

  public WebSocketConfig(final CommunicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  @Bean
  @Scope("prototype")
  public WebSocketClient secureWebSocketClient(
      @Value("${popp-client.secure.key-store}") final String keyStore,
      @Value("${popp-client.secure.store-password}") final String keystorePassword,
      @Value("${popp-server.url}") final String serverUrl) {
    return new SecureWebSocketClient(
        URI.create(serverUrl), eventPublisher, keyStore, keystorePassword);
  }
}
