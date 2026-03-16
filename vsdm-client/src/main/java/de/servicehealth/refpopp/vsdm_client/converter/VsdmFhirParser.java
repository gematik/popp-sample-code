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

package de.servicehealth.refpopp.vsdm_client.converter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.util.HashMap;
import java.util.Map;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VsdmFhirParser {

  private static final Logger LOG = LoggerFactory.getLogger(VsdmFhirParser.class);
  private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

  // Constants for FHIR Extensions and Codes
  private static final String EXT_NAMENSZUSATZ = "humanname-namenszusatz";
  private static final String EXT_OWN_NAME = "humanname-own-name";
  private static final String EXT_STREET = "iso21090-ADXP-streetName";
  private static final String EXT_HOUSE_NUMBER = "iso21090-ADXP-houseNumber";
  private static final String EXT_WOP = "gkv/wop";
  private static final String EXT_VERSICHERTENART = "gkv/versichertenart";
  private static final String EXT_PERSONENGRUPPE = "gkv/besondere-personengruppe";
  private static final String EXT_ZUZAHLUNGSSTATUS = "gkv/zuzahlungsstatus";
  private static final String EXT_DMP_KENNZEICHNUNG = "gkv/dmp-kennzeichnung";
  private static final String EXT_KOSTENTRAEGER_ROLLE = "VSDMKostentraegerRolle";
  private static final String ROLE_HAUPTKOSTENTRAEGER = "H";

  public VsdmBundle parse(String fhirXmlContent) {
    LOG.debug("Starting to parse FHIR Bundle XML");
    VsdmBundle bundle = new VsdmBundle();
    try {
      IParser parser = FHIR_CONTEXT.newXmlParser();
      Bundle fhirBundle = parser.parseResource(Bundle.class, fhirXmlContent);

      // 1. Index Organizations by ID for lookup (Payor)
      Map<String, Organization> orgMap = indexOrganizations(fhirBundle);

      // 2. Process Entries
      for (Bundle.BundleEntryComponent entry : fhirBundle.getEntry()) {
        if (entry.getResource() instanceof Patient) {
          extractPatientData((Patient) entry.getResource(), bundle);
        } else if (entry.getResource() instanceof Coverage) {
          extractCoverageData((Coverage) entry.getResource(), bundle, orgMap);
        }
      }

      LOG.info("Successfully parsed FHIR Bundle for KVNR: {}", bundle.getKvnr());
      return bundle;

    } catch (Exception e) {
      throw new VsdmProcessingException("Error parsing FHIR XML bundle", e);
    }
  }

  private Map<String, Organization> indexOrganizations(Bundle fhirBundle) {
    Map<String, Organization> orgMap = new HashMap<>();
    for (Bundle.BundleEntryComponent entry : fhirBundle.getEntry()) {
      if (entry.getResource() instanceof Organization) {
        Organization org = (Organization) entry.getResource();
        if (org.getIdElement() != null) {
          orgMap.put(org.getIdElement().getIdPart(), org);
        }
      }
    }
    return orgMap;
  }

  private void extractPatientData(Patient p, VsdmBundle bundle) {
    if (p.hasIdentifier()) {
      bundle.setKvnr(p.getIdentifierFirstRep().getValue());
    }

    if (p.hasName()) {
      HumanName name = p.getNameFirstRep();
      bundle.setVorname(name.getGivenAsSingleString());

      if (name.hasFamilyElement()) {
        StringType family = name.getFamilyElement();
        String ownName = "";
        String nameZusatz = "";

        for (Extension ext : family.getExtension()) {
          String url = ext.getUrl();
          if (url.endsWith(EXT_NAMENSZUSATZ) && ext.getValue() instanceof StringType) {
            nameZusatz = ((StringType) ext.getValue()).getValue();
          } else if (url.endsWith(EXT_OWN_NAME) && ext.getValue() instanceof StringType) {
            ownName = ((StringType) ext.getValue()).getValue();
          }
        }
        bundle.setNachname(!ownName.isEmpty() ? ownName : family.getValue());
        bundle.setNamenszusatz(nameZusatz);
      }
    }

    if (p.hasGender()) {
      String g = p.getGender().toCode();
      if ("male".equalsIgnoreCase(g)) bundle.setGeschlecht("M");
      else if ("female".equalsIgnoreCase(g)) bundle.setGeschlecht("W");
      else bundle.setGeschlecht("X");
    }

    if (p.hasBirthDateElement()) {
      bundle.setGeburtsdatum(p.getBirthDateElement().getValueAsString().replace("-", ""));
    }

    if (p.hasAddress()) {
      Address addr = p.getAddressFirstRep();
      bundle.setOrt(addr.getCity());
      bundle.setPlz(addr.getPostalCode());
      if (addr.hasCountry()) {
        bundle.setWohnsitzlaendercode(addr.getCountry());
      }

      if (addr.hasLine()) {
        for (StringType line : addr.getLine()) {
          for (Extension ext : line.getExtension()) {
            String url = ext.getUrl();
            if (url.endsWith(EXT_STREET) && ext.getValue() instanceof StringType) {
              bundle.setStrasse(((StringType) ext.getValue()).getValue());
            } else if (url.endsWith(EXT_HOUSE_NUMBER) && ext.getValue() instanceof StringType) {
              bundle.setHausnummer(((StringType) ext.getValue()).getValue());
            }
          }
        }
      }
    }
  }

  private void extractCoverageData(
      Coverage c, VsdmBundle bundle, Map<String, Organization> orgMap) {
    for (Extension ext : c.getExtension()) {
      String url = ext.getUrl();
      if (ext.getValue() instanceof Coding) {
        String code = ((Coding) ext.getValue()).getCode();
        if (url.endsWith(EXT_WOP)) bundle.setWop(code);
        else if (url.endsWith(EXT_VERSICHERTENART)) bundle.setVersichertenStatus(code);
        else if (url.endsWith(EXT_PERSONENGRUPPE)) bundle.setBesonderePersonengruppe(code);
        else if (url.endsWith(EXT_ZUZAHLUNGSSTATUS)) bundle.setZuzahlungsstatus(code);
        else if (url.endsWith(EXT_DMP_KENNZEICHNUNG)) bundle.setDmpKennzeichnung(code);
      }
    }

    if (c.hasPeriod()) {
      if (c.getPeriod().hasStart())
        bundle.setVersicherungsschutzBeginn(
            c.getPeriod().getStartElement().getValueAsString().replace("-", ""));
      if (c.getPeriod().hasEnd())
        bundle.setVersicherungsschutzEnde(
            c.getPeriod().getEndElement().getValueAsString().replace("-", ""));
    }

    for (Reference ref : c.getPayor()) {
      boolean isHaupt = false;
      for (Extension ext : ref.getExtension()) {
        if (ext.getUrl().endsWith(EXT_KOSTENTRAEGER_ROLLE) && ext.getValue() instanceof Coding) {
          if (ROLE_HAUPTKOSTENTRAEGER.equals(((Coding) ext.getValue()).getCode())) {
            isHaupt = true;
            break;
          }
        }
      }

      if (isHaupt && ref.hasReference()) {
        String refId = ref.getReferenceElement().getIdPart();
        Organization org = orgMap.get(refId);
        if (org != null) {
          bundle.setKostentraegerName(org.getName());
          if (org.hasIdentifier()) {
            bundle.setKostentraegerKennung(org.getIdentifierFirstRep().getValue());
          }
        }
        break;
      }
    }
  }
}
