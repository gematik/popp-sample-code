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

package de.gematik.refpopp.popp_server.handler;

/**
 * Wraps a transport {@link SessionCommunication} and exposes a logical session id as {@link
 * #getSessionId()}. This makes all downstream state access request scoped (one entry per {@code
 * clientSessionId}) while sending still goes through the shared underlying WebSocket connection.
 */
public class RequestScopedSessionCommunication implements SessionCommunication {

  private final SessionCommunication delegate;
  private final String logicalSessionId;

  public RequestScopedSessionCommunication(
      final SessionCommunication delegate, final String logicalSessionId) {
    this.delegate = delegate;
    this.logicalSessionId = logicalSessionId;
  }

  @Override
  public void sendMessage(final Object message) {
    delegate.sendMessage(message);
  }

  @Override
  public String getSessionId() {
    return logicalSessionId;
  }

  @Override
  public String getTransportSessionId() {
    return delegate.getTransportSessionId();
  }

  @Override
  public void closeSession() {
    delegate.closeSession();
  }
}
