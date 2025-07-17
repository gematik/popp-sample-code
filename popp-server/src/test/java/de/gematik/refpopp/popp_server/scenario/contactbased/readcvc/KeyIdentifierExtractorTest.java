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

package de.gematik.refpopp.popp_server.scenario.contactbased.readcvc;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.smartcards.utils.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeyIdentifierExtractorTest {

  private KeyIdentifierExtractor sut;

  @BeforeEach
  void setUp() {
    sut = new KeyIdentifierExtractor();
  }

  @Test
  void extractValidDataShouldReturnEmptyList() {
    // given
    final byte[] data =
        Hex.toByteArray(
            "7f2181da7f4e81935f290170420844454758581102237f494b06062b24030503018641045e7ae614740e7012e350de71c10021ec668f21d6859591b4f709c4c73cce91c5a7fb0be1327e59ff1d0cb402b9c2bb0dc0432fa566bd4ff5f532258c7364aecd5f200c0009802768831100001565497f4c1306082a8214004c0481185307000000000000005f25060204000400025f24060209000400015f37409d244d497832172304f298bd49f91f45bf346cb306adeb44b0742017a074902146cccbdbb35426c2eb602d38253d92ebe1ac6905f388407398a474c4ea612d84");

    // When
    final var result = sut.extract(data);

    // Then
    assertThat(result).isEmpty();
  }
}
