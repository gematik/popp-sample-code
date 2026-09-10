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

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EgkEntryTest {

  @Test
  void constructAndCopy() {
    byte[] cvc = new byte[] {1, 2, 3};
    byte[] aut = new byte[] {4, 5, 6};
    EgkEntry entry =
        new EgkEntry(cvc, aut, EgkEntryState.IMPORTED, LocalDateTime.now().plusDays(1));

    assertArrayEquals(cvc, entry.getCvcHash());
    assertArrayEquals(aut, entry.getAutHash());
    assertEquals(EgkEntryState.IMPORTED, entry.getState());

    EgkEntry copy = new EgkEntry(entry);
    assertArrayEquals(entry.getCvcHash(), copy.getCvcHash());
    assertEquals(entry.getState(), copy.getState());
  }
}
