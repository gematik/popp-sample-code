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

package de.gematik.refpopp.popp_client.vsdm.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlRootElement(
    name = "UC_PersoenlicheVersichertendatenXML",
    namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
@XmlAccessorType(XmlAccessType.FIELD)
public class PersoenlicheVersichertendatenXML {

  @XmlAttribute(name = "CDM_VERSION")
  private String cdmVersion = "5.2.0";

  @XmlElement(name = "Versicherter", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
  private Versicherter versicherter;

  public void setVersicherter(Versicherter versicherter) {
    this.versicherter = versicherter;
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  public static class Versicherter {
    @XmlElement(name = "Versicherten_ID", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String versichertenId;

    @XmlElement(name = "Person", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private Person person;

    public void setVersichertenId(String versichertenId) {
      this.versichertenId = versichertenId;
    }

    public void setPerson(Person person) {
      this.person = person;
    }
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(
      propOrder = {
        "geburtsdatum",
        "vorname",
        "nachname",
        "geschlecht",
        "vorsatzwort",
        "namenszusatz",
        "titel",
        "strassenAdresse"
      })
  public static class Person {
    @XmlElement(name = "Geburtsdatum", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String geburtsdatum;

    @XmlElement(name = "Vorname", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String vorname;

    @XmlElement(name = "Nachname", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String nachname;

    @XmlElement(name = "Geschlecht", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String geschlecht;

    @XmlElement(name = "Vorsatzwort", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String vorsatzwort;

    @XmlElement(name = "Namenszusatz", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String namenszusatz;

    @XmlElement(name = "Titel", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String titel;

    @XmlElement(name = "StrassenAdresse", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private StrassenAdresse strassenAdresse;

    public void setGeburtsdatum(String v) {
      this.geburtsdatum = v;
    }

    public void setVorname(String v) {
      this.vorname = v;
    }

    public void setNachname(String v) {
      this.nachname = v;
    }

    public void setGeschlecht(String v) {
      this.geschlecht = v;
    }

    public void setVorsatzwort(String v) {
      this.vorsatzwort = v;
    }

    public void setNamenszusatz(String v) {
      this.namenszusatz = v;
    }

    public void setTitel(String v) {
      this.titel = v;
    }

    public void setStrassenAdresse(StrassenAdresse v) {
      this.strassenAdresse = v;
    }
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(
      propOrder = {"postleitzahl", "ort", "land", "strasse", "hausnummer", "anschriftenzusatz"})
  public static class StrassenAdresse {
    @XmlElement(name = "Postleitzahl", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String postleitzahl;

    @XmlElement(name = "Ort", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String ort;

    @XmlElement(name = "Land", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private Land land;

    @XmlElement(name = "Strasse", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String strasse;

    @XmlElement(name = "Hausnummer", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String hausnummer;

    @XmlElement(name = "Anschriftenzusatz", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String anschriftenzusatz;

    public void setPostleitzahl(String v) {
      this.postleitzahl = v;
    }

    public void setOrt(String v) {
      this.ort = v;
    }

    public void setLand(Land v) {
      this.land = v;
    }

    public void setStrasse(String v) {
      this.strasse = v;
    }

    public void setHausnummer(String v) {
      this.hausnummer = v;
    }

    public void setAnschriftenzusatz(String v) {
      this.anschriftenzusatz = v;
    }
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  public static class Land {
    @XmlElement(name = "Wohnsitzlaendercode", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String wohnsitzlaendercode;

    public void setWohnsitzlaendercode(String v) {
      this.wohnsitzlaendercode = v;
    }
  }
}
