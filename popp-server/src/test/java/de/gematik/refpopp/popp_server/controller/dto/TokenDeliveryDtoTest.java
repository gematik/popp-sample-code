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

package de.gematik.refpopp.popp_server.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;

class TokenDeliveryDtoTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void readsRequestWithWorkplaceFilter() {
    final var request =
        mapper.readValue(
            """
            {"telematikId":"1-SMC-B-Testkarte--88311000016875","workplaceIds":["Arbeitsplatz 1"],"limit":1}
            """,
            TokenDeliveryRequest.class);

    assertThat(request.telematikId()).isEqualTo("1-SMC-B-Testkarte--88311000016875");
    assertThat(request.workplaceIds()).containsExactly("Arbeitsplatz 1");
    assertThat(request.limit()).isEqualTo(1);
  }

  @Test
  void keepsMissingAndEmptyWorkplaceFiltersDistinct() {
    final var absent =
        mapper.readValue(
            """
            {"telematikId":"test-id","limit":100}
            """,
            TokenDeliveryRequest.class);
    final var empty =
        mapper.readValue(
            """
            {"telematikId":"test-id","workplaceIds":[],"limit":100}
            """,
            TokenDeliveryRequest.class);

    assertThat(absent.workplaceIds()).isNull();
    assertThat(empty.workplaceIds()).isEmpty();
  }

  @Test
  void rejectsNonIntegerLimit() {
    for (final var limit : List.of("1.5", "\"1\"", "true", "false", "[]", "{}", "2147483648")) {
      assertThatThrownBy(
              () ->
                  mapper.readValue(
                      "{\"telematikId\":\"test-id\",\"limit\":" + limit + "}",
                      TokenDeliveryRequest.class))
          .isInstanceOf(JacksonException.class);
    }
  }

  @Test
  void rejectsNonStringIdentifiers() {
    for (final var json :
        List.of(
            "{\"telematikId\":123,\"limit\":1}",
            "{\"telematikId\":true,\"limit\":1}",
            "{\"telematikId\":{},\"limit\":1}",
            "{\"telematikId\":\"test-id\",\"workplaceIds\":[123],\"limit\":1}",
            "{\"telematikId\":\"test-id\",\"workplaceIds\":[false],\"limit\":1}",
            "{\"telematikId\":\"test-id\",\"workplaceIds\":[{}],\"limit\":1}")) {
      assertThatThrownBy(() -> mapper.readValue(json, TokenDeliveryRequest.class))
          .isInstanceOf(JacksonException.class);
    }
  }

  @Test
  void keepsStringIdentifiersAndIntegerLimitUnchanged() {
    final var request =
        mapper.readValue(
            """
            {"telematikId":"  test-id  ","workplaceIds":["first","second"],"limit":100}
            """,
            TokenDeliveryRequest.class);

    assertThat(request.telematikId()).isEqualTo("  test-id  ");
    assertThat(request.workplaceIds()).containsExactly("first", "second");
    assertThat(request.limit()).isEqualTo(100);
  }

  @Test
  void integerDeserializerDelegatesUnexpectedTokenToContext() {
    final var parser = mock(JsonParser.class);
    final var context = mock(DeserializationContext.class);
    when(parser.currentToken()).thenReturn(JsonToken.VALUE_STRING);
    when(context.handleUnexpectedToken(Integer.class, parser)).thenReturn(7);

    assertThat(new IntegerLimitDeserializer().deserialize(parser, context)).isEqualTo(7);
  }

  @Test
  void stringDeserializerDelegatesUnexpectedTokenToContext() {
    final var parser = mock(JsonParser.class);
    final var context = mock(DeserializationContext.class);
    when(parser.currentToken()).thenReturn(JsonToken.VALUE_NUMBER_INT);
    when(context.handleUnexpectedToken(String.class, parser)).thenReturn("handled");

    assertThat(new StrictStringDeserializer().deserialize(parser, context)).isEqualTo("handled");
  }

  @Test
  void writesResponseUsingContractFieldNames() {
    final var id = UUID.fromString("237cc7b7-f85e-42c3-981c-ad7f9d97ddca");
    final var response =
        new TokenDeliveryResponse(
            List.of(new TokenDeliveryItem("signed.jwt.token", null, id)), true);

    assertThat(mapper.readTree(mapper.writeValueAsString(response)))
        .isEqualTo(
            mapper.readTree(
                """
                {"popptokens":[{"token":"signed.jwt.token","poppStatusRecordId":"237cc7b7-f85e-42c3-981c-ad7f9d97ddca"}],"hasmore":true}
                """));
  }

  @Test
  void writesEmptyResponse() {
    final var response = new TokenDeliveryResponse(List.of(), false);

    assertThat(mapper.readTree(mapper.writeValueAsString(response)))
        .isEqualTo(
            mapper.readTree(
                """
                {"popptokens":[],"hasmore":false}
                """));
  }
}
