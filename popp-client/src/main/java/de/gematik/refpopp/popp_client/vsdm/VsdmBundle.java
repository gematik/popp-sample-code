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

/** Representation of relevant data from a VSDM 2.0 Bundle. */
public class VsdmBundle {
  private String kvnr;
  private String vorname;
  private String nachname;
  private String namenszusatz;
  private String geschlecht; // M, W, X, D
  private String geburtsdatum; // YYYYMMDD
  private String strasse;
  private String hausnummer;
  private String plz;
  private String ort;
  private String wohnsitzlaendercode; // e.g. D
  private String kostentraegerName;
  private String kostentraegerKennung; // IK
  private String versichertenStatus;
  private String versicherungsschutzBeginn;
  private String versicherungsschutzEnde;
  private String wop;
  private String besonderePersonengruppe;
  private String zuzahlungsstatus;
  private String dmpKennzeichnung;

  public String getKvnr() {
    return kvnr;
  }

  public void setKvnr(String kvnr) {
    this.kvnr = kvnr;
  }

  public String getVorname() {
    return vorname;
  }

  public void setVorname(String vorname) {
    this.vorname = vorname;
  }

  public String getNachname() {
    return nachname;
  }

  public void setNachname(String nachname) {
    this.nachname = nachname;
  }

  public String getNamenszusatz() {
    return namenszusatz;
  }

  public void setNamenszusatz(String namenszusatz) {
    this.namenszusatz = namenszusatz;
  }

  public String getGeschlecht() {
    return geschlecht;
  }

  public void setGeschlecht(String geschlecht) {
    this.geschlecht = geschlecht;
  }

  public String getGeburtsdatum() {
    return geburtsdatum;
  }

  public void setGeburtsdatum(String geburtsdatum) {
    this.geburtsdatum = geburtsdatum;
  }

  public String getStrasse() {
    return strasse;
  }

  public void setStrasse(String strasse) {
    this.strasse = strasse;
  }

  public String getHausnummer() {
    return hausnummer;
  }

  public void setHausnummer(String hausnummer) {
    this.hausnummer = hausnummer;
  }

  public String getPlz() {
    return plz;
  }

  public void setPlz(String plz) {
    this.plz = plz;
  }

  public String getOrt() {
    return ort;
  }

  public void setOrt(String ort) {
    this.ort = ort;
  }

  public String getWohnsitzlaendercode() {
    return wohnsitzlaendercode;
  }

  public void setWohnsitzlaendercode(String wohnsitzlaendercode) {
    this.wohnsitzlaendercode = wohnsitzlaendercode;
  }

  public String getKostentraegerName() {
    return kostentraegerName;
  }

  public void setKostentraegerName(String kostentraegerName) {
    this.kostentraegerName = kostentraegerName;
  }

  public String getKostentraegerKennung() {
    return kostentraegerKennung;
  }

  public void setKostentraegerKennung(String kostentraegerKennung) {
    this.kostentraegerKennung = kostentraegerKennung;
  }

  public String getVersichertenStatus() {
    return versichertenStatus;
  }

  public void setVersichertenStatus(String versichertenStatus) {
    this.versichertenStatus = versichertenStatus;
  }

  public String getVersicherungsschutzBeginn() {
    return versicherungsschutzBeginn;
  }

  public void setVersicherungsschutzBeginn(String versicherungsschutzBeginn) {
    this.versicherungsschutzBeginn = versicherungsschutzBeginn;
  }

  public String getVersicherungsschutzEnde() {
    return versicherungsschutzEnde;
  }

  public void setVersicherungsschutzEnde(String versicherungsschutzEnde) {
    this.versicherungsschutzEnde = versicherungsschutzEnde;
  }

  public String getWop() {
    return wop;
  }

  public void setWop(String wop) {
    this.wop = wop;
  }

  public String getBesonderePersonengruppe() {
    return besonderePersonengruppe;
  }

  public void setBesonderePersonengruppe(String besonderePersonengruppe) {
    this.besonderePersonengruppe = besonderePersonengruppe;
  }

  public String getZuzahlungsstatus() {
    return zuzahlungsstatus;
  }

  public void setZuzahlungsstatus(String zuzahlungsstatus) {
    this.zuzahlungsstatus = zuzahlungsstatus;
  }

  public String getDmpKennzeichnung() {
    return dmpKennzeichnung;
  }

  public void setDmpKennzeichnung(String dmpKennzeichnung) {
    this.dmpKennzeichnung = dmpKennzeichnung;
  }
}
