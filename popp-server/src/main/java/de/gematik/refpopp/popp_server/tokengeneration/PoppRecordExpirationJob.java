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

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically expires PoPP records whose retention period has elapsed.
 *
 * <p>The execution schedule is configured through {@code popp.token-generation.expiration.cron}.
 */
@Component
public class PoppRecordExpirationJob {

  private final PoppTokenGenerationRecordService recordService;

  public PoppRecordExpirationJob(final PoppTokenGenerationRecordService recordService) {
    this.recordService = recordService;
  }

  /** Marks expired message records as canceled and removes their associated PoPP records. */
  @Scheduled(cron = "${popp.token-generation.expiration.cron}")
  public void expireRecords() {
    recordService.expireRecords();
  }
}
