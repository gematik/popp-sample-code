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

import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers OpenAPI schemas referenced by the mobile check-in response DTOs.
 *
 * <p>Defines {@code StateSchema} with the status values and {@code ErrorType} with the error
 * response fields from the API contract so that the generated OpenAPI document and Swagger UI can
 * resolve the schema references. This configuration does not perform runtime validation or change
 * the stored check-in status.
 */
@Configuration
public class MobileCheckInOpenApiConfiguration {

  /**
   * Creates a customizer that adds the string schema for {@code pending}, {@code success} and
   * {@code canceled}, along with the error response schema, to the OpenAPI components.
   *
   * @return the customizer registering {@code StateSchema} and {@code ErrorType}
   */
  @Bean
  public OpenApiCustomizer mobileCheckInSchemas() {
    return openApi -> {
      if (openApi.getComponents() == null) {
        openApi.setComponents(new Components());
      }
      final Schema<String> stateSchema = new StringSchema();
      stateSchema.setDescription("The status of PoPP-Token generation");
      stateSchema.setEnum(
          List.of(
              PoppTokenGenerationStatus.PENDING.getValue(),
              PoppTokenGenerationStatus.SUCCESS.getValue(),
              PoppTokenGenerationStatus.CANCELED.getValue()));
      openApi.getComponents().addSchemas("StateSchema", stateSchema);
      openApi.getComponents().addSchemas("ErrorType", errorTypeSchema());
    };
  }

  private Schema<Object> errorTypeSchema() {
    final Schema<Object> schema = new Schema<>();
    schema.setType("object");
    schema.setDescription("error object with additional information about the occurred error");
    final var code = new StringSchema();
    code.setDescription("Error condition specifier");
    final var detail = new StringSchema();
    detail.setDescription("additional details regarding the error condition (if applicable)");
    schema.addProperty("errorCode", code);
    schema.addProperty("errorDetail", detail);
    schema.setRequired(List.of("errorCode"));
    return schema;
  }
}
