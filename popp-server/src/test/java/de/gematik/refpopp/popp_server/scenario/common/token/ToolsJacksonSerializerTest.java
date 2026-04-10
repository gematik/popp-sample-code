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

package de.gematik.refpopp.popp_server.scenario.common.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ToolsJacksonSerializerTest {

  private final ToolsJacksonSerializer sut = new ToolsJacksonSerializer();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void serializeReturnsJsonBytes() throws Exception {
    final Map<String, Object> payload = Map.of("type", "Token", "token", "abc", "pn", "pn1");

    final byte[] json = sut.serialize(payload);

    @SuppressWarnings("unchecked")
    final Map<String, Object> parsed = mapper.readValue(json, Map.class);
    assertThat(parsed)
        .containsEntry("type", "Token")
        .containsEntry("token", "abc")
        .containsEntry("pn", "pn1");
  }

  @Test
  void serializeWritesToOutputStream() throws Exception {
    final Map<String, Object> payload = Map.of("type", "Token", "token", "abc", "pn", "pn1");
    final var out = new ByteArrayOutputStream();

    sut.serialize(payload, out);

    @SuppressWarnings("unchecked")
    final Map<String, Object> parsed = mapper.readValue(out.toByteArray(), Map.class);
    assertThat(parsed)
        .containsEntry("type", "Token")
        .containsEntry("token", "abc")
        .containsEntry("pn", "pn1");
  }
}
