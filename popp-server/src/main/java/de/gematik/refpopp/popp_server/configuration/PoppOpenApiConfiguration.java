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
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PoppOpenApiConfiguration {

  @Bean
  public OpenApiCustomizer poppSchemas() {
    return openApi -> {
      if (openApi.getComponents() == null) {
        openApi.setComponents(new Components());
      }
      openApi
          .getComponents()
          .addSchemas(
              "UserAgentSchema",
              new StringSchema()
                  .description(
                      "Information about client software with: ClientId(20 characters) + / +"
                          + " VersionNumber (1 to 15 characters).")
                  .pattern("^[a-zA-Z0-9\\-]{1,20}\\/[a-zA-Z0-9\\-\\.]{1,15}$"))
          .addSchemas(
              "TelematikIdSchema",
              new StringSchema().description("Telematik-ID of a healthcare institution"))
          .addSchemas(
              "WorkplaceIdSchema",
              new StringSchema()
                  .description("Schema of the WorkplaceID of a ehealth institution")
                  .pattern("^[^\\/:*?\"<>|]{1,64}$"))
          .addSchemas(
              "PoppStatusRecordIdSchema",
              new StringSchema()
                  .description(
                      "ID of the message record containing the check-in status information")
                  .format("uuid"))
          .addSchemas(
              "KVNRSchema",
              new StringSchema()
                  .description("Never-changing part of the insured person's KVNR")
                  .pattern("^[A-Z]{1}[0-9]{9}$"))
          .addSchemas(
              "IKNrSchema",
              new StringSchema().description("Institution number (9 digits)").pattern("^[0-9]{9}$"))
          .addSchemas(
              "StateSchema",
              new StringSchema()
                  .description("Status of PoPP token generation")
                  ._enum(List.of("pending", "success", "canceled")));

      final var checkInPath =
          openApi.getPaths() == null
              ? null
              : openApi.getPaths().get("/popp/patient/api/v1/mobile/checkin");
      if (checkInPath != null && checkInPath.getPost() != null) {
        final var checkIn = checkInPath.getPost();
        checkIn.setSecurity(List.of());
        for (final var parameter : checkIn.getParameters()) {
          if ("x-kvnr".equals(parameter.getName()) || "x-iknr".equals(parameter.getName())) {
            parameter.addExtension("x-injected-by", "ZETA Guard");
            parameter.addExtension("x-client-visible", false);
          }
        }
      }
    };
  }
}
