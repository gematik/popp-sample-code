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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Maintains session-scoped state for PoPP token requests.
 *
 * <p>The registry correlates token waiters, request contexts, and virtual-card resources by {@code
 * clientSessionId}. It completes or fails the respective token waiter when processing finishes and
 * removes the associated session state afterwards.
 */
@Component
public class CommunicationSessionRegistry {

  private final ConcurrentMap<String, CompletableFuture<String>> tokenQueue =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, SecureWebSocketClient> tokenConnections =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, VirtualCardService> virtualCardServicesBySession =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, VirtualCardSessionState> virtualCardSessionStatesBySession =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ClientRequestContext> requestContextsBySession =
      new ConcurrentHashMap<>();

  public CompletableFuture<String> registerTokenWaiter(final String sessionId) {
    final var tokenFuture = new CompletableFuture<String>();
    tokenQueue.put(sessionId, tokenFuture);
    return tokenFuture;
  }

  /**
   * Registers (or replaces) the request-scoped context for the given {@code clientSessionId}. The
   * context isolates the state of a single token request.
   */
  public ClientRequestContext registerRequestContext(
      final String sessionId, final CardConnectionType cardConnectionType) {
    final var context = new ClientRequestContext(sessionId);
    if (cardConnectionType != null && cardConnectionType != CardConnectionType.UNKNOWN) {
      context.setCardConnectionType(cardConnectionType);
    }
    requestContextsBySession.put(sessionId, context);
    return context;
  }

  public ClientRequestContext getRequestContext(final String sessionId) {
    if (sessionId == null) {
      return null;
    }
    return requestContextsBySession.get(sessionId);
  }

  public void registerVirtualCard(
      final String sessionId, final VirtualCardService virtualCardService) {
    virtualCardServicesBySession.put(sessionId, virtualCardService);
    virtualCardSessionStatesBySession.put(sessionId, new VirtualCardSessionState());
  }

  public VirtualCardService getVirtualCardServiceOrDefault(
      final String sessionId, final VirtualCardService defaultVirtualCardService) {
    return virtualCardServicesBySession.getOrDefault(sessionId, defaultVirtualCardService);
  }

  public VirtualCardSessionState getOrCreateVirtualCardSessionState(final String sessionId) {
    return virtualCardSessionStatesBySession.computeIfAbsent(
        sessionId, ignored -> new VirtualCardSessionState());
  }

  public boolean completeToken(final String sessionId, final String token) {
    if (sessionId == null) {
      return false;
    }
    final var tokenFuture = tokenQueue.remove(sessionId);
    tokenConnections.remove(sessionId);
    cleanup(sessionId);
    if (tokenFuture == null) {
      return false;
    }
    tokenFuture.complete(token);
    return true;
  }

  public boolean failToken(final String sessionId, final Throwable throwable) {
    if (sessionId == null) {
      return false;
    }
    final var tokenFuture = tokenQueue.remove(sessionId);
    tokenConnections.remove(sessionId);
    cleanup(sessionId);
    if (tokenFuture == null) {
      return false;
    }
    tokenFuture.completeExceptionally(throwable);
    return true;
  }

  public int failAllPendingTokens(final Throwable throwable) {
    int failedTokens = 0;
    for (final var entry : tokenQueue.entrySet()) {
      final var sessionId = entry.getKey();
      final var tokenFuture = entry.getValue();
      if (tokenQueue.remove(sessionId, tokenFuture)) {
        tokenConnections.remove(sessionId);
        cleanup(sessionId);
        tokenFuture.completeExceptionally(throwable);
        failedTokens++;
      }
    }
    return failedTokens;
  }

  public void associatePendingTokenWithConnection(
      final String sessionId, final SecureWebSocketClient client) {
    if (client != null && tokenQueue.containsKey(sessionId)) {
      tokenConnections.put(sessionId, client);
    }
  }

  public Optional<String> getPendingSessionIdForConnection(final SecureWebSocketClient client) {
    if (client == null) {
      return Optional.empty();
    }
    final var pending =
        tokenConnections.entrySet().stream()
            .filter(entry -> entry.getValue() == client && tokenQueue.containsKey(entry.getKey()))
            .map(Map.Entry::getKey)
            .toList();
    if (pending.size() > 1) {
      throw new IllegalStateException(
          "Multiple token requests pending on one WebSocket connection");
    }
    return pending.stream().findFirst();
  }

  public int failPendingTokensForConnection(
      final SecureWebSocketClient client, final Throwable throwable) {
    if (client == null) {
      return 0;
    }
    int failedTokens = 0;
    for (final var entry : tokenConnections.entrySet()) {
      final var sessionId = entry.getKey();
      if (entry.getValue() == client) {
        final var tokenFuture = tokenQueue.remove(sessionId);
        tokenConnections.remove(sessionId, client);
        if (tokenFuture != null) {
          cleanup(sessionId);
          tokenFuture.completeExceptionally(throwable);
          failedTokens++;
        }
      }
    }
    return failedTokens;
  }

  public void cleanup(final String sessionId) {
    virtualCardServicesBySession.remove(sessionId);
    virtualCardSessionStatesBySession.remove(sessionId);
    requestContextsBySession.remove(sessionId);
  }

  public boolean hasPendingToken(final String sessionId) {
    if (sessionId == null) {
      return false;
    }
    return tokenQueue.containsKey(sessionId);
  }
}
