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

import de.gematik.poppcommons.api.messages.PoPPMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MessageHandlerProvider {

  private final Map<Class<? extends PoPPMessage>, MessageHandler<? extends PoPPMessage>> handlerMap;

  public MessageHandlerProvider(final List<MessageHandler<? extends PoPPMessage>> handlers) {
    this.handlerMap = new HashMap<>();
    for (final MessageHandler<? extends PoPPMessage> handler : handlers) {
      handlerMap.put(handler.getMessageType(), handler);
    }
  }

  public <T extends PoPPMessage> MessageHandler<T> getHandlerFor(final T message) {
    @SuppressWarnings("unchecked")
    final MessageHandler<T> handler = (MessageHandler<T>) handlerMap.get(message.getClass());
    return handler;
  }
}
