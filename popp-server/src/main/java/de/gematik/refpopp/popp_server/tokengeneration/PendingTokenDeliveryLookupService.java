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

import de.gematik.refpopp.popp_server.model.PoppTokenGenerationRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import de.gematik.refpopp.popp_server.repository.PoppTokenGenerationRecordRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Finds and locks eligible PoPP records for token delivery. */
@Service
public class PendingTokenDeliveryLookupService {

  private final PoppTokenGenerationRecordRepository repository;

  public PendingTokenDeliveryLookupService(final PoppTokenGenerationRecordRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public boolean hasMatchingRecords(final String telematikId, final List<String> workplaceIds) {
    if (workplaceIds == null || workplaceIds.isEmpty()) {
      return repository.existsByActorId(telematikId);
    }
    return repository.existsByActorIdAndWorkplaceIdIn(telematikId, workplaceIds);
  }

  @Transactional(readOnly = true)
  public boolean hasPending(final String telematikId, final List<String> workplaceIds) {
    final var page = PageRequest.of(0, 1);
    final var now = Instant.now();
    if (workplaceIds == null || workplaceIds.isEmpty()) {
      return !repository
          .findPendingMessageIdsForActor(telematikId, PoppTokenGenerationStatus.PENDING, now, page)
          .isEmpty();
    }
    return !repository
        .findPendingMessageIdsForActorAndWorkplaces(
            telematikId, workplaceIds, PoppTokenGenerationStatus.PENDING, now, page)
        .isEmpty();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public List<PoppTokenGenerationRecord> lockForDelivery(
      final String telematikId, final List<String> workplaceIds, final int limit) {
    validateLimit(limit);

    final var now = Instant.now();
    if (workplaceIds == null || workplaceIds.isEmpty()) {
      return repository.lockPendingForDelivery(telematikId, now, limit);
    }
    return repository.lockPendingForDeliveryAtWorkplaces(telematikId, workplaceIds, now, limit);
  }

  private void validateLimit(final int limit) {
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("limit must be between 1 and 100");
    }
  }
}
