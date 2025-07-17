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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.gematik.refpopp.popp_server.handler.WebSocketHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

  @InjectMocks private WebSocketConfig webSocketConfig;

  @Test
  void registerWebSocketHandlersRegistersHandler() {
    // given
    final var registry = mock(WebSocketHandlerRegistry.class);

    // when
    webSocketConfig.registerWebSocketHandlers(registry);

    // then
    verify(registry).addHandler(any(WebSocketHandler.class), eq("/ws"));
  }

  @Test
  void webSocketHandlerReturnsNewInstance() {
    // given

    // when
    final var handler = webSocketConfig.webSocketHandler();

    // then
    assertThat(handler).isNotNull().isInstanceOf(WebSocketHandler.class);
  }
}
