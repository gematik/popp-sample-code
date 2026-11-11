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

package de.gematik.refpopp.popp_client.client.session;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardService;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardSessionState;
import de.gematik.refpopp.popp_client.client.transport.SecureWebSocketClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Maintains session-scoped state for PoPP token requests.
 *
 * <p>Each active {@code clientSessionId} owns one session containing its token waiter, connection,
 * request context, and optional virtual-card resources. Registry operations synchronize on the
 * session map. Completion atomically removes the whole session before notifying the waiter, so
 * callbacks run outside the registry lock and cannot clean up a subsequent request.
 *
 * <p>This does not change the sequential processing of requests over the shared WebSocket
 * connection.
 */
@Component
public class CommunicationSessionRegistry {

  private final Map<String, RequestSession> sessions = new HashMap<>();

  /**
   * Starts a request. An active session ID cannot be registered again: the existing request remains
   * unchanged and an {@link IllegalStateException} is thrown. Completed IDs may be reused.
   */
  public CompletableFuture<String> registerTokenWaiter(final String sessionId) {
    Objects.requireNonNull(sessionId, "sessionId");
    synchronized (sessions) {
      if (sessions.containsKey(sessionId)) {
        throw new IllegalStateException(
            "Token request already registered for session " + sessionId);
      }
      final var session = new RequestSession(sessionId);
      sessions.put(sessionId, session);
      return session.tokenFuture;
    }
  }

  /**
   * Initializes the existing request context without replacing it. The token waiter must be
   * registered first; updates after completion are rejected rather than recreating session state.
   */
  public ClientRequestContext registerRequestContext(
      final String sessionId, final CardConnectionType cardConnectionType) {
    synchronized (sessions) {
      final var context = requireSession(sessionId).context;
      if (cardConnectionType != null && cardConnectionType != CardConnectionType.UNKNOWN) {
        context.setCardConnectionType(cardConnectionType);
      }
      return context;
    }
  }

  public ClientRequestContext getRequestContext(final String sessionId) {
    synchronized (sessions) {
      final var session = sessions.get(sessionId);
      return session == null ? null : session.context;
    }
  }

  public void registerVirtualCard(
      final String sessionId, final VirtualCardService virtualCardService) {
    Objects.requireNonNull(virtualCardService, "virtualCardService");
    synchronized (sessions) {
      final var session = requireSession(sessionId);
      session.virtualCardService = virtualCardService;
      session.virtualCardSessionState = new VirtualCardSessionState();
    }
  }

  public VirtualCardService getVirtualCardServiceOrDefault(
      final String sessionId, final VirtualCardService defaultVirtualCardService) {
    synchronized (sessions) {
      final var session = sessions.get(sessionId);
      return session == null || session.virtualCardService == null
          ? defaultVirtualCardService
          : session.virtualCardService;
    }
  }

  public VirtualCardSessionState getOrCreateVirtualCardSessionState(final String sessionId) {
    synchronized (sessions) {
      final var session = requireSession(sessionId);
      if (session.virtualCardSessionState == null) {
        session.virtualCardSessionState = new VirtualCardSessionState();
      }
      return session.virtualCardSessionState;
    }
  }

  public boolean completeToken(final String sessionId, final String token) {
    return finishToken(sessionId, session -> true, future -> future.complete(token));
  }

  public boolean failToken(final String sessionId, final Throwable throwable) {
    Objects.requireNonNull(throwable, "throwable");
    return finishToken(
        sessionId, session -> true, future -> future.completeExceptionally(throwable));
  }

  public int failAllPendingTokens(final Throwable throwable) {
    Objects.requireNonNull(throwable, "throwable");
    int failedTokens = 0;
    for (final var session : pendingSessions()) {
      if (finishToken(
          session.context.getClientSessionId(),
          current -> current == session,
          future -> future.completeExceptionally(throwable))) {
        failedTokens++;
      }
    }
    return failedTokens;
  }

  public void associatePendingTokenWithConnection(
      final String sessionId, final SecureWebSocketClient client) {
    if (client != null) {
      synchronized (sessions) {
        requireSession(sessionId).connection = client;
      }
    }
  }

  public Optional<String> getPendingSessionIdForConnection(final SecureWebSocketClient client) {
    if (client == null) {
      return Optional.empty();
    }
    synchronized (sessions) {
      final var pending =
          sessions.values().stream()
              .filter(session -> session.connection == client)
              .map(session -> session.context.getClientSessionId())
              .toList();
      if (pending.size() > 1) {
        throw new IllegalStateException(
            "Multiple token requests pending on one WebSocket connection");
      }
      return pending.stream().findFirst();
    }
  }

  public int failPendingTokensForConnection(
      final SecureWebSocketClient client, final Throwable throwable) {
    Objects.requireNonNull(throwable, "throwable");
    if (client == null) {
      return 0;
    }
    int failedTokens = 0;
    for (final var session : pendingSessions()) {
      if (finishToken(
          session.context.getClientSessionId(),
          current -> current == session && current.connection == client,
          future -> future.completeExceptionally(throwable))) {
        failedTokens++;
      }
    }
    return failedTokens;
  }

  /** Cancels an active request and removes all its state without abandoning its waiter. */
  public void cleanup(final String sessionId) {
    failToken(sessionId, new CancellationException("Token request cleaned up"));
  }

  public boolean hasPendingToken(final String sessionId) {
    synchronized (sessions) {
      return sessions.containsKey(sessionId);
    }
  }

  private RequestSession requireSession(final String sessionId) {
    final var session = sessions.get(sessionId);
    if (session == null) {
      throw new IllegalStateException("No pending token request for session " + sessionId);
    }
    return session;
  }

  private List<RequestSession> pendingSessions() {
    synchronized (sessions) {
      return List.copyOf(sessions.values());
    }
  }

  private boolean finishToken(
      final String sessionId,
      final Predicate<RequestSession> matches,
      final Consumer<CompletableFuture<String>> completion) {
    final RequestSession session;
    synchronized (sessions) {
      session = sessions.get(sessionId);
      if (session == null || !matches.test(session)) {
        return false;
      }
      sessions.remove(sessionId);
    }
    completion.accept(session.tokenFuture);
    return true;
  }

  private static final class RequestSession {
    private final CompletableFuture<String> tokenFuture = new CompletableFuture<>();
    private final ClientRequestContext context;
    private SecureWebSocketClient connection;
    private VirtualCardService virtualCardService;
    private VirtualCardSessionState virtualCardSessionState;

    private RequestSession(final String sessionId) {
      context = new ClientRequestContext(sessionId);
    }
  }
}
