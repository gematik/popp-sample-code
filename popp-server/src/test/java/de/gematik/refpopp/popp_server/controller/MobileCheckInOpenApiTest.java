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
import de.gematik.refpopp.popp_server.tokengeneration.MobileCheckInRequestValidator;
import de.gematik.refpopp.popp_server.tokengeneration.PoppTokenGenerationRecordService;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(MobileCheckInController.class)
@EnableConfigurationProperties(SpringDocConfigProperties.class)
@Import({PoppOpenApiConfiguration.class, MobileCheckInRequestValidator.class})
@ImportAutoConfiguration({
  SpringDocConfiguration.class,
  SpringDocSpecPropertiesConfiguration.class,
  SpringDocWebMvcConfiguration.class
})
class MobileCheckInOpenApiTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PoppTokenGenerationRecordService recordService;

  @Test
  void documentsCheckInRequestAndHeaders() throws Exception {
    final var document = loadDocument();
    final var operation =
        document.path("paths").path("/popp/patient/api/v1/mobile/checkin").path("post");
    final var schemas = document.path("components").path("schemas");

    assertThat(operation.path("operationId").asString()).isEqualTo("checkInGID");
    assertThat(operation.path("tags").get(0).asString()).isEqualTo("Online Check-in");
    assertThat(operation.path("security")).hasToString("[]");
    assertThat(operation.path("requestBody").path("required").asBoolean()).isTrue();
    assertThat(
            operation
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("$ref")
                .asString())
        .isEqualTo("#/components/schemas/CheckInRequestSchema");

    final var parameters =
        operation
            .path("parameters")
            .valueStream()
            .collect(
                Collectors.toMap(
                    parameter -> parameter.path("name").asString(), Function.identity()));
    assertThat(parameters).containsOnlyKeys("x-useragent", "x-kvnr", "x-iknr");
    assertHeader(parameters, "x-useragent", "UserAgentSchema");
    assertHeader(parameters, "x-kvnr", "KVNRSchema");
    assertHeader(parameters, "x-iknr", "IKNrSchema");
    for (final var name : new String[] {"x-kvnr", "x-iknr"}) {
      assertThat(parameters.get(name).path("x-injected-by").asString()).isEqualTo("ZETA Guard");
      assertThat(parameters.get(name).path("x-client-visible").asBoolean()).isFalse();
    }

    final var request = schemas.path("CheckInRequestSchema");
    assertThat(request.path("required").toString()).contains("\"telematikId\"");
    assertThat(request.path("required").toString()).doesNotContain("\"workplaceId\"");
    assertRef(request.path("properties").path("telematikId"), "TelematikIdSchema");
    assertRef(request.path("properties").path("workplaceId"), "WorkplaceIdSchema");
  }

  @Test
  void documentsCheckInResponsesAndSchemas() throws Exception {
    final var document = loadDocument();
    final var operation =
        document.path("paths").path("/popp/patient/api/v1/mobile/checkin").path("post");
    final var schemas = document.path("components").path("schemas");
    final var responses = operation.path("responses");
    for (final var code : new String[] {"200", "201"}) {
      assertRef(
          responses.path(code).path("content").path("application/json").path("schema"),
          "StatusPoPPTokenGenerationSchema");
    }
    for (final var code : new String[] {"400", "404", "500"}) {
      assertRef(
          responses.path(code).path("content").path("application/json").path("schema"),
          "ErrorSchema");
    }
    final var response = schemas.path("StatusPoPPTokenGenerationSchema");
    assertThat(response.path("required").toString())
        .contains("\"state\"", "\"poppStatusRecordId\"");
    assertRef(response.path("properties").path("state"), "StateSchema");
    assertRef(response.path("properties").path("poppStatusRecordId"), "PoppStatusRecordIdSchema");
    assertThat(schemas.path("StateSchema").path("enum"))
        .hasToString("[\"pending\",\"success\",\"canceled\"]");
    assertThat(schemas.path("PoppStatusRecordIdSchema").path("format").asString())
        .isEqualTo("uuid");
    assertThat(schemas.path("KVNRSchema").path("pattern").asString())
        .isEqualTo("^[A-Z]{1}[0-9]{9}$");
    assertThat(schemas.path("IKNrSchema").path("pattern").asString()).isEqualTo("^[0-9]{9}$");
    assertThat(schemas.path("UserAgentSchema").path("pattern").asString())
        .isEqualTo("^[a-zA-Z0-9\\-]{1,20}\\/[a-zA-Z0-9\\-\\.]{1,15}$");
    assertThat(schemas.path("WorkplaceIdSchema").path("pattern").asString())
        .isEqualTo("^[^\\/:*?\"<>|]{1,64}$");
    assertThat(schemas.path("ErrorSchema").path("required").toString()).contains("\"errorCode\"");
  }

  private JsonNode loadDocument() throws Exception {
    final var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    return new ObjectMapper().readTree(result.getResponse().getContentAsString());
  }

  private void assertHeader(
      final Map<String, JsonNode> parameters, final String name, final String schemaName) {
    final var header = parameters.get(name);
    assertThat(header.path("in").asString()).isEqualTo("header");
    assertThat(header.path("required").asBoolean()).isTrue();
    assertRef(header.path("schema"), schemaName);
  }

  private void assertRef(final JsonNode schema, final String name) {
    assertThat(schema.path("$ref").asString()).isEqualTo("#/components/schemas/" + name);
  }
}
