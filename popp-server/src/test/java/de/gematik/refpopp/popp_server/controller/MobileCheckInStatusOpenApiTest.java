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

import de.gematik.refpopp.popp_server.configuration.MobileCheckInOpenApiConfiguration;
import de.gematik.refpopp.popp_server.configuration.PoppOpenApiConfiguration;
import de.gematik.refpopp.popp_server.configuration.TokenDeliveryOpenApiConfiguration;
import de.gematik.refpopp.popp_server.tokengeneration.PoppTokenGenerationRecordService;
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

@WebMvcTest({MobileCheckInStatusController.class, TokenDeliveryController.class})
@EnableConfigurationProperties(SpringDocConfigProperties.class)
@Import({
  MobileCheckInOpenApiConfiguration.class,
  PoppOpenApiConfiguration.class,
  TokenDeliveryOpenApiConfiguration.class,
  TokenDeliveryRequestValidator.class
})
@ImportAutoConfiguration({
  SpringDocConfiguration.class,
  SpringDocSpecPropertiesConfiguration.class,
  SpringDocWebMvcConfiguration.class
})
class MobileCheckInStatusOpenApiTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PoppTokenGenerationRecordService recordService;
  @MockitoBean private TokenDeliveryService deliveryService;

  @Test
  void documentsStatusOperationWithoutChangingTokenDeliverySchemas() throws Exception {
    final var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    final var document = new ObjectMapper().readTree(result.getResponse().getContentAsString());
    final var operation =
        document
            .path("paths")
            .path("/popp/patient/api/v1/mobile/checkin/poppStatusRecordId")
            .path("get");
    assertThat(operation.path("operationId").asString()).isEqualTo("readStatusPoPP");
    assertThat(operation.path("tags").toString()).hasToString("[\"status popp\"]");
    assertThat(operation.path("security").toString()).hasToString("[]");
    assertThat(operation.has("requestBody")).isFalse();

    final var parameters = operation.path("parameters");
    assertThat(parameters.size()).isEqualTo(1);
    final var parameter = parameters.get(0);
    assertThat(parameter.path("name").asString()).isEqualTo("statusrequest");
    assertThat(parameter.path("in").asString()).isEqualTo("query");
    assertThat(parameter.path("required").asBoolean()).isTrue();
    assertThat(parameter.path("schema").path("$ref").asString())
        .isEqualTo("#/components/schemas/PoppStatusRecordIdSchema");
    assertThat(parameter.path("schema").size()).isEqualTo(1);

    final var schemas = document.path("components").path("schemas");
    final var response = schemas.path("StatusPoPPTokenGenerationSchema");
    assertThat(response.path("required").isMissingNode()).isTrue();
    assertThat(response.path("properties").path("state").path("$ref").asString())
        .isEqualTo("#/components/schemas/StateSchema");
    assertThat(response.path("properties").path("poppStatusRecordId").path("$ref").asString())
        .isEqualTo("#/components/schemas/PoppStatusRecordIdSchema");
    assertThat(schemas.path("StateSchema").path("enum").toString())
        .hasToString("[\"pending\",\"success\",\"canceled\"]");
    assertThat(schemas.has("PoppStatusRecordIdSchema")).isTrue();
    assertThat(schemas.path("PoppStatusRecordIdSchema").path("format").asString())
        .isEqualTo("uuid");

    final var responses = operation.path("responses");
    assertThat(responses.size()).isEqualTo(4);
    assertThat(
            responses
                .path("200")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("$ref")
                .asString())
        .isEqualTo("#/components/schemas/StatusPoPPTokenGenerationSchema");
    for (final var code : new String[] {"400", "404", "500"}) {
      assertThat(
              responses
                  .path(code)
                  .path("content")
                  .path("application/json")
                  .path("schema")
                  .path("$ref")
                  .asString())
          .isEqualTo("#/components/schemas/ErrorType");
    }
    assertThat(schemas.path("ErrorType").path("required").toString())
        .hasToString("[\"errorCode\"]");
    assertThat(
            schemas
                .path("ErrorType")
                .path("properties")
                .path("errorCode")
                .path("enum")
                .isMissingNode())
        .isTrue();
    assertThat(
            document
                .path("paths")
                .path("/popp/practitioner/api/v1/token-deliveries")
                .path("post")
                .path("responses")
                .path("400")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("$ref")
                .asString())
        .isEqualTo("#/components/schemas/ErrorSchema");
    assertThat(schemas.path("ErrorSchema").path("required").toString())
        .hasToString("[\"errorCode\"]");
  }
}
