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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class CommunicationSessionRegistry {

  private final ConcurrentMap<String, CompletableFuture<String>> tokenQueue =
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
   * context isolates the state of a single (potentially parallel) token request over a shared
   * WebSocket connection.
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
    cleanup(sessionId);
    if (tokenFuture == null) {
      return false;
    }
    tokenFuture.complete(token);
    return true;
  }

  /**
   * Fallback correlation for servers that do not echo back the client's {@code clientSessionId} (or
   * send none at all) in the token message. If exactly one token request is currently in flight,
   * its waiter is completed regardless of the key so that the calling thread is not stuck until it
   * times out. Returns {@code false} if there is zero or more than one pending request, because in
   * that case the token cannot be attributed unambiguously.
   */
  public boolean completeSolePendingToken(final String token) {
    if (tokenQueue.size() != 1) {
      return false;
    }
    final var soleSessionId = tokenQueue.keySet().stream().findFirst().orElseThrow();
    final var tokenFuture = tokenQueue.remove(soleSessionId);
    cleanup(soleSessionId);
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
    cleanup(sessionId);
    if (tokenFuture == null) {
      return false;
    }
    tokenFuture.completeExceptionally(throwable);
    return true;
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
