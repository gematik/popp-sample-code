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

import io.jsonwebtoken.io.SerializationException;
import io.jsonwebtoken.io.Serializer;
import java.io.OutputStream;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

final class ToolsJacksonSerializer implements Serializer<Map<String, ?>> {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public byte[] serialize(final Map<String, ?> value) throws SerializationException {
    try {
      return objectMapper.writeValueAsBytes(value);
    } catch (final Exception e) {
      throw new SerializationException("Unable to serialize JWT payload", e);
    }
  }

  @Override
  public void serialize(final Map<String, ?> value, final OutputStream out)
      throws SerializationException {
    try {
      objectMapper.writeValue(out, value);
    } catch (final Exception e) {
      throw new SerializationException("Unable to serialize JWT payload", e);
    }
  }
}
