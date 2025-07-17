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

package de.gematik.refpopp.popp_server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "egk_entries")
public class EgkEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "cvc_hash", columnDefinition = "bytea", nullable = false)
  private byte[] cvcHash;

  @Column(name = "aut_hash", columnDefinition = "bytea", nullable = false)
  private byte[] autHash;

  @Column(name = "state", length = 8, nullable = false)
  @Convert(converter = EgkEntryStateConverter.class)
  private EgkEntryState state;

  @Column(name = "not_after", nullable = false)
  private LocalDateTime notAfter;

  public EgkEntry() {}

  public EgkEntry(
      final byte[] cvcHash,
      final byte[] autHash,
      final EgkEntryState state,
      final LocalDateTime notAfter) {
    this.cvcHash = cvcHash;
    this.autHash = autHash;
    this.state = state;
    this.notAfter = notAfter;
  }

  public EgkEntry(final EgkEntry other) {
    this.id = other.id;
    this.cvcHash = other.cvcHash;
    this.autHash = other.autHash;
    this.state = other.state;
    this.notAfter = other.notAfter;
  }
}
