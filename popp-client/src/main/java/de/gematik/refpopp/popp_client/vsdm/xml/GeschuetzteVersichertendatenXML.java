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
    name = "UC_GeschuetzteVersichertendatenXML",
    namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
@XmlAccessorType(XmlAccessType.FIELD)
public class GeschuetzteVersichertendatenXML {

  @XmlAttribute(name = "CDM_VERSION")
  private String cdmVersion = "5.2.0";

  @XmlElement(name = "Versicherter", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
  private Versicherter versicherter;

  public void setVersicherter(Versicherter versicherter) {
    this.versicherter = versicherter;
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(
      propOrder = {
        "zuzahlungsstatus",
        "besonderePersonengruppe",
        "dmpKennzeichnung",
        "selektivvertraege",
        "ruhenVersicherungsschutz"
      })
  public static class Versicherter {
    @XmlElement(name = "Zuzahlungsstatus", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private Zuzahlungsstatus zuzahlungsstatus;

    @XmlElement(
        name = "BesonderePersonengruppe",
        namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String besonderePersonengruppe;

    @XmlElement(name = "DMP_Kennzeichnung", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String dmpKennzeichnung;

    @XmlElement(name = "Selektivvertraege", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private Selektivvertraege selektivvertraege;

    @XmlElement(
        name = "Ruhen_Versicherungsschutz",
        namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private RuhenVersicherungsschutz ruhenVersicherungsschutz;

    public void setZuzahlungsstatus(Zuzahlungsstatus z) {
      this.zuzahlungsstatus = z;
    }

    public void setBesonderePersonengruppe(String b) {
      this.besonderePersonengruppe = b;
    }

    public void setDmpKennzeichnung(String d) {
      this.dmpKennzeichnung = d;
    }

    public void setSelektivvertraege(Selektivvertraege s) {
      this.selektivvertraege = s;
    }

    public void setRuhenVersicherungsschutz(RuhenVersicherungsschutz r) {
      this.ruhenVersicherungsschutz = r;
    }
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(propOrder = {"status", "gueltigBis"})
  public static class Zuzahlungsstatus {
    @XmlElement(name = "Status", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String status;

    @XmlElement(name = "Gueltig_bis", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String gueltigBis;

    public void setStatus(String s) {
      this.status = s;
    }

    public void setGueltigBis(String g) {
      this.gueltigBis = g;
    }
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(propOrder = {"aerztlich", "zahnaerztlich"})
  public static class Selektivvertraege {
    @XmlElement(name = "Aerztlich", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String aerztlich; // 0 or 1

    @XmlElement(name = "Zahnaerztlich", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String zahnaerztlich; // 0 or 1

    public void setAerztlich(String a) {
      this.aerztlich = a;
    }

    public void setZahnaerztlich(String z) {
      this.zahnaerztlich = z;
    }
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(propOrder = {"beginn", "ende", "artDesRuhens"})
  public static class RuhenVersicherungsschutz {
    @XmlElement(name = "Beginn", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String beginn;

    @XmlElement(name = "Ende", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String ende;

    @XmlElement(name = "Art_des_Ruhens", namespace = "http://ws.gematik.de/fa/vsdm/vsd/v5.2")
    private String artDesRuhens;

    public void setBeginn(String b) {
      this.beginn = b;
    }

    public void setEnde(String e) {
      this.ende = e;
    }

    public void setArtDesRuhens(String a) {
      this.artDesRuhens = a;
    }
  }
}
