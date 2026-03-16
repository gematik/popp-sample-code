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

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
    name = "",
    propOrder = {"ts", "e", "pz"})
@XmlRootElement(name = "PN", namespace = "http://ws.gematik.de/fa/vsdm/pnw/v1.0")
public class PruefungsnachweisXML {

  @XmlAttribute(name = "CDM_VERSION", required = true)
  private String cdmVersion = "5.2.0";

  @XmlElement(name = "TS", required = true)
  private String ts;

  @XmlElement(name = "E", required = true)
  private String e;

  @XmlElement(name = "PZ", required = true)
  private String pz;

  public void setTs(String ts) {
    this.ts = ts;
  }

  public void setE(String e) {
    this.e = e;
  }

  public void setPz(String pz) {
    this.pz = pz;
  }
}
