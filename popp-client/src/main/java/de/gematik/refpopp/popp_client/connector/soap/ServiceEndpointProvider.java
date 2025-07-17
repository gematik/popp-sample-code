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

package de.gematik.refpopp.popp_client.connector.soap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ServiceEndpointProvider {

  private final ServicePathExtractor servicePathExtractor;

  public ServiceEndpointProvider(final ServicePathExtractor servicePathExtractor) {
    this.servicePathExtractor = servicePathExtractor;
  }

  public ServiceEndpoint getCardServiceEndpoint() {
    final var cardServicePath = servicePathExtractor.getCardServicePath();
    return determineServiceEndpoint(cardServicePath);
  }

  public String getEventServiceFullEndpoint() {
    final var cardServicePath = servicePathExtractor.getEventServicePath();
    return servicePathExtractor.getConnectorUrl()
        + determineServiceEndpoint(cardServicePath).getEndpoint();
  }

  public ServiceEndpoint getEventServiceEndpoint() {
    final var eventServicePath = servicePathExtractor.getEventServicePath();
    return determineServiceEndpoint(eventServicePath);
  }

  public String getCardServiceFullEndpoint() {
    final var eventServicePath = servicePathExtractor.getCardServicePath();
    return servicePathExtractor.getConnectorUrl()
        + determineServiceEndpoint(eventServicePath).getEndpoint();
  }

  private ServiceEndpoint determineServiceEndpoint(final ServicePath servicePath) {
    final var serviceEndpoint = new ServiceEndpoint();
    serviceEndpoint.setVersion(servicePath.getVersion());
    serviceEndpoint.setEndpoint(servicePath.getPath());
    return serviceEndpoint;
  }
}
