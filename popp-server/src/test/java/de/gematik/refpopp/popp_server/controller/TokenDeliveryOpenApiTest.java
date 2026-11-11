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

package de.gematik.refpopp.popp_server.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.refpopp.popp_server.configuration.PoppOpenApiConfiguration;
import de.gematik.refpopp.popp_server.configuration.TokenDeliveryOpenApiConfiguration;
import de.gematik.refpopp.popp_server.tokengeneration.TokenDeliveryRequestValidator;
import de.gematik.refpopp.popp_server.tokengeneration.TokenDeliveryService;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(TokenDeliveryController.class)
@EnableConfigurationProperties(SpringDocConfigProperties.class)
@Import({
  PoppOpenApiConfiguration.class,
  TokenDeliveryOpenApiConfiguration.class,
  TokenDeliveryRequestValidator.class
})
@ImportAutoConfiguration({
  SpringDocConfiguration.class,
  SpringDocSpecPropertiesConfiguration.class,
  SpringDocWebMvcConfiguration.class
})
class TokenDeliveryOpenApiTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private TokenDeliveryService deliveryService;

  @Test
  void documentsContractSchemaNamesAndReferences() throws Exception {
    final var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    final var document = new ObjectMapper().readTree(result.getResponse().getContentAsString());
    final var schemas = document.path("components").path("schemas");
    final var operation =
        document.path("paths").path("/popp/practitioner/api/v1/token-deliveries").path("post");

    for (final var name :
        new String[] {
          "TokenRequestSchema",
          "TokenResponseSchema",
          "ErrorSchema",
          "UserAgentSchema",
          "TelematikIdSchema",
          "WorkplaceIdSchema",
          "PoppStatusRecordIdSchema"
        }) {
      assertThat(schemas.has(name)).as(name).isTrue();
    }
    assertThat(schemas.has("ErrorResponse")).isFalse();
    assertThat(schemas.has("TokenDeliveryItem")).isFalse();

    assertThat(
            operation
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("$ref")
                .asString())
        .isEqualTo("#/components/schemas/TokenRequestSchema");
    final var header = operation.path("parameters").get(0);
    assertThat(header.path("name").asString()).isEqualTo("x-useragent");
    assertThat(header.path("required").asBoolean()).isTrue();
    assertThat(header.path("schema").path("$ref").asString())
        .isEqualTo("#/components/schemas/UserAgentSchema");

    final var request = schemas.path("TokenRequestSchema").path("properties");
    assertThat(request.path("telematikId").path("$ref").asString())
        .isEqualTo("#/components/schemas/TelematikIdSchema");
    assertThat(request.path("workplaceIds").path("items").path("$ref").asString())
        .isEqualTo("#/components/schemas/WorkplaceIdSchema");

    final var response = schemas.path("TokenResponseSchema").path("properties");
    assertThat(response.has("popptokens")).isTrue();
    assertThat(response.has("hasmore")).isTrue();
    final var item = response.path("popptokens").path("items");
    assertThat(item.has("$ref")).isFalse();
    assertThat(item.path("properties").has("token")).isTrue();
    assertThat(item.path("properties").has("workplaceId")).isTrue();
    assertThat(item.path("properties").path("poppStatusRecordId").path("$ref").asString())
        .isEqualTo("#/components/schemas/PoppStatusRecordIdSchema");
    assertThat(item.path("required").toString()).contains("\"token\"", "\"poppStatusRecordId\"");
    assertThat(schemas.path("PoppStatusRecordIdSchema").path("format").asString())
        .isEqualTo("uuid");
    assertThat(schemas.path("UserAgentSchema").path("pattern").asString())
        .isEqualTo("^[a-zA-Z0-9\\-]{1,20}\\/[a-zA-Z0-9\\-\\.]{1,15}$");
    assertThat(schemas.path("WorkplaceIdSchema").path("pattern").asString())
        .isEqualTo("^[^\\/:*?\"<>|]{1,64}$");

    final var responses = operation.path("responses");
    assertThat(
            responses
                .path("200")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("$ref")
                .asString())
        .isEqualTo("#/components/schemas/TokenResponseSchema");
    for (final var status : new String[] {"400", "404", "500"}) {
      assertThat(
              responses
                  .path(status)
                  .path("content")
                  .path("application/json")
                  .path("schema")
                  .path("$ref")
                  .asString())
          .isEqualTo("#/components/schemas/ErrorSchema");
    }
    assertThat(schemas.path("ErrorSchema").path("required")).hasToString("[\"errorCode\"]");
  }
}
