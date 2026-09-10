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

package de.gematik.refpopp.popp_server.sessionmanagement;

/**
 * Helper for building and parsing the logical session id used as the server side state key.
 *
 * <p>A logical session id scopes state to a single (potentially parallel) token request. It is
 * composed of the transport (WebSocket) session id and the request's {@code clientSessionId}:
 *
 * <pre>logicalSessionId = transportSessionId + "::" + clientSessionId</pre>
 *
 * <p>Including the transport id prevents collisions when two different connections happen to use
 * the same {@code clientSessionId}. Connection scoped data (e.g. ZETA user info) stays keyed by the
 * plain transport session id.
 */
public final class LogicalSessionId {

  public static final String SEPARATOR = "::";

  private LogicalSessionId() {}

  public static String of(final String transportSessionId, final String clientSessionId) {
    return transportSessionId + SEPARATOR + clientSessionId;
  }

  public static String transportPrefix(final String transportSessionId) {
    return transportSessionId + SEPARATOR;
  }

  public static boolean isLogical(final String sessionId) {
    return sessionId != null && sessionId.contains(SEPARATOR);
  }

  public static String clientSessionIdOf(final String logicalSessionId) {
    if (!isLogical(logicalSessionId)) {
      return null;
    }
    return logicalSessionId.substring(logicalSessionId.indexOf(SEPARATOR) + SEPARATOR.length());
  }
}
