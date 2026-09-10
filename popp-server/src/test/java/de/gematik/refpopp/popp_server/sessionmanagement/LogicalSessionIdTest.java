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

package de.gematik.refpopp.popp_server.sessionmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogicalSessionIdTest {

  @Test
  void ofBuildsConcatenatedLogicalId() {
    // given
    final var transport = "transportId";
    final var client = "client123";

    // when
    final var logical = LogicalSessionId.of(transport, client);

    // then
    assertThat(logical).isEqualTo(transport + LogicalSessionId.SEPARATOR + client);
  }

  @Test
  void transportPrefixReturnsTransportWithSeparator() {
    // given
    final var transport = "t1";

    // when
    final var prefix = LogicalSessionId.transportPrefix(transport);

    // then
    assertThat(prefix).isEqualTo(transport + LogicalSessionId.SEPARATOR);
  }

  @Test
  void isLogicalDetectsLogicalIdsAndNulls() {
    // given
    final var logical = "a" + LogicalSessionId.SEPARATOR + "b";
    final var plain = "no-sep";

    // when / then
    assertThat(LogicalSessionId.isLogical(logical)).isTrue();
    assertThat(LogicalSessionId.isLogical(plain)).isFalse();
  }

  @Test
  void clientSessionIdOfReturnsClientPartOrNull() {
    // given
    final var transport = "transport";
    final var client = "client";
    final var logical = LogicalSessionId.of(transport, client);
    final var notLogical = "justOnePart";
    final var multi =
        transport + LogicalSessionId.SEPARATOR + client + LogicalSessionId.SEPARATOR + "extra";

    // when
    final var extracted = LogicalSessionId.clientSessionIdOf(logical);
    final var extractedMulti = LogicalSessionId.clientSessionIdOf(multi);
    final var extractedNon = LogicalSessionId.clientSessionIdOf(notLogical);
    final var extractedNull = LogicalSessionId.clientSessionIdOf(null);

    // then
    assertThat(extracted).isEqualTo(client);
    // when multiple separators are present, the remainder after the first separator is returned
    assertThat(extractedMulti).isEqualTo(client + LogicalSessionId.SEPARATOR + "extra");
    assertThat(extractedNon).isNull();
    assertThat(extractedNull).isNull();
  }
}
