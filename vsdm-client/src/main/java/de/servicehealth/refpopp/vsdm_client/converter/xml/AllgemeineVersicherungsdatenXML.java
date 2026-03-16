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

package de.servicehealth.refpopp.vsdm_client.converter.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlRootElement(
    name = "UC_AllgemeineVersicherungsdatenXML",
    namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
@XmlAccessorType(XmlAccessType.FIELD)
public class AllgemeineVersicherungsdatenXML {

  @XmlAttribute(name = "CDM_VERSION")
  private String cdmVersion = "5.2.0";

  @XmlElement(name = "Versicherter", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
  private Versicherter versicherter;

  public void setVersicherter(Versicherter versicherter) {
    this.versicherter = versicherter;
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  public static class Versicherter {
    @XmlElement(name = "Versicherungsschutz", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private Versicherungsschutz versicherungsschutz;

    @XmlElement(name = "Zusatzinfos", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private Zusatzinfos zusatzinfos;

    public void setVersicherungsschutz(Versicherungsschutz versicherungsschutz) {
      this.versicherungsschutz = versicherungsschutz;
    }

    public void setZusatzinfos(Zusatzinfos zusatzinfos) {
      this.zusatzinfos = zusatzinfos;
    }
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(propOrder = {"beginn", "ende", "kostentraeger"})
  public static class Versicherungsschutz {
    @XmlElement(name = "Beginn", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String beginn;

    @XmlElement(name = "Ende", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String ende;

    @XmlElement(name = "Kostentraeger", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private Kostentraeger kostentraeger;

    public void setBeginn(String beginn) {
      this.beginn = beginn;
    }

    public void setEnde(String ende) {
      this.ende = ende;
    }

    public void setKostentraeger(Kostentraeger kostentraeger) {
      this.kostentraeger = kostentraeger;
    }
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(propOrder = {"kostentraegerkennung", "name", "abrechnenderKostentraeger"})
  public static class Kostentraeger {
    @XmlElement(name = "Kostentraegerkennung", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String kostentraegerkennung;

    @XmlElement(name = "Name", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String name;

    @XmlElement(
        name = "AbrechnenderKostentraeger",
        namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private AbrechnenderKostentraeger abrechnenderKostentraeger;

    public void setKostentraegerkennung(String k) {
      this.kostentraegerkennung = k;
    }

    public void setName(String n) {
      this.name = n;
    }

    public void setAbrechnenderKostentraeger(AbrechnenderKostentraeger a) {
      this.abrechnenderKostentraeger = a;
    }
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(propOrder = {"kostentraegerkennung", "name"})
  public static class AbrechnenderKostentraeger {
    @XmlElement(name = "Kostentraegerkennung", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String kostentraegerkennung;

    @XmlElement(name = "Name", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String name;

    public void setKostentraegerkennung(String k) {
      this.kostentraegerkennung = k;
    }

    public void setName(String n) {
      this.name = n;
    }
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  public static class Zusatzinfos {
    @XmlElement(name = "ZusatzinfosGKV", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private ZusatzinfosGKV zusatzinfosGKV;

    public void setZusatzinfosGKV(ZusatzinfosGKV z) {
      this.zusatzinfosGKV = z;
    }
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(propOrder = {"versichertenart", "zusatzinfosAbrechnungGKV"})
  public static class ZusatzinfosGKV {
    @XmlElement(name = "Versichertenart", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String versichertenart = "1";

    @XmlElement(
        name = "Zusatzinfos_Abrechnung_GKV",
        namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private ZusatzinfosAbrechnungGKV zusatzinfosAbrechnungGKV;

    public void setVersichertenart(String v) {
      this.versichertenart = v;
    }

    public void setZusatzinfosAbrechnungGKV(ZusatzinfosAbrechnungGKV z) {
      this.zusatzinfosAbrechnungGKV = z;
    }
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(propOrder = {"kostenerstattung", "wop", "versichertenstatus"})
  public static class ZusatzinfosAbrechnungGKV {
    @XmlElement(name = "Kostenerstattung", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private Boolean kostenerstattung;

    @XmlElement(name = "WOP", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String wop;

    @XmlElement(name = "Versichertenstatus", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String versichertenstatus;

    public void setKostenerstattung(Boolean k) {
      this.kostenerstattung = k;
    }

    public void setWop(String v) {
      this.wop = v;
    }

    public void setVersichertenstatus(String v) {
      this.versichertenstatus = v;
    }
  }
}
