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

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TokenDeliveryOpenApiConfiguration {

  @Bean
  public OpenApiCustomizer tokenDeliverySchemas() {
    return openApi -> {
      if (openApi.getComponents() != null) {
        inlineTokenResponseItem(openApi.getComponents());
      }
    };
  }

  private void inlineTokenResponseItem(final Components components) {
    final Schema<?> response = components.getSchemas().get("TokenResponseSchema");
    if (response != null) {
      final Schema<?> item = components.getSchemas().get("TokenDeliveryItem");
      if (item == null) {
        throw new IllegalStateException(
            "TokenDeliveryItem schema is missing from the token response");
      }
      final Schema<?> tokens = response.getProperties().get("popptokens");
      tokens.setItems(item);
      components.getSchemas().remove("TokenDeliveryItem");
    }
  }
}
