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

package de.gematik.refpopp.popp_server.scenario.common.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.gematik.refpopp.popp_server.config.OcspProperties;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class OcspResponseProviderConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(ProviderConfiguration.class);

  @Test
  void registersRestProviderByDefault() {
    contextRunner.run(
        context ->
            assertThat(context)
                .hasSingleBean(OcspResponseProvider.class)
                .hasSingleBean(RestOcspResponseProvider.class)
                .doesNotHaveBean(ClasspathOcspResponseProvider.class));
  }

  @Test
  void registersClasspathProviderWhenConfigured() {
    contextRunner
        .withPropertyValues(
            "certificates.ocsp.provider=classpath",
            "certificates.ocsp.classpath.resource=classpath:ocsp-response.txt")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(OcspResponseProvider.class)
                    .hasSingleBean(ClasspathOcspResponseProvider.class)
                    .doesNotHaveBean(RestOcspResponseProvider.class));
  }

  @Test
  void acceptsConfiguredRestFallback() {
    contextRunner
        .withPropertyValues(
            "certificates.ocsp.provider=rest",
            "certificates.ocsp.rest.fallback.resource=classpath:ocsp-response.txt")
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void rejectsClasspathProviderWithoutResource() {
    contextRunner
        .withPropertyValues("certificates.ocsp.provider=classpath")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage("certificates.ocsp.classpath.resource must be configured");
            });
  }

  @Test
  void rejectsNonClasspathResource() {
    contextRunner
        .withPropertyValues(
            "certificates.ocsp.provider=classpath",
            "certificates.ocsp.classpath.resource=file:ocsp-response.txt")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "certificates.ocsp.classpath.resource must use the classpath: prefix");
            });
  }

  @Test
  void rejectsUnreadableFallbackResource() {
    contextRunner
        .withPropertyValues(
            "certificates.ocsp.rest.fallback.resource=classpath:missing-response.der")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "certificates.ocsp.rest.fallback.resource must reference a readable"
                          + " resource");
            });
  }

  @Test
  void rejectsTimeoutThatIsNotInWholeSeconds() {
    contextRunner
        .withPropertyValues("certificates.ocsp.rest.timeout=1500ms")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "certificates.ocsp.rest.timeout must be a positive whole-second duration");
            });
  }

  @Test
  void rejectsUnknownProvider() {
    contextRunner
        .withPropertyValues("certificates.ocsp.provider=unknown")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsUnknownProperty() {
    contextRunner
        .withPropertyValues("certificates.ocsp.rest.timout=10s")
        .run(context -> assertThat(context).hasFailed());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(OcspProperties.class)
  @Import({ClasspathOcspResponseProvider.class, RestOcspResponseProvider.class})
  static class ProviderConfiguration {

    @Bean
    OcspResponderUrlExtractor ocspResponderUrlExtractor() {
      return mock(OcspResponderUrlExtractor.class);
    }

    @Bean
    OcspTransceiverClient ocspTransceiverClient() {
      return mock(OcspTransceiverClient.class);
    }

    @Bean
    SessionContainer sessionContainer() {
      return mock(SessionContainer.class);
    }
  }
}
