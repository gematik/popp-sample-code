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

import de.gematik.refpopp.popp_server.controller.dto.CheckInStatusResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class MobileCheckInOpenApiConfigurationTest {

  private final MobileCheckInOpenApiConfiguration configuration =
      new MobileCheckInOpenApiConfiguration();

  @Test
  void registersStateSchemaWithoutExistingComponents() {
    final var openApi = new OpenAPI();

    configuration.mobileCheckInSchemas().customise(openApi);

    final var state = openApi.getComponents().getSchemas().get("StateSchema");
    assertThat(state.getType()).isEqualTo("string");
    assertThat(state.getEnum()).containsExactly("pending", "success", "canceled");
  }

  @Test
  void preservesExistingComponentsAndSchemas() {
    final var existing = new StringSchema();
    final var components = new Components().addSchemas("ExistingSchema", existing);
    final var openApi = new OpenAPI().components(components);

    configuration.mobileCheckInSchemas().customise(openApi);

    assertThat(openApi.getComponents()).isSameAs(components);
    assertThat(components.getSchemas().get("ExistingSchema")).isSameAs(existing);
  }

  @Test
  void registersErrorTypeWithRequiredCodeAndOptionalDetail() {
    final var openApi = new OpenAPI();

    configuration.mobileCheckInSchemas().customise(openApi);

    final Schema<?> error = openApi.getComponents().getSchemas().get("ErrorType");
    assertThat(error.getType()).isEqualTo("object");
    assertThat(error.getRequired()).containsExactly("errorCode");
    assertThat(error.getProperties()).containsOnlyKeys("errorCode", "errorDetail");
    assertThat(error.getProperties().get("errorCode").getType()).isEqualTo("string");
    assertThat(error.getProperties().get("errorCode").getEnum()).isNull();
    assertThat(error.getProperties().get("errorDetail").getType()).isEqualTo("string");
  }

  @Test
  void resolvesResponseWithContractReferencesAndNoRequiredProperties() {
    final var resolved =
        ModelConverters.getInstance()
            .resolveAsResolvedSchema(
                new AnnotatedType(CheckInStatusResponse.class).resolveAsRef(true));
    final var openApi =
        new OpenAPI()
            .components(new Components().schemas(new LinkedHashMap<>(resolved.referencedSchemas)));
    new PoppOpenApiConfiguration().poppSchemas().customise(openApi);
    configuration.mobileCheckInSchemas().customise(openApi);

    assertThat(resolved.schema.get$ref())
        .isEqualTo("#/components/schemas/StatusPoPPTokenGenerationSchema");
    final var schemas = openApi.getComponents().getSchemas();
    final Schema<?> response = schemas.get("StatusPoPPTokenGenerationSchema");
    assertThat(response.getType()).isEqualTo("object");
    assertThat(response.getRequired()).isNullOrEmpty();
    assertThat(response.getProperties()).containsOnlyKeys("state", "poppStatusRecordId");
    assertThat(response.getProperties().get("state").get$ref())
        .isEqualTo("#/components/schemas/StateSchema");
    assertThat(response.getProperties().get("poppStatusRecordId").get$ref())
        .isEqualTo("#/components/schemas/PoppStatusRecordIdSchema");
    assertThat(schemas).containsKey("PoppStatusRecordIdSchema");
    assertThat(schemas.get("PoppStatusRecordIdSchema").getType()).isEqualTo("string");
    assertThat(schemas.get("PoppStatusRecordIdSchema").getFormat()).isEqualTo("uuid");
  }
}
