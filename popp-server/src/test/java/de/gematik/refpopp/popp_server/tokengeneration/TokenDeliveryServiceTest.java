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

package de.gematik.refpopp.popp_server.tokengeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.controller.dto.TokenDeliveryItem;
import de.gematik.refpopp.popp_server.model.PoppMessageRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import de.gematik.refpopp.popp_server.scenario.common.token.PoppTokenCreator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenDeliveryServiceTest {

  private PendingTokenDeliveryLookupService lookupService;
  private PoppTokenCreator tokenCreator;
  private PoppTokenGenerationRecordService recordService;
  private TokenDeliveryService service;

  @BeforeEach
  void setUp() {
    lookupService = mock(PendingTokenDeliveryLookupService.class);
    tokenCreator = mock(PoppTokenCreator.class);
    recordService = mock(PoppTokenGenerationRecordService.class);
    service = new TokenDeliveryService(lookupService, tokenCreator, recordService);
  }

  @Test
  void rejectsUnknownActorOrWorkplacesWithoutLockingOrSigning() {
    final var workplaces = List.of("workplace");
    assertThatThrownBy(() -> service.deliver("actor", workplaces, 10))
        .isInstanceOf(TokenDeliveryNotFoundException.class);

    verify(lookupService, never()).lockForDelivery(any(), any(), anyInt());
    verifyNoInteractions(tokenCreator, recordService);
  }

  @Test
  void returnsEmptyBatchAndChecksForMoreWhenNoRecordsArePending() {
    when(lookupService.hasMatchingRecords("actor", null)).thenReturn(true);
    when(lookupService.lockForDelivery("actor", null, 1)).thenReturn(List.of());

    final var result = service.deliver("actor", null, 1);

    assertThat(result.poppTokens()).isEmpty();
    assertThat(result.hasMore()).isFalse();
    verify(lookupService).hasPending("actor", null);
    verifyNoInteractions(tokenCreator, recordService);
  }

  @Test
  void signsAllTokensBeforeMarkingRecordsDeliveredAndReturnsRemainingFlag() {
    final var first = createRecord("first");
    final var second = createRecord("second");
    final var workplaces = List.of("first", "second");
    when(lookupService.hasMatchingRecords("actor", workplaces)).thenReturn(true);
    when(lookupService.lockForDelivery("actor", workplaces, 2)).thenReturn(List.of(first, second));
    when(tokenCreator.createPoppToken(first)).thenReturn("jwt-first");
    when(tokenCreator.createPoppToken(second)).thenReturn("jwt-second");
    when(recordService.markTokenDelivered(first.getMessageId())).thenReturn(delivered(first));
    when(recordService.markTokenDelivered(second.getMessageId())).thenReturn(delivered(second));
    when(lookupService.hasPending("actor", workplaces)).thenReturn(true);

    final var result = service.deliver("actor", workplaces, 2);

    assertThat(result.poppTokens())
        .extracting(TokenDeliveryItem::token)
        .containsExactly("jwt-first", "jwt-second");
    assertThat(result.poppTokens())
        .extracting(TokenDeliveryItem::workplaceId)
        .containsExactly("first", "second");
    assertThat(result.poppTokens())
        .extracting(TokenDeliveryItem::poppStatusRecordId)
        .containsExactly(first.getMessageId(), second.getMessageId());
    assertThat(result.hasMore()).isTrue();
    final var order = inOrder(tokenCreator, recordService, lookupService);
    order.verify(tokenCreator).createPoppToken(first);
    order.verify(tokenCreator).createPoppToken(second);
    order.verify(recordService).markTokenDelivered(first.getMessageId());
    order.verify(recordService).markTokenDelivered(second.getMessageId());
    order.verify(lookupService).hasPending("actor", workplaces);
  }

  @Test
  void doesNotMarkRecordsDeliveredWhenSigningFails() {
    final var tokenRecord = createRecord("first");
    when(lookupService.hasMatchingRecords("actor", null)).thenReturn(true);
    when(lookupService.lockForDelivery("actor", null, 1)).thenReturn(List.of(tokenRecord));
    when(tokenCreator.createPoppToken(tokenRecord))
        .thenThrow(new IllegalStateException("signing failed"));

    assertThatThrownBy(() -> service.deliver("actor", null, 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("signing failed");
    verifyNoInteractions(recordService);
    verify(lookupService, never()).hasPending(any(), any());
  }

  @Test
  void rejectsUnchangedDeliveryStatus() {
    final var tokenRecord = createRecord("first");
    final var message = delivered(tokenRecord);
    message.setStatus(PoppTokenGenerationStatus.PENDING);
    assertInvalidDelivery(tokenRecord, message);
  }

  @Test
  void rejectsMissingDeliveryTimestamp() {
    final var tokenRecord = createRecord("first");
    final var message = delivered(tokenRecord);
    message.setTokenDeliveredAt(null);
    assertInvalidDelivery(tokenRecord, message);
  }

  private void assertInvalidDelivery(
      PoppTokenGenerationRecord tokenRecord, PoppMessageRecord message) {
    when(lookupService.hasMatchingRecords("actor", null)).thenReturn(true);
    when(lookupService.lockForDelivery("actor", null, 1)).thenReturn(List.of(tokenRecord));
    when(tokenCreator.createPoppToken(tokenRecord)).thenReturn("jwt");
    when(recordService.markTokenDelivered(tokenRecord.getMessageId())).thenReturn(message);

    assertThatThrownBy(() -> service.deliver("actor", null, 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Could not mark PoPP token as delivered: " + tokenRecord.getMessageId());
    verify(lookupService, never()).hasPending(any(), any());
  }

  private PoppTokenGenerationRecord createRecord(String workplace) {
    return new PoppTokenGenerationRecord(
        UUID.randomUUID(), "A123456789", "123456789", "actor", workplace, Instant.now());
  }

  private PoppMessageRecord delivered(PoppTokenGenerationRecord tokenRecord) {
    final var message = new PoppMessageRecord(tokenRecord.getMessageId(), "client", Instant.now());
    message.setStatus(PoppTokenGenerationStatus.SUCCESS);
    message.setTokenDeliveredAt(Instant.now());
    return message;
  }
}
