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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.model.PoppTokenGenerationRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import de.gematik.refpopp.popp_server.repository.PoppTokenGenerationRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class PendingTokenDeliveryLookupServiceTest {

  private PoppTokenGenerationRecordRepository repository;
  private PendingTokenDeliveryLookupService service;

  @BeforeEach
  void setUp() {
    repository = mock(PoppTokenGenerationRecordRepository.class);
    service = new PendingTokenDeliveryLookupService(repository);
  }

  @Test
  void checksRecordsWithoutWorkplaceFilterForNullAndEmptyList() {
    when(repository.existsByActorId("actor")).thenReturn(true);

    assertThat(service.hasMatchingRecords("actor", null)).isTrue();
    assertThat(service.hasMatchingRecords("actor", List.of())).isTrue();
    verify(repository, times(2)).existsByActorId("actor");
  }

  @Test
  void checksRecordsAtRequestedWorkplaces() {
    final var workplaces = List.of("first", "second");
    when(repository.existsByActorIdAndWorkplaceIdIn("actor", workplaces)).thenReturn(true);

    assertThat(service.hasMatchingRecords("actor", workplaces)).isTrue();
    assertThat(service.hasMatchingRecords("other", workplaces)).isFalse();
    verify(repository).existsByActorIdAndWorkplaceIdIn("actor", workplaces);
  }

  @Test
  void checksPendingRecordsWithoutWorkplaceFilterUsingOneResult() {
    when(repository.findPendingMessageIdsForActor(
            eq("actor"),
            eq(PoppTokenGenerationStatus.PENDING),
            any(Instant.class),
            eq(PageRequest.of(0, 1))))
        .thenReturn(List.of(UUID.randomUUID()), List.of());

    assertThat(service.hasPending("actor", null)).isTrue();
    assertThat(service.hasPending("actor", List.of())).isFalse();
  }

  @Test
  void checksPendingRecordsAtRequestedWorkplacesUsingOneResult() {
    final var workplaces = List.of("first");
    when(repository.findPendingMessageIdsForActorAndWorkplaces(
            eq("actor"),
            eq(workplaces),
            eq(PoppTokenGenerationStatus.PENDING),
            any(Instant.class),
            eq(PageRequest.of(0, 1))))
        .thenReturn(List.of(), List.of(UUID.randomUUID()));

    assertThat(service.hasPending("actor", workplaces)).isFalse();
    assertThat(service.hasPending("actor", workplaces)).isTrue();
  }

  @Test
  void locksRecordsWithoutWorkplaceFilterAtLimitBoundaries() {
    final var records = List.of(new PoppTokenGenerationRecord());
    when(repository.lockPendingForDelivery(eq("actor"), any(Instant.class), eq(1)))
        .thenReturn(records);

    assertThat(service.lockForDelivery("actor", null, 1)).isSameAs(records);
    assertThat(service.lockForDelivery("actor", List.of(), 100)).isEmpty();
    verify(repository).lockPendingForDelivery(eq("actor"), any(Instant.class), eq(100));
  }

  @Test
  void locksRecordsAtRequestedWorkplaces() {
    final var workplaces = List.of("first");
    final var records = List.of(new PoppTokenGenerationRecord());
    when(repository.lockPendingForDeliveryAtWorkplaces(
            eq("actor"), eq(workplaces), any(Instant.class), eq(5)))
        .thenReturn(records);

    assertThat(service.lockForDelivery("actor", workplaces, 5)).isSameAs(records);
  }

  @Test
  void rejectsLimitsOutsideOneToOneHundredBeforeAccessingRepository() {
    for (final var limit : List.of(-1, 0, 101)) {
      assertThatThrownBy(() -> service.lockForDelivery("actor", null, limit))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("limit must be between 1 and 100");
    }
    verifyNoInteractions(repository);
  }
}
