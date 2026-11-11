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

/**
 * Liest das Dienstverzeichnis des Konnektors und hält es lokal vor.
 *
 * <p>Umsetzung von TIP1-A_4967 „Cachen von Service-Endpunkten“ (gemILF_PS V2.29.0): „Das
 * Primärsystem MUSS die Endpunkte der Services, die der Konnektor anbietet, aus dem
 * Dienstverzeichnisdienst initial unter einem FQDN ermitteln, der im Primärsystem konfiguriert ist,
 * und die Endpunktinformationen der Dienste lokal cachen. Wenn ein Verbindungsproblem auftritt
 * (Dienst nicht erreichbar), muss das Primärsystem einen Refresh auf alle Endpunktinformationen des
 * Dienstverzeichnisdienstes durchführen.“
 *
 * <p>{@link #createConnectorServices()} lädt {@code connector.sds} beim ersten Aufruf und liefert
 * danach den gecachten Stand. {@link #refresh()} lädt das Dienstverzeichnis neu; ein
 * fehlgeschlagener Refresh leert den Cache, damit der nächste Aufruf es erneut lädt.
 */
@Component
@Slf4j
public class ConnectorServicesFactory {

  private final RestTemplate restTemplate;
  private final JAXBContext jaxbContext;

  @Value("${connector.end-point-url}")
  private String connectorAddress;

  private ConnectorServices cachedConnectorServices;

  public ConnectorServicesFactory(final RestTemplate restTemplate, final JAXBContext jaxbContext) {
    this.restTemplate = restTemplate;
    this.jaxbContext = jaxbContext;
  }

  /** Liefert das gecachte Dienstverzeichnis und lädt es nur, wenn noch keines vorliegt. */
  public synchronized ConnectorServices createConnectorServices() {
    if (cachedConnectorServices == null) {
      cachedConnectorServices = download();
    }
    return cachedConnectorServices;
  }

  /**
   * Refresh auf alle Endpunktinformationen nach einem Verbindungsproblem (TIP1-A_4967). Schlägt das
   * Laden fehl, bleibt der Cache leer und der nächste Aufruf von {@link #createConnectorServices()}
   * lädt erneut.
   */
  public synchronized void refresh() {
    cachedConnectorServices = null;
    cachedConnectorServices = download();
  }

  private ConnectorServices download() {
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
