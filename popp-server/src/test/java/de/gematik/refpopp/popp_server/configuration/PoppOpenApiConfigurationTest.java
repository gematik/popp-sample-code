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

package de.gematik.refpopp.popp_server.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import java.util.List;
import org.junit.jupiter.api.Test;

class PoppOpenApiConfigurationTest {

  private final PoppOpenApiConfiguration configuration = new PoppOpenApiConfiguration();

  @Test
  void createsComponentsAndRegistersSharedSchemasWithoutPaths() {
    final var openApi = new OpenAPI();

    configuration.poppSchemas().customise(openApi);

    final var schemas = openApi.getComponents().getSchemas();
    assertThat(schemas)
        .containsKeys(
            "UserAgentSchema",
            "TelematikIdSchema",
            "WorkplaceIdSchema",
            "PoppStatusRecordIdSchema",
            "KVNRSchema",
            "IKNrSchema",
            "StateSchema");
    assertThat(schemas.get("UserAgentSchema").getPattern())
        .isEqualTo("^[a-zA-Z0-9\\-]{1,20}\\/[a-zA-Z0-9\\-\\.]{1,15}$");
    assertThat(schemas.get("WorkplaceIdSchema").getPattern()).isEqualTo("^[^\\/:*?\"<>|]{1,64}$");
    assertThat(schemas.get("PoppStatusRecordIdSchema").getFormat()).isEqualTo("uuid");
    assertThat(schemas.get("KVNRSchema").getPattern()).isEqualTo("^[A-Z]{1}[0-9]{9}$");
    assertThat(schemas.get("IKNrSchema").getPattern()).isEqualTo("^[0-9]{9}$");
    assertThat(schemas.get("StateSchema").getEnum())
        .containsExactlyElementsOf(List.of("pending", "success", "canceled"));
  }
}
