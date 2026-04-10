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

package de.gematik.refpopp.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;

public final class JwtAssertions {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private JwtAssertions() {}

  public static String extractJwt(String responseBody) throws Exception {
    JsonNode json = new ObjectMapper().readTree(responseBody);

    return json.get("token").asText();
  }

  public static void checkTokenHeader(String jwt) throws Exception {
    JsonNode header = decodeHeader(jwt);

    assertThat(header.get("alg").asText()).isEqualTo("ES256");
    assertThat(header.get("typ").asText()).isEqualTo("vnd.telematik.popp+jwt");
    assertThat(header.get("kid").asText()).isNotBlank();
  }

  public static void checkTokenBody(String jwt) throws Exception {
    JsonNode body = decodeBody(jwt);

    assertThat(body.get("actorId").asText()).isEqualTo("telematik-id");
    assertThat(body.get("actorProfessionOid").asText()).isEqualTo("1.2.276.0.76.4.50");
    assertThat(body.get("authorization_details").asText()).isEqualTo("details");
    assertThat(body.get("proofMethod").asText()).isEqualTo("ehc-practitioner-trustedchannel");
    assertThat(body.get("version").asText()).isEqualTo("1.0.0");
    assertThat(body.get("iss").asText()).isEqualTo("https://popp.example.com");
    assertThat(body.get("iat").asLong()).isGreaterThan(0);
  }

  private static JsonNode decodeHeader(String jwt) throws Exception {
    String[] parts = split(jwt);
    byte[] decoded = Base64.getUrlDecoder().decode(parts[0]);
    return OBJECT_MAPPER.readTree(decoded);
  }

  private static JsonNode decodeBody(String jwt) throws Exception {
    String[] parts = split(jwt);
    byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
    return OBJECT_MAPPER.readTree(decoded);
  }

  private static String[] split(String jwt) {
    assertThat(jwt).isNotBlank();

    String[] parts = jwt.split("\\.");
    assertThat(parts).as("JWT must have header.payload.signature").hasSize(3);

    return parts;
  }
}
