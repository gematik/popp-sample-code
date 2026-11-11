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

package de.gematik.refpopp.popp_client.connector.session;

import de.gematik.refpopp.popp_client.connector.ConnectorCommunicationServiceWrapper;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.ws.soap.client.SoapFaultClientException;

/**
 * Manages the lifecycle of connector card sessions for PoPP requests.
 *
 * <p>The caller owns the returned session and closes it with try-with-resources after the complete
 * token request, including failed initialization, timeout, and interruption. The handle retains the
 * connector session ID independently of the protocol request context and closes it at most once.
 * Already closed or cancelled sessions are handled as normal shutdown conditions.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ConnectorSessionLifecycle {

  private final ConnectorCommunicationServiceWrapper connectorCommunicationServiceWrapper;

  public ConnectorSession startSession(final String patientId) {
    final var sessionId =
        connectorCommunicationServiceWrapper.startCardSession(
            connectorCommunicationServiceWrapper.getConnectedEgkCard(patientId));
    return new ConnectorSession(sessionId);
  }

  private void stopSession(final String clientSessionId) {
    try {
      connectorCommunicationServiceWrapper.stopCardSession(clientSessionId);
    } catch (CancellationException e) {
      log.info(
          "| StopCardSession was cancelled, probably because the session is already shutting"
              + " down: {}",
          clientSessionId);
    } catch (SoapFaultClientException exception) {
      final String faultString = exception.getFaultStringOrReason();
      final boolean unknownSession =
          faultString != null && faultString.contains("Unbekannte Session ID");
      if (!unknownSession) {
        throw exception;
      }
      log.info("| Session {} is already closed.", clientSessionId);
    }
  }

  public final class ConnectorSession implements AutoCloseable {

    @Getter private final String sessionId;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ConnectorSession(final String sessionId) {
      this.sessionId = sessionId;
    }

    @Override
    public void close() {
      if (!closed.compareAndSet(false, true)) {
        return;
      }
      // Allow the connector call to run even when token waiting was interrupted.
      final boolean interrupted = Thread.interrupted();
      try {
        stopSession(sessionId);
      } catch (RuntimeException e) {
        log.error("| Failed to close connector session {}", sessionId, e);
        throw e;
      } finally {
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }
}
