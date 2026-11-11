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

import de.gematik.refpopp.popp_server.controller.dto.TokenDeliveryItem;
import de.gematik.refpopp.popp_server.controller.dto.TokenDeliveryResponse;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import de.gematik.refpopp.popp_server.scenario.common.token.PoppTokenCreator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Signs and marks a batch of PoPP tokens as delivered in one transaction. */
@Service
public class TokenDeliveryService {

  private final PendingTokenDeliveryLookupService lookupService;
  private final PoppTokenCreator tokenCreator;
  private final PoppTokenGenerationRecordService recordService;

  public TokenDeliveryService(
      final PendingTokenDeliveryLookupService lookupService,
      final PoppTokenCreator tokenCreator,
      final PoppTokenGenerationRecordService recordService) {
    this.lookupService = lookupService;
    this.tokenCreator = tokenCreator;
    this.recordService = recordService;
  }

  @Transactional
  public TokenDeliveryResponse deliver(
      final String telematikId, final List<String> workplaceIds, final int limit) {
    if (!lookupService.hasMatchingRecords(telematikId, workplaceIds)) {
      throw new TokenDeliveryNotFoundException();
    }
    final var records = lookupService.lockForDelivery(telematikId, workplaceIds, limit);
    final var tokens = new ArrayList<TokenDeliveryItem>(records.size());
    for (final var tokenGenerationRecord : records) {
      tokens.add(
          new TokenDeliveryItem(
              tokenCreator.createPoppToken(tokenGenerationRecord),
              tokenGenerationRecord.getWorkplaceId(),
              tokenGenerationRecord.getMessageId()));
    }

    for (final var tokenGenerationRecord : records) {
      final var message = recordService.markTokenDelivered(tokenGenerationRecord.getMessageId());
      if (message.getStatus() != PoppTokenGenerationStatus.SUCCESS
          || message.getTokenDeliveredAt() == null) {
        throw new IllegalStateException(
            "Could not mark PoPP token as delivered: " + tokenGenerationRecord.getMessageId());
      }
    }

    final var hasMore = lookupService.hasPending(telematikId, workplaceIds);
    return new TokenDeliveryResponse(List.copyOf(tokens), hasMore);
  }
}
