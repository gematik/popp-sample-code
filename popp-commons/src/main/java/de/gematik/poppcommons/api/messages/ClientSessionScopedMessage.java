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

package de.gematik.poppcommons.api.messages;

/**
 * Implemented by messages that carry a {@code clientSessionId}. The value correlates a single
 * (potentially parallel) token request across the client/server message exchange. On the server it
 * is used - together with the transport (WebSocket) session id - to derive a logical session id so
 * that state of concurrent requests over the same connection does not collide.
 */
public interface ClientSessionScopedMessage {

  String getClientSessionId();
}
