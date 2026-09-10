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

package de.gematik.refpopp.popp_server.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "certificates.ocsp", ignoreUnknownFields = false)
@Getter
@Setter
public class OcspProperties implements InitializingBean {

  private Provider provider = Provider.REST;
  private final Rest rest = new Rest();
  private final Classpath classpath = new Classpath();

  @Override
  public void afterPropertiesSet() {
    if (provider == null) {
      throw new IllegalStateException("certificates.ocsp.provider must be configured");
    }

    if (provider == Provider.REST) {
      validateRestConfiguration();
    } else {
      validateClasspathResource(classpath.getResource(), "certificates.ocsp.classpath.resource");
    }
  }

  public int timeoutSeconds() {
    return Math.toIntExact(rest.getTimeout().getSeconds());
  }

  private void validateRestConfiguration() {
    final var timeout = rest.getTimeout();
    if (timeout == null
        || timeout.isZero()
        || timeout.isNegative()
        || timeout.getNano() != 0
        || timeout.getSeconds() > Integer.MAX_VALUE) {
      throw new IllegalStateException(
          "certificates.ocsp.rest.timeout must be a positive whole-second duration");
    }

    final var fallbackResource = rest.getFallback().getResource();
    if (fallbackResource != null) {
      validateClasspathResource(fallbackResource, "certificates.ocsp.rest.fallback.resource");
    }
  }

  private static void validateClasspathResource(
      final Resource resource, final String propertyName) {
    if (resource == null) {
      throw new IllegalStateException(propertyName + " must be configured");
    }
    if (!(resource instanceof ClassPathResource)) {
      throw new IllegalStateException(propertyName + " must use the classpath: prefix");
    }
    if (!resource.exists() || !resource.isReadable()) {
      throw new IllegalStateException(propertyName + " must reference a readable resource");
    }
  }

  public enum Provider {
    REST,
    CLASSPATH
  }

  @Getter
  @Setter
  public static class Rest {
    private Duration timeout = Duration.ofSeconds(10);
    private final Fallback fallback = new Fallback();
  }

  @Getter
  @Setter
  public static class Fallback {
    private Resource resource;
  }

  @Getter
  @Setter
  public static class Classpath {
    private Resource resource;
  }
}
