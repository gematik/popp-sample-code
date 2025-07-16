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

package de.gematik.refpopp.popp_server.communication;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.handler.SessionCommunication;
import java.io.IOException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

public class WebSocketSessionCommunication implements SessionCommunication {

  private final WebSocketSession session;
  private final ObjectMapper mapper;

  public WebSocketSessionCommunication(final WebSocketSession session) {
    this.session = session;
    mapper = new ObjectMapper();
  }

  @Override
  public void sendMessage(final Object message) {
    try {
      final var textMessage = new TextMessage(mapper.writeValueAsString(message));
      session.sendMessage(textMessage);
    } catch (final IOException e) {
      throw new ScenarioException(session.getId(), e.getMessage(), "errorCode");
    }
  }

  @Override
  public String getSessionId() {
    return session.getId();
  }

  @Override
  public void closeSession() {
    try {
      session.close(CloseStatus.NORMAL);
    } catch (final IOException e) {
      throw new ScenarioException(session.getId(), e.getMessage(), "errorCode");
    }
  }
}
