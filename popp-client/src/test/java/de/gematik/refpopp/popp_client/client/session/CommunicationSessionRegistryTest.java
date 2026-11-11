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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardService;
import de.gematik.refpopp.popp_client.client.transport.SecureWebSocketClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CommunicationSessionRegistryTest {

  private CommunicationSessionRegistry sut;

  @BeforeEach
  void setUp() {
    sut = new CommunicationSessionRegistry();
  }

  @Test
  void registerRequestContextSetsCardConnectionTypeWhenProvided() {
    // given
    final var sessionId = "req1";
    sut.registerTokenWaiter(sessionId);

    // when
    final var ctx = sut.registerRequestContext(sessionId, CardConnectionType.CONTACT_CONNECTOR);

    // then
    assertThat(ctx).isNotNull();
    assertThat(ctx.getCardConnectionType()).isEqualTo(CardConnectionType.CONTACT_CONNECTOR);
    assertThat(sut.getRequestContext(sessionId)).isSameAs(ctx);
  }

  @Test
  void registerRequestContextLeavesNullWhenUnknownOrNull() {
    // given
    final var id1 = "req2";
    final var id2 = "req3";
    sut.registerTokenWaiter(id1);
    sut.registerTokenWaiter(id2);

    // when
    final var ctx1 = sut.registerRequestContext(id1, CardConnectionType.UNKNOWN);
    final var ctx2 = sut.registerRequestContext(id2, null);

    // then
    assertThat(ctx1.getCardConnectionType()).isNull();
    assertThat(ctx2.getCardConnectionType()).isNull();
  }

  @Test
  void getRequestContextReturnsNullForNullSessionId() {
    // when
    final var result = sut.getRequestContext(null);

    // then
    assertThat(result).isNull();
  }

  @Test
  void cleanupRemovesAllSessionEntries() {
    // given
    final var sessionId = "cleanup1";
    final var future = sut.registerTokenWaiter(sessionId);
    sut.registerVirtualCard(sessionId, Mockito.mock(VirtualCardService.class));
    sut.registerRequestContext(sessionId, CardConnectionType.CONTACT_STANDARD);

    // when
    sut.cleanup(sessionId);

    // then
    assertThat(sut.getVirtualCardServiceOrDefault(sessionId, null)).isNull();
    assertThat(sut.getRequestContext(sessionId)).isNull();
    assertThat(sut.hasPendingToken(sessionId)).isFalse();
    assertThat(future).isCancelled();
  }

  @Test
  void hasPendingTokenHandlesNullSessionIdAndNullCases() {
    // given / when
    final var completeResult = sut.completeToken(null, "x");
    final var failResult = sut.failToken(null, new RuntimeException());

    // then
    assertThat(completeResult).isFalse();
    assertThat(failResult).isFalse();
  }

  @Test
  void registerTokenWaiterStoresPendingTokenFuture() {
    // given
    final var sessionId = "session-id";

    // when
    final CompletableFuture<String> future = sut.registerTokenWaiter(sessionId);

    // then
    assertThat(sut.hasPendingToken(sessionId)).isTrue();
    assertThat(future).isNotDone();
    assertThat(sut.getRequestContext(sessionId).getClientSessionId()).isEqualTo(sessionId);
  }

  @Test
  void registerVirtualCardStoresServiceAndCreatesSessionState() {
    // given
    final var sessionId = "session-id";
    sut.registerTokenWaiter(sessionId);
    final var virtualCardService = Mockito.mock(VirtualCardService.class);

    // when
    sut.registerVirtualCard(sessionId, virtualCardService);

    // then
    assertThat(
            sut.getVirtualCardServiceOrDefault(sessionId, Mockito.mock(VirtualCardService.class)))
        .isSameAs(virtualCardService);
    assertThat(sut.getOrCreateVirtualCardSessionState(sessionId))
        .isSameAs(sut.getOrCreateVirtualCardSessionState(sessionId));
  }

  @Test
  void getVirtualCardServiceOrDefaultReturnsFallbackWhenNoServiceWasRegistered() {
    // given
    final var defaultService = Mockito.mock(VirtualCardService.class);

    // when
    final var result = sut.getVirtualCardServiceOrDefault("session-id", defaultService);

    // then
    assertThat(result).isSameAs(defaultService);
  }

  @Test
  void completeTokenCompletesFutureAndCleansUpEntireRequest() {
    // given
    final var sessionId = "session-id";
    final var tokenFuture = sut.registerTokenWaiter(sessionId);
    final var virtualCardService = Mockito.mock(VirtualCardService.class);
    sut.registerVirtualCard(sessionId, virtualCardService);
    final var sessionState = sut.getOrCreateVirtualCardSessionState(sessionId);
    final var defaultService = Mockito.mock(VirtualCardService.class);
    final var client = Mockito.mock(SecureWebSocketClient.class);
    sut.associatePendingTokenWithConnection(sessionId, client);

    // when
    final var completed = sut.completeToken(sessionId, "token-value");

    // then
    assertThat(completed).isTrue();
    assertThat(tokenFuture).isCompletedWithValue("token-value");
    assertThat(sut.hasPendingToken(sessionId)).isFalse();
    assertThat(sut.getVirtualCardServiceOrDefault(sessionId, defaultService))
        .isSameAs(defaultService);
    assertThat(sut.getRequestContext(sessionId)).isNull();
    assertThat(sut.getPendingSessionIdForConnection(client)).isEmpty();
    assertInactiveSessionCannotBeUpdated(sessionId, client, virtualCardService);
    sut.registerTokenWaiter(sessionId);
    assertThat(sut.getOrCreateVirtualCardSessionState(sessionId)).isNotSameAs(sessionState);
  }

  @Test
  void failTokenCompletesExceptionallyAndCleansUpEntireRequest() {
    // given
    final var sessionId = "session-id";
    final var tokenFuture = sut.registerTokenWaiter(sessionId);
    final var virtualCardService = Mockito.mock(VirtualCardService.class);
    sut.registerVirtualCard(sessionId, virtualCardService);
    final var sessionState = sut.getOrCreateVirtualCardSessionState(sessionId);
    final var defaultService = Mockito.mock(VirtualCardService.class);
    final var failure = new IllegalStateException("broken");
    final var client = Mockito.mock(SecureWebSocketClient.class);
    sut.associatePendingTokenWithConnection(sessionId, client);

    // when
    final var completed = sut.failToken(sessionId, failure);

    // then
    assertThat(completed).isTrue();
    assertThatThrownBy(tokenFuture::join).hasCause(failure);
    assertThat(sut.hasPendingToken(sessionId)).isFalse();
    assertThat(sut.getVirtualCardServiceOrDefault(sessionId, defaultService))
        .isSameAs(defaultService);
    assertThat(sut.getRequestContext(sessionId)).isNull();
    assertThat(sut.getPendingSessionIdForConnection(client)).isEmpty();
    assertInactiveSessionCannotBeUpdated(sessionId, client, virtualCardService);
    sut.registerTokenWaiter(sessionId);
    assertThat(sut.getOrCreateVirtualCardSessionState(sessionId)).isNotSameAs(sessionState);
  }

  @Test
  void failAllPendingTokensCompletesAllWaitersExceptionallyAndCleansUpSessionData() {
    final var firstSessionId = "first-session";
    final var secondSessionId = "second-session";
    final var firstFuture = sut.registerTokenWaiter(firstSessionId);
    final var secondFuture = sut.registerTokenWaiter(secondSessionId);
    sut.registerRequestContext(firstSessionId, CardConnectionType.CONTACT_STANDARD);
    sut.registerRequestContext(secondSessionId, CardConnectionType.CONTACT_CONNECTOR);
    final var firstClient = Mockito.mock(SecureWebSocketClient.class);
    final var secondClient = Mockito.mock(SecureWebSocketClient.class);
    final var service = Mockito.mock(VirtualCardService.class);
    sut.associatePendingTokenWithConnection(firstSessionId, firstClient);
    sut.associatePendingTokenWithConnection(secondSessionId, secondClient);
    sut.registerVirtualCard(firstSessionId, service);
    sut.registerVirtualCard(secondSessionId, service);
    final var failure = new IllegalStateException("connection closed");

    final var failedTokenCount = sut.failAllPendingTokens(failure);

    assertThat(failedTokenCount).isEqualTo(2);
    assertThat(firstFuture).isCompletedExceptionally();
    assertThat(secondFuture).isCompletedExceptionally();
    assertThat(sut.hasPendingToken(firstSessionId)).isFalse();
    assertThat(sut.hasPendingToken(secondSessionId)).isFalse();
    assertThat(sut.getRequestContext(firstSessionId)).isNull();
    assertThat(sut.getRequestContext(secondSessionId)).isNull();
    assertInactiveSessionCannotBeUpdated(firstSessionId, firstClient, service);
    assertInactiveSessionCannotBeUpdated(secondSessionId, secondClient, service);
  }

  @Test
  void failPendingTokensForConnectionOnlyFailsWaitersUsingThatConnection() {
    final var failedSessionId = "failed-session";
    final var activeSessionId = "active-session";
    final var failedFuture = sut.registerTokenWaiter(failedSessionId);
    final var activeFuture = sut.registerTokenWaiter(activeSessionId);
    final var failedClient = Mockito.mock(SecureWebSocketClient.class);
    final var activeClient = Mockito.mock(SecureWebSocketClient.class);
    final var virtualCardService = Mockito.mock(VirtualCardService.class);
    sut.registerVirtualCard(failedSessionId, virtualCardService);
    sut.registerVirtualCard(activeSessionId, virtualCardService);
    final var activeContext = sut.getRequestContext(activeSessionId);
    final var activeState = sut.getOrCreateVirtualCardSessionState(activeSessionId);
    sut.associatePendingTokenWithConnection(failedSessionId, failedClient);
    sut.associatePendingTokenWithConnection(activeSessionId, activeClient);

    final var failedTokenCount =
        sut.failPendingTokensForConnection(
            failedClient, new IllegalStateException("connection closed"));

    assertThat(failedTokenCount).isEqualTo(1);
    assertThat(failedFuture).isCompletedExceptionally();
    assertThat(activeFuture).isNotDone();
    assertThat(sut.hasPendingToken(activeSessionId)).isTrue();
    assertThat(sut.getRequestContext(failedSessionId)).isNull();
    assertThat(sut.getVirtualCardServiceOrDefault(failedSessionId, null)).isNull();
    assertThat(sut.getPendingSessionIdForConnection(failedClient)).isEmpty();
    assertThat(sut.getRequestContext(activeSessionId)).isSameAs(activeContext);
    assertThat(sut.getVirtualCardServiceOrDefault(activeSessionId, null))
        .isSameAs(virtualCardService);
    assertThat(sut.getOrCreateVirtualCardSessionState(activeSessionId)).isSameAs(activeState);
    assertThat(sut.getPendingSessionIdForConnection(activeClient)).contains(activeSessionId);
  }

  @Test
  void pendingSessionIsResolvedOnlyForItsOwnConnectionAndRemovedOnCompletion() {
    final var firstClient = Mockito.mock(SecureWebSocketClient.class);
    final var secondClient = Mockito.mock(SecureWebSocketClient.class);
    sut.registerTokenWaiter("first");
    sut.registerTokenWaiter("second");
    sut.associatePendingTokenWithConnection("first", firstClient);
    sut.associatePendingTokenWithConnection("second", secondClient);

    assertThat(sut.getPendingSessionIdForConnection(firstClient)).contains("first");
    assertThat(sut.getPendingSessionIdForConnection(secondClient)).contains("second");
    assertThat(sut.getPendingSessionIdForConnection(null)).isEmpty();

    sut.completeToken("first", "token");
    assertThat(sut.getPendingSessionIdForConnection(firstClient)).isEmpty();
    assertThat(sut.getPendingSessionIdForConnection(secondClient)).contains("second");

    sut.failToken("second", new IllegalStateException("failed"));
    assertThat(sut.getPendingSessionIdForConnection(secondClient)).isEmpty();
  }

  @Test
  void pendingSessionLookupRejectsMultipleWaitersOnOneConnection() {
    final var client = Mockito.mock(SecureWebSocketClient.class);
    sut.registerTokenWaiter("first");
    sut.registerTokenWaiter("second");
    sut.associatePendingTokenWithConnection("first", client);
    sut.associatePendingTokenWithConnection("second", client);

    assertThatThrownBy(() -> sut.getPendingSessionIdForConnection(client))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Multiple token requests");
  }

  @Test
  void completeTokenReturnsFalseWhenNoTokenIsRegistered() {
    // given
    final var defaultService = Mockito.mock(VirtualCardService.class);
    sut.registerTokenWaiter("session-id");
    sut.registerVirtualCard("session-id", defaultService);
    final var sessionState = sut.getOrCreateVirtualCardSessionState("session-id");

    // when
    final var completed = sut.completeToken("missing-session", "token");

    // then
    assertThat(completed).isFalse();
    assertThat(sut.getVirtualCardServiceOrDefault("session-id", defaultService))
        .isSameAs(defaultService);
    assertThat(sut.getOrCreateVirtualCardSessionState("session-id")).isSameAs(sessionState);
  }

  @Test
  void failTokenReturnsFalseWhenNoTokenIsRegistered() {
    // given
    final var sessionId = "missing-session";
    final var exception = new RuntimeException("test error");

    // when
    final var failed = sut.failToken(sessionId, exception);

    // then
    assertThat(failed).isFalse();
  }

  @Test
  void hasPendingTokenReturnsFalseForUnknownSession() {
    // given / when
    final var result = sut.hasPendingToken("unknown-session");

    // then
    assertThat(result).isFalse();
  }

  @Test
  void hasPendingTokenReturnsFalseForNullSessionId() {
    // given / when
    final var result = sut.hasPendingToken(null);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void getOrCreateVirtualCardSessionStateCreatesNewStateWhenNotExists() {
    // given
    final var sessionId = "new-session";
    sut.registerTokenWaiter(sessionId);

    // when
    final var state = sut.getOrCreateVirtualCardSessionState(sessionId);

    // then
    assertThat(state).isNotNull();
    assertThat(sut.getOrCreateVirtualCardSessionState(sessionId)).isSameAs(state);
  }

  @Test
  void registerRequestContextRetainsExistingContextAndFlags() {
    // given
    final var sessionId = "session-id";
    sut.registerTokenWaiter(sessionId);
    final var firstContext =
        sut.registerRequestContext(sessionId, CardConnectionType.CONTACT_CONNECTOR);
    firstContext.setVirtualCard(true);
    firstContext.setConnectorMock(true);

    // when
    final var secondContext =
        sut.registerRequestContext(sessionId, CardConnectionType.CONTACT_STANDARD);

    // then
    assertThat(secondContext).isSameAs(firstContext);
    assertThat(sut.getRequestContext(sessionId)).isSameAs(secondContext);
    assertThat(secondContext.getCardConnectionType())
        .isEqualTo(CardConnectionType.CONTACT_STANDARD);
    assertThat(secondContext.isVirtualCard()).isTrue();
    assertThat(secondContext.isConnectorMock()).isTrue();
  }

  @Test
  void cleanupWithMultipleRegistrationsRemovesAllData() {
    // given
    final var sessionId = "multi-session";
    final var tokenFuture = sut.registerTokenWaiter(sessionId);
    final var client = Mockito.mock(SecureWebSocketClient.class);
    sut.associatePendingTokenWithConnection(sessionId, client);
    sut.registerVirtualCard(sessionId, Mockito.mock(VirtualCardService.class));
    sut.registerRequestContext(sessionId, CardConnectionType.CONTACT_CONNECTOR);
    sut.getOrCreateVirtualCardSessionState(sessionId);

    // when
    sut.cleanup(sessionId);

    // then
    assertThat(sut.getRequestContext(sessionId)).isNull();
    assertThat(sut.getVirtualCardServiceOrDefault(sessionId, null)).isNull();
    assertThat(sut.hasPendingToken(sessionId)).isFalse();
    assertThat(sut.getPendingSessionIdForConnection(client)).isEmpty();
    assertThat(tokenFuture).isCancelled();
  }

  @Test
  void getRequestContextReturnsNullForUnknownSession() {
    // given / when
    final var result = sut.getRequestContext("unknown-session");

    // then
    assertThat(result).isNull();
  }

  @Test
  void duplicateActiveRegistrationRejectsWithoutChangingExistingRequest() {
    final var future = sut.registerTokenWaiter("session-id");
    final var context =
        sut.registerRequestContext("session-id", CardConnectionType.CONTACT_STANDARD);
    context.setVirtualCard(true);
    final var service = Mockito.mock(VirtualCardService.class);
    final var client = Mockito.mock(SecureWebSocketClient.class);
    sut.registerVirtualCard("session-id", service);
    sut.associatePendingTokenWithConnection("session-id", client);
    final var state = sut.getOrCreateVirtualCardSessionState("session-id");

    assertThatThrownBy(() -> sut.registerTokenWaiter("session-id"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already registered");

    assertThat(future).isNotDone();
    assertThat(sut.getRequestContext("session-id")).isSameAs(context);
    assertThat(context.isVirtualCard()).isTrue();
    assertThat(sut.getVirtualCardServiceOrDefault("session-id", null)).isSameAs(service);
    assertThat(sut.getOrCreateVirtualCardSessionState("session-id")).isSameAs(state);
    assertThat(sut.getPendingSessionIdForConnection(client)).contains("session-id");
    assertThat(sut.completeToken("session-id", "original-token")).isTrue();
    assertThat(future).isCompletedWithValue("original-token");
  }

  @Test
  void repeatedCompletionAttemptsDoNotChangeResultOrOtherRequests() {
    final var completed = sut.registerTokenWaiter("completed");
    final var failed = sut.registerTokenWaiter("failed");
    final var other = sut.registerTokenWaiter("other");
    final var otherContext = sut.getRequestContext("other");
    final var failure = new IllegalStateException("failed");

    assertThat(sut.completeToken("completed", "token")).isTrue();
    assertThat(sut.failToken("failed", failure)).isTrue();
    assertThat(sut.completeToken("completed", "different-token")).isFalse();
    assertThat(sut.failToken("completed", failure)).isFalse();
    assertThat(sut.completeToken("failed", "token")).isFalse();
    assertThat(sut.failToken("failed", new IllegalStateException("different"))).isFalse();

    assertThat(completed).isCompletedWithValue("token");
    assertThatThrownBy(failed::join).hasCause(failure);
    assertThat(other).isNotDone();
    assertThat(sut.getRequestContext("other")).isSameAs(otherContext);
  }

  @Test
  void requestUpdatesRequireAnActiveRegistration() {
    assertInactiveSessionCannotBeUpdated(
        "unknown",
        Mockito.mock(SecureWebSocketClient.class),
        Mockito.mock(VirtualCardService.class));
  }

  @Test
  void nullFailureDoesNotRemovePendingRequest() {
    final var future = sut.registerTokenWaiter("session-id");
    final var client = Mockito.mock(SecureWebSocketClient.class);
    sut.associatePendingTokenWithConnection("session-id", client);

    assertThatThrownBy(() -> sut.failToken("session-id", null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> sut.failAllPendingTokens(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> sut.failPendingTokensForConnection(client, null))
        .isInstanceOf(NullPointerException.class);

    assertThat(future).isNotDone();
    assertThat(sut.hasPendingToken("session-id")).isTrue();
    assertThat(sut.getPendingSessionIdForConnection(client)).contains("session-id");
  }

  @Test
  void nullOrUnknownConnectionDoesNotFailRequestsIncludingUnassociatedOnes() {
    final var future = sut.registerTokenWaiter("session-id");
    final var failure = new IllegalStateException("closed");

    assertThat(sut.failPendingTokensForConnection(null, failure)).isZero();
    assertThat(
            sut.failPendingTokensForConnection(Mockito.mock(SecureWebSocketClient.class), failure))
        .isZero();

    assertThat(future).isNotDone();
    assertThat(sut.hasPendingToken("session-id")).isTrue();
  }

  @Test
  void connectionFailureRemovesAllRequestsOnThatConnection() {
    final var client = Mockito.mock(SecureWebSocketClient.class);
    final var first = sut.registerTokenWaiter("first");
    final var second = sut.registerTokenWaiter("second");
    sut.associatePendingTokenWithConnection("first", client);
    sut.associatePendingTokenWithConnection("second", client);
    final var failure = new IllegalStateException("closed");

    assertThat(sut.failPendingTokensForConnection(client, failure)).isEqualTo(2);
    assertThatThrownBy(first::join).hasCause(failure);
    assertThatThrownBy(second::join).hasCause(failure);
    assertThat(sut.getRequestContext("first")).isNull();
    assertThat(sut.getRequestContext("second")).isNull();
    assertThat(sut.getPendingSessionIdForConnection(client)).isEmpty();
    assertThat(sut.failPendingTokensForConnection(client, failure)).isZero();
  }

  @Test
  void connectionFailureRechecksConnectionBeforeRemovingRequest() {
    final var failedClient = Mockito.mock(SecureWebSocketClient.class);
    final var newClient = Mockito.mock(SecureWebSocketClient.class);
    final var first = sut.registerTokenWaiter("first");
    final var second = sut.registerTokenWaiter("second");
    sut.associatePendingTokenWithConnection("first", failedClient);
    sut.associatePendingTokenWithConnection("second", failedClient);
    first.whenComplete(
        (token, error) -> sut.associatePendingTokenWithConnection("second", newClient));
    second.whenComplete(
        (token, error) -> sut.associatePendingTokenWithConnection("first", newClient));

    assertThat(
            sut.failPendingTokensForConnection(failedClient, new IllegalStateException("closed")))
        .isEqualTo(1);

    final var remainingId = sut.getPendingSessionIdForConnection(newClient).orElseThrow();
    assertThat(remainingId.equals("first") ? first : second).isNotDone();
    assertThat(sut.getRequestContext(remainingId)).isNotNull();
    assertThat(sut.getPendingSessionIdForConnection(failedClient)).isEmpty();
  }

  @Test
  void futureCallbackCanReuseSessionIdWithoutLosingNewRequestState() {
    final var future = sut.registerTokenWaiter("session-id");
    final var oldContext = sut.getRequestContext("session-id");
    final var service = Mockito.mock(VirtualCardService.class);
    final var client = Mockito.mock(SecureWebSocketClient.class);
    final var callback =
        future.thenApply(
            token -> {
              assertThat(sut.getRequestContext("session-id")).isNull();
              final var nextFuture = sut.registerTokenWaiter("session-id");
              sut.registerVirtualCard("session-id", service);
              sut.associatePendingTokenWithConnection("session-id", client);
              return nextFuture;
            });

    assertThat(sut.completeToken("session-id", "first-token")).isTrue();

    final var nextFuture = callback.join();
    assertThat(future).isCompletedWithValue("first-token");
    assertThat(nextFuture).isNotDone();
    assertThat(sut.getRequestContext("session-id")).isNotSameAs(oldContext);
    assertThat(sut.getVirtualCardServiceOrDefault("session-id", null)).isSameAs(service);
    assertThat(sut.getPendingSessionIdForConnection(client)).contains("session-id");
    assertThat(sut.completeToken("session-id", "second-token")).isTrue();
    assertThat(nextFuture).isCompletedWithValue("second-token");
  }

  @Test
  void bulkFailuresDoNotRemoveReusedSessionIdsFromTheirSnapshot() {
    assertBulkFailurePreservesNewRequests(false);
    sut = new CommunicationSessionRegistry();
    assertBulkFailurePreservesNewRequests(true);
  }

  private void assertBulkFailurePreservesNewRequests(final boolean connectionScoped) {
    final var client = Mockito.mock(SecureWebSocketClient.class);
    final var first = sut.registerTokenWaiter("first");
    final var second = sut.registerTokenWaiter("second");
    sut.associatePendingTokenWithConnection("first", client);
    sut.associatePendingTokenWithConnection("second", client);
    final var replacing = new AtomicBoolean();
    final var replacements = new ArrayList<CompletableFuture<String>>();
    final Runnable replace =
        () -> {
          if (replacing.compareAndSet(false, true)) {
            sut.completeToken("first", "token");
            sut.completeToken("second", "token");
            replacements.add(sut.registerTokenWaiter("first"));
            replacements.add(sut.registerTokenWaiter("second"));
            sut.associatePendingTokenWithConnection("first", client);
            sut.associatePendingTokenWithConnection("second", client);
          }
        };
    final var firstCallback = first.whenComplete((token, error) -> replace.run());
    final var secondCallback = second.whenComplete((token, error) -> replace.run());
    final var failure = new IllegalStateException("closed");

    final var failedCount =
        connectionScoped
            ? sut.failPendingTokensForConnection(client, failure)
            : sut.failAllPendingTokens(failure);

    assertThat(failedCount).isEqualTo(1);
    assertThat(firstCallback).isDone();
    assertThat(secondCallback).isDone();
    assertThat(replacements).hasSize(2).allSatisfy(future -> assertThat(future).isNotDone());
    assertThat(sut.getRequestContext("first")).isNotNull();
    assertThat(sut.getRequestContext("second")).isNotNull();
  }

  @Test
  void concurrentCompletionAndConnectionFailureFinishExactlyOnceAndLeaveOtherRequestsUntouched()
      throws Exception {
    final var client = Mockito.mock(SecureWebSocketClient.class);
    final var otherClient = Mockito.mock(SecureWebSocketClient.class);
    final var service = Mockito.mock(VirtualCardService.class);
    final var failure = new IllegalStateException("failed");
    final var otherFuture = sut.registerTokenWaiter("other");
    sut.associatePendingTokenWithConnection("other", otherClient);
    sut.registerVirtualCard("other", service);
    final var otherContext = sut.getRequestContext("other");
    final var otherState = sut.getOrCreateVirtualCardSessionState("other");

    try (final var executor = Executors.newFixedThreadPool(3)) {
      for (int attempt = 0; attempt < 100; attempt++) {
        final var future = sut.registerTokenWaiter("racing");
        sut.associatePendingTokenWithConnection("racing", client);
        sut.registerVirtualCard("racing", service);
        final List<Integer> results =
            race(
                executor,
                List.of(
                    () -> sut.completeToken("racing", "token") ? 1 : 0,
                    () -> sut.failToken("racing", failure) ? 1 : 0,
                    () -> sut.failPendingTokensForConnection(client, failure)));

        assertThat(results.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
        if (future.isCompletedExceptionally()) {
          assertThatThrownBy(future::join).hasCause(failure);
        } else {
          assertThat(future).isCompletedWithValue("token");
        }
        assertThat(sut.hasPendingToken("racing")).isFalse();
        assertThat(sut.getRequestContext("racing")).isNull();
        assertThat(sut.getVirtualCardServiceOrDefault("racing", null)).isNull();
        assertThat(sut.getPendingSessionIdForConnection(client)).isEmpty();
        assertThat(otherFuture).isNotDone();
        assertThat(sut.getRequestContext("other")).isSameAs(otherContext);
        assertThat(sut.getOrCreateVirtualCardSessionState("other")).isSameAs(otherState);
        assertThat(sut.getPendingSessionIdForConnection(otherClient)).contains("other");
      }
    }
  }

  @Test
  void concurrentGlobalFailureAndCompletionFinishExactlyOnce() throws Exception {
    final var failure = new IllegalStateException("shutdown");
    try (final var executor = Executors.newFixedThreadPool(2)) {
      for (int attempt = 0; attempt < 100; attempt++) {
        final var future = sut.registerTokenWaiter("racing");

        final List<Integer> results =
            race(
                executor,
                List.of(
                    () -> sut.completeToken("racing", "token") ? 1 : 0,
                    () -> sut.failAllPendingTokens(failure)));

        assertThat(results.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
        assertThat(future).isDone();
        assertThat(sut.hasPendingToken("racing")).isFalse();
        assertThat(sut.getRequestContext("racing")).isNull();
      }
    }
  }

  @Test
  void concurrentRegistrationsKeepExactlyOneWaiter() throws Exception {
    try (final var executor = Executors.newFixedThreadPool(2)) {
      for (int attempt = 0; attempt < 100; attempt++) {
        final Callable<CompletableFuture<String>> register =
            () -> {
              try {
                return sut.registerTokenWaiter("racing");
              } catch (IllegalStateException expected) {
                assertThat(expected).hasMessageContaining("already registered");
                return null;
              }
            };
        final var results = race(executor, List.of(register, register));
        final var waiters = results.stream().filter(java.util.Objects::nonNull).toList();

        assertThat(waiters).hasSize(1);
        assertThat(sut.completeToken("racing", "token")).isTrue();
        assertThat(waiters.getFirst()).isCompletedWithValue("token");
        assertThat(sut.getRequestContext("racing")).isNull();
      }
    }
  }

  @Test
  void concurrentResourceRegistrationAndCompletionCannotRecreateSessionState() throws Exception {
    final var client = Mockito.mock(SecureWebSocketClient.class);
    final var service = Mockito.mock(VirtualCardService.class);
    try (final var executor = Executors.newFixedThreadPool(2)) {
      for (int attempt = 0; attempt < 100; attempt++) {
        final var future = sut.registerTokenWaiter("racing");
        race(
            executor,
            List.of(
                () -> sut.completeToken("racing", "token"),
                () -> {
                  try {
                    sut.registerRequestContext("racing", CardConnectionType.CONTACT_STANDARD);
                    sut.registerVirtualCard("racing", service);
                    sut.getOrCreateVirtualCardSessionState("racing");
                    sut.associatePendingTokenWithConnection("racing", client);
                  } catch (IllegalStateException expected) {
                    assertThat(expected).hasMessageContaining("No pending token request");
                  }
                  return true;
                }));

        assertThat(future).isCompletedWithValue("token");
        assertThat(sut.hasPendingToken("racing")).isFalse();
        assertThat(sut.getRequestContext("racing")).isNull();
        assertThat(sut.getVirtualCardServiceOrDefault("racing", null)).isNull();
        assertThat(sut.getPendingSessionIdForConnection(client)).isEmpty();
        assertInactiveSessionCannotBeUpdated("racing", client, service);
      }
    }
  }

  private void assertInactiveSessionCannotBeUpdated(
      final String sessionId,
      final SecureWebSocketClient client,
      final VirtualCardService service) {
    assertThatThrownBy(
            () -> sut.registerRequestContext(sessionId, CardConnectionType.CONTACT_STANDARD))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> sut.registerVirtualCard(sessionId, service))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> sut.getOrCreateVirtualCardSessionState(sessionId))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> sut.associatePendingTokenWithConnection(sessionId, client))
        .isInstanceOf(IllegalStateException.class);
    assertThat(sut.hasPendingToken(sessionId)).isFalse();
    assertThat(sut.getRequestContext(sessionId)).isNull();
    assertThat(sut.getVirtualCardServiceOrDefault(sessionId, null)).isNull();
    assertThat(sut.getPendingSessionIdForConnection(client)).isEmpty();
  }

  private <T> List<T> race(final ExecutorService executor, final List<Callable<T>> actions)
      throws Exception {
    final var barrier = new CyclicBarrier(actions.size());
    final var tasks =
        actions.stream()
            .map(
                action ->
                    executor.submit(
                        () -> {
                          barrier.await(5, TimeUnit.SECONDS);
                          return action.call();
                        }))
            .toList();
    final var results = new ArrayList<T>();
    for (final var task : tasks) {
      results.add(task.get(5, TimeUnit.SECONDS));
    }
    return results;
  }
}
