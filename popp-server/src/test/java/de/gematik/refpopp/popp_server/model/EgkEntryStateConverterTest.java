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

import static de.gematik.refpopp.popp_server.model.EgkEntryState.AD_HOC;
import static de.gematik.refpopp.popp_server.model.EgkEntryState.BLOCKED;
import static de.gematik.refpopp.popp_server.model.EgkEntryState.IMPORTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EgkEntryStateConverterTest {

  private EgkEntryStateConverter sut;

  @BeforeEach
  void setUp() {
    sut = new EgkEntryStateConverter();
  }

  @Test
  void convertToDatabaseColumnWithValidData() {
    assertThat(sut.convertToDatabaseColumn(IMPORTED)).isEqualTo("imported");
    assertThat(sut.convertToDatabaseColumn(AD_HOC)).isEqualTo("ad hoc");
    assertThat(sut.convertToDatabaseColumn(BLOCKED)).isEqualTo("blocked");
  }

  @Test
  void convertToDatabaseColumnWithNullData() {
    assertThat(sut.convertToDatabaseColumn(null)).isNull();
  }

  @Test
  void convertToEntityAttributeWithValidData() {
    assertThat(sut.convertToEntityAttribute("imported")).isEqualTo(IMPORTED);
    assertThat(sut.convertToEntityAttribute("ad hoc")).isEqualTo(AD_HOC);
    assertThat(sut.convertToEntityAttribute("blocked")).isEqualTo(BLOCKED);
  }

  @Test
  void convertToEntityAttributeWithNullData() {
    assertThat(sut.convertToEntityAttribute(null)).isNull();
  }

  @Test
  void convertToEntityAttributeWithInvalidData() {
    assertThrows(IllegalArgumentException.class, () -> sut.convertToEntityAttribute("invalid"));
    assertThatThrownBy(() -> sut.convertToEntityAttribute("invalid"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
