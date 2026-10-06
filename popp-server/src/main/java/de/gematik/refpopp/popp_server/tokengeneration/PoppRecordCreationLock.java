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

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Serializes concurrent record creation for the same patient and actor within a transaction. */
@Component
public class PoppRecordCreationLock {

  private static final String LOCK_QUERY =
      """
      SELECT pg_advisory_xact_lock(
          hashtext(?),
          hashtext(?)
      )
      """;

  private final JdbcTemplate jdbcTemplate;

  public PoppRecordCreationLock(final JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Acquires a PostgreSQL transaction-level advisory lock for a patient and actor combination.
   *
   * @param patientId the insured person's identifier
   * @param actorId the actor's Telematik ID
   */
  public void acquire(final String patientId, final String actorId) {
    jdbcTemplate.query(
        LOCK_QUERY,
        statement -> {
          statement.setString(1, patientId);
          statement.setString(2, actorId);
        },
        resultSet -> null);
  }
}
