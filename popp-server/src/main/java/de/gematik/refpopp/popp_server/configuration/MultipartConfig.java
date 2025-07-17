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

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
public class MultipartConfig {

  @SuppressWarnings(
      "squid:S5693") // Sonar: max content length is set in application.properties and here
  @Bean
  public MultipartConfigElement multipartConfigElement() {
    final var factory = new MultipartConfigFactory();
    factory.setMaxFileSize(DataSize.ofGigabytes(2));
    factory.setFileSizeThreshold(DataSize.ofKilobytes(1024));
    factory.setLocation("/tmp");
    factory.setMaxRequestSize(DataSize.ofGigabytes(2));
    return factory.createMultipartConfig();
  }
}
