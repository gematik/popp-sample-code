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

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.util.unit.DataSize;

class MultipartConfigTest {

  @Test
  void multipartConfigElementCreatesConfigWithCorrectFileSizeLimits() {
    // given
    final var factory = new MultipartConfigFactory();
    factory.setMaxFileSize(DataSize.ofGigabytes(2));
    factory.setFileSizeThreshold(DataSize.ofKilobytes(1024));
    factory.setLocation("/tmp");
    factory.setMaxRequestSize(DataSize.ofGigabytes(2));

    // when
    final var config = factory.createMultipartConfig();

    // then
    assertThat(config.getMaxFileSize()).isEqualTo(DataSize.ofGigabytes(2).toBytes());
    assertThat(config.getFileSizeThreshold()).isEqualTo(DataSize.ofKilobytes(1024).toBytes());
    assertThat(config.getLocation()).isEqualTo("/tmp");
    assertThat(config.getMaxRequestSize()).isEqualTo(DataSize.ofGigabytes(2).toBytes());
  }

  @Test
  void multipartConfigElementHandlesNullLocationGracefully() {
    // given
    final var factory = new MultipartConfigFactory();
    factory.setLocation(null);

    // when
    final var config = factory.createMultipartConfig();

    // then
    assertThat(config.getLocation()).isEqualTo("");
  }
}
