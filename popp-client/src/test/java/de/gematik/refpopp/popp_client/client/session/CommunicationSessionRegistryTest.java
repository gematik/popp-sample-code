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

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardService;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardSessionState;
import java.util.concurrent.CompletableFuture;
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
    sut.registerVirtualCard(sessionId, Mockito.mock(VirtualCardService.class));
    sut.registerRequestContext(sessionId, CardConnectionType.CONTACT_STANDARD);

    // when
    sut.cleanup(sessionId);

    // then
    assertThat(sut.getVirtualCardServiceOrDefault(sessionId, null)).isNull();
    assertThat(sut.getRequestContext(sessionId)).isNull();
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
  }

  @Test
  void registerVirtualCardStoresServiceAndCreatesSessionState() {
    // given
    final var sessionId = "session-id";
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
  void completeTokenCompletesFutureAndCleansUpVirtualCardData() {
    // given
    final var sessionId = "session-id";
    final var tokenFuture = sut.registerTokenWaiter(sessionId);
    final var virtualCardService = Mockito.mock(VirtualCardService.class);
    sut.registerVirtualCard(sessionId, virtualCardService);
    final var sessionState = sut.getOrCreateVirtualCardSessionState(sessionId);
    final var defaultService = Mockito.mock(VirtualCardService.class);

    // when
    final var completed = sut.completeToken(sessionId, "token-value");

    // then
    assertThat(completed).isTrue();
    assertThat(tokenFuture).isCompletedWithValue("token-value");
    assertThat(sut.hasPendingToken(sessionId)).isFalse();
    assertThat(sut.getVirtualCardServiceOrDefault(sessionId, defaultService))
        .isSameAs(defaultService);
    assertThat(sut.getOrCreateVirtualCardSessionState(sessionId)).isNotSameAs(sessionState);
  }

  @Test
  void failTokenCompletesExceptionallyAndCleansUpVirtualCardData() {
    // given
    final var sessionId = "session-id";
    final var tokenFuture = sut.registerTokenWaiter(sessionId);
    final var virtualCardService = Mockito.mock(VirtualCardService.class);
    sut.registerVirtualCard(sessionId, virtualCardService);
    final var sessionState = sut.getOrCreateVirtualCardSessionState(sessionId);
    final var defaultService = Mockito.mock(VirtualCardService.class);
    final var failure = new IllegalStateException("broken");

    // when
    final var completed = sut.failToken(sessionId, failure);

    // then
    assertThat(completed).isTrue();
    assertThat(tokenFuture).isCompletedExceptionally();
    assertThat(sut.hasPendingToken(sessionId)).isFalse();
    assertThat(sut.getVirtualCardServiceOrDefault(sessionId, defaultService))
        .isSameAs(defaultService);
    assertThat(sut.getOrCreateVirtualCardSessionState(sessionId)).isNotSameAs(sessionState);
  }

  @Test
  void completeTokenReturnsFalseWhenNoTokenIsRegistered() {
    // given
    final var defaultService = Mockito.mock(VirtualCardService.class);
    final var sessionState = new VirtualCardSessionState();
    sut.registerVirtualCard("session-id", defaultService);
    assertThat(sut.getOrCreateVirtualCardSessionState("session-id")).isNotSameAs(sessionState);

    // when
    final var completed = sut.completeToken("missing-session", "token");

    // then
    assertThat(completed).isFalse();
    assertThat(sut.getVirtualCardServiceOrDefault("session-id", defaultService))
        .isSameAs(defaultService);
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
  void completeSolePendingTokenReturnsFalseWhenQueueIsEmpty() {
    // given / when
    final var completed = sut.completeSolePendingToken("token-value");

    // then
    assertThat(completed).isFalse();
  }

  @Test
  void completeSolePendingTokenReturnsFalseWhenMultipleTokensArePending() {
    // given
    final var sessionId1 = "session-1";
    final var sessionId2 = "session-2";
    sut.registerTokenWaiter(sessionId1);
    sut.registerTokenWaiter(sessionId2);

    // when
    final var completed = sut.completeSolePendingToken("token-value");

    // then
    assertThat(completed).isFalse();
    assertThat(sut.hasPendingToken(sessionId1)).isTrue();
    assertThat(sut.hasPendingToken(sessionId2)).isTrue();
  }

  @Test
  void completeSolePendingTokenCompletesWhenExactlyOneTokenIsPending() {
    // given
    final var sessionId = "sole-session";
    final var tokenFuture = sut.registerTokenWaiter(sessionId);
    final var virtualCardService = Mockito.mock(VirtualCardService.class);
    sut.registerVirtualCard(sessionId, virtualCardService);
    final var sessionState = sut.getOrCreateVirtualCardSessionState(sessionId);

    // when
    final var completed = sut.completeSolePendingToken("token-value");

    // then
    assertThat(completed).isTrue();
    assertThat(tokenFuture).isCompletedWithValue("token-value");
    assertThat(sut.hasPendingToken(sessionId)).isFalse();
    assertThat(sut.getOrCreateVirtualCardSessionState(sessionId)).isNotSameAs(sessionState);
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

    // when
    final var state = sut.getOrCreateVirtualCardSessionState(sessionId);

    // then
    assertThat(state).isNotNull();
    assertThat(sut.getOrCreateVirtualCardSessionState(sessionId)).isSameAs(state);
  }

  @Test
  void registerRequestContextReplacesExistingContext() {
    // given
    final var sessionId = "session-id";
    final var firstContext =
        sut.registerRequestContext(sessionId, CardConnectionType.CONTACT_CONNECTOR);

    // when
    final var secondContext =
        sut.registerRequestContext(sessionId, CardConnectionType.CONTACT_STANDARD);

    // then
    assertThat(secondContext).isNotSameAs(firstContext);
    assertThat(sut.getRequestContext(sessionId)).isSameAs(secondContext);
    assertThat(secondContext.getCardConnectionType())
        .isEqualTo(CardConnectionType.CONTACT_STANDARD);
  }

  @Test
  void cleanupWithMultipleRegistrationsRemovesAllData() {
    // given
    final var sessionId = "multi-session";
    sut.registerTokenWaiter(sessionId);
    sut.registerVirtualCard(sessionId, Mockito.mock(VirtualCardService.class));
    sut.registerRequestContext(sessionId, CardConnectionType.CONTACT_CONNECTOR);
    sut.getOrCreateVirtualCardSessionState(sessionId);

    // when
    sut.cleanup(sessionId);

    // then
    assertThat(sut.getRequestContext(sessionId)).isNull();
    assertThat(sut.getVirtualCardServiceOrDefault(sessionId, null)).isNull();
    // Note: cleanup doesn't remove tokenQueue entries, only virtual card and request context
  }

  @Test
  void getRequestContextReturnsNullForUnknownSession() {
    // given / when
    final var result = sut.getRequestContext("unknown-session");

    // then
    assertThat(result).isNull();
  }
}
