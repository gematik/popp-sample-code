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

package de.gematik.refpopp.popp_client.connector;

import de.gematik.ws.conn.servicedirectory.ConnectorServices;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.io.StringReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class ConnectorServicesFactory {

  private final RestTemplate restTemplate;
  private final JAXBContext jaxbContext;

  @Value("${connector.end-point-url}")
  private String connectorAddress;

  public ConnectorServicesFactory(final RestTemplate restTemplate, final JAXBContext jaxbContext) {
    this.restTemplate = restTemplate;
    this.jaxbContext = jaxbContext;
  }

  public ConnectorServices createConnectorServices() {
    final var connectorSdsUrl = connectorAddress + "/connector.sds";
    log.info("Downloading connector.sds from '{}'", connectorSdsUrl);

    final ResponseEntity<String> responseEntity =
        restTemplate.getForEntity(connectorSdsUrl, String.class);
    if (!responseEntity.getStatusCode().is2xxSuccessful() || responseEntity.getBody() == null) {
      throw new IllegalStateException("Failed to load connector.sds from " + connectorSdsUrl);
    } else {
      log.info(
          "Successfully downloaded connector.sds from '{}', Status: {}",
          connectorSdsUrl,
          responseEntity.getStatusCode());
    }

    final String bodyString = responseEntity.getBody();

    try {
      final var unmarshaller = jaxbContext.createUnmarshaller();
      return (ConnectorServices) unmarshaller.unmarshal(new StringReader(bodyString));
    } catch (final JAXBException e) {
      throw new IllegalStateException(e);
    }
  }
}
