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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;

class TokenDeliveryOpenApiConfigurationTest {

  private final TokenDeliveryOpenApiConfiguration configuration =
      new TokenDeliveryOpenApiConfiguration();

  @Test
  void skipsMissingResponseWithoutCreatingComponents() {
    final var openApi = new OpenAPI();

    configuration.tokenDeliverySchemas().customise(openApi);

    assertThat(openApi.getComponents()).isNull();
  }

  @Test
  void inlinesDeliveryItemIntoResponseWithoutReplacingOtherSchemas() {
    final var item = new Schema<>().addProperty("token", new StringSchema());
    final var tokens =
        new ArraySchema().items(new Schema<>().$ref("#/components/schemas/TokenDeliveryItem"));
    final var response = new Schema<>().addProperty("popptokens", tokens);
    final var other = new StringSchema();
    final var components =
        new Components()
            .addSchemas("TokenResponseSchema", response)
            .addSchemas("TokenDeliveryItem", item)
            .addSchemas("OtherSchema", other);
    final var openApi = new OpenAPI().components(components);

    configuration.tokenDeliverySchemas().customise(openApi);

    assertThat(openApi.getComponents()).isSameAs(components);
    assertThat(tokens.getItems()).isSameAs(item);
    assertThat(components.getSchemas()).doesNotContainKey("TokenDeliveryItem");
    assertThat(components.getSchemas().get("OtherSchema")).isSameAs(other);
  }

  @Test
  void rejectsResponseWithMissingDeliveryItemSchema() {
    final var response = new Schema<>().addProperty("popptokens", new ArraySchema());
    final var components = new Components().addSchemas("TokenResponseSchema", response);
    final var openApi = new OpenAPI().components(components);
    final var customizer = configuration.tokenDeliverySchemas();

    assertThatThrownBy(() -> customizer.customise(openApi))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("TokenDeliveryItem schema is missing from the token response");
  }
}
