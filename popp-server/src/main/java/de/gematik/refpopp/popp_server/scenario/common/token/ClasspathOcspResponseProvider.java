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

import de.gematik.poppcommons.api.enums.BdeErrorCode;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.config.OcspProperties;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "certificates.ocsp.provider", havingValue = "classpath")
final class ClasspathOcspResponseProvider implements OcspResponseProvider {

  private final Resource responseResource;

  @Autowired
  ClasspathOcspResponseProvider(final OcspProperties properties) {
    this(properties.getClasspath().getResource());
  }

  ClasspathOcspResponseProvider(final Resource responseResource) {
    this.responseResource = responseResource;
  }

  @Override
  public String getResponse(final OcspRequest request) {
    try (final var inputStream = responseResource.getInputStream()) {
      return Base64.getEncoder().encodeToString(inputStream.readAllBytes());
    } catch (final Exception e) {
      throw new ScenarioException(
          request.sessionId(),
          "Could not read OCSP response from " + responseResource.getDescription(),
          BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR);
    }
  }
}
