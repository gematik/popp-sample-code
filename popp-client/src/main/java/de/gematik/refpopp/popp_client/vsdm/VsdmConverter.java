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

package de.gematik.refpopp.popp_client.vsdm;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VsdmConverter {

  private static final Logger LOG = LoggerFactory.getLogger(VsdmConverter.class);

  private final VsdmFhirParser parser;
  private final VsdmSoapBuilder soapBuilder;

  public ReadVSDResponse createReadVSDResponse(String fhirXmlContent, String poppToken) {
    // 1. Parse FHIR
    VsdmBundle bundle = parser.parse(fhirXmlContent);

    LOG.debug("Parsed Bundle Data:");
    LOG.debug("KVNR: {}", bundle.getKvnr());
    LOG.debug("Name: {} {}", bundle.getVorname(), bundle.getNachname());
    LOG.debug("Geburtsdatum: {}", bundle.getGeburtsdatum());
    LOG.debug(
        "Adresse: {} {}, {} {}",
        bundle.getStrasse(),
        bundle.getHausnummer(),
        bundle.getPlz(),
        bundle.getOrt());
    LOG.debug(
        "Kostenträger: {} (IK: {})",
        bundle.getKostentraegerName(),
        bundle.getKostentraegerKennung());
    LOG.debug(
        "Versicherungsschutz: {} - {}",
        bundle.getVersicherungsschutzBeginn(),
        bundle.getVersicherungsschutzEnde());
    LOG.debug("Status: {}", bundle.getVersichertenStatus());
    LOG.debug("WOP: {}", bundle.getWop());
    LOG.debug("Besondere Personengruppe: {}", bundle.getBesonderePersonengruppe());
    LOG.debug("--------------------------------------------------");

    // 2. Build SOAP Response Object
    return soapBuilder.createResponse(bundle, poppToken);
  }
}
