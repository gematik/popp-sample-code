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

import de.gematik.ws.conn.vsds.vsdservice.v5.*;
import de.servicehealth.refpopp.vsdm_client.converter.xml.*;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.soap.*;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VsdmSoapBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(VsdmSoapBuilder.class);

  private static final String VSD_STATUS_OK = "0";
  private static final String CDM_VERSION = "5.2.0";
  private static final String DEFAULT_VERSICHERTENART = "1";
  private static final String ENCODING_ISO_8859_15 = "ISO-8859-15";
  private static final String INDENT_YES = "yes";
  private static final String INDENT_AMOUNT = "2";

  private final JAXBContext readVsdResponseContext;
  private final Map<Class<?>, JAXBContext> innerContexts = new ConcurrentHashMap<>();

  public VsdmSoapBuilder() {
    try {
      this.readVsdResponseContext = JAXBContext.newInstance(ReadVSDResponse.class);
    } catch (JAXBException e) {
      throw new RuntimeException("Failed to initialize JAXBContext", e);
    }
  }

  public ReadVSDResponse createResponse(VsdmBundle bundle, String poppToken) {
    LOG.debug("Creating ReadVSDResponse for KVNR: {}", bundle.getKvnr());
    try {
      ObjectFactory factory = new ObjectFactory();
      ReadVSDResponse response = factory.createReadVSDResponse();

      response.setPersoenlicheVersichertendaten(compress(generatePersonalDataXml(bundle)));
      response.setAllgemeineVersicherungsdaten(
          compress(generateAllgemeineVersicherungsdatenXml(bundle)));
      response.setGeschuetzteVersichertendaten(
          compress(generateGeschuetzteVersichertendatenXml(bundle)));

      VSDStatusType status = factory.createVSDStatusType();
      status.setStatus(VSD_STATUS_OK);
      status.setVersion(CDM_VERSION);

      GregorianCalendar c = new GregorianCalendar();
      c.setTime(new Date());
      XMLGregorianCalendar date = DatatypeFactory.newInstance().newXMLGregorianCalendar(c);
      status.setTimestamp(date);

      response.setVSDStatus(status);

      response.setPruefungsnachweis(compress(generatePruefungsnachweisXml(poppToken)));

      return response;
    } catch (Exception e) {
      LOG.error("Error creating ReadVSDResponse", e);
      throw new VsdmProcessingException("Failed to create SOAP response object", e);
    }
  }

  public String marshallToSoapString(ReadVSDResponse response) {
    try {
      MessageFactory mf = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
      SOAPMessage soapMessage = mf.createMessage();
      SOAPBody soapBody = soapMessage.getSOAPBody();

      Marshaller m = readVsdResponseContext.createMarshaller();
      m.marshal(response, soapBody);

      soapMessage.saveChanges();

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      Transformer transformer = transformerFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, INDENT_YES);
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", INDENT_AMOUNT);

      DOMSource source = new DOMSource(soapMessage.getSOAPPart());
      StreamResult result = new StreamResult(out);
      transformer.transform(source, result);

      return out.toString("UTF-8");
    } catch (Exception e) {
      LOG.error("Error marshalling SOAP message", e);
      throw new VsdmProcessingException("Failed to marshal SOAP message", e);
    }
  }

  private String generatePersonalDataXml(VsdmBundle b) throws Exception {
    PersoenlicheVersichertendatenXML xml = new PersoenlicheVersichertendatenXML();
    PersoenlicheVersichertendatenXML.Versicherter versicherter =
        new PersoenlicheVersichertendatenXML.Versicherter();
    versicherter.setVersichertenId(b.getKvnr());

    PersoenlicheVersichertendatenXML.Person person = new PersoenlicheVersichertendatenXML.Person();
    person.setGeburtsdatum(b.getGeburtsdatum());
    person.setVorname(b.getVorname());
    person.setNachname(b.getNachname());
    person.setGeschlecht(b.getGeschlecht());
    person.setNamenszusatz(b.getNamenszusatz());

    PersoenlicheVersichertendatenXML.StrassenAdresse adresse =
        new PersoenlicheVersichertendatenXML.StrassenAdresse();
    adresse.setPostleitzahl(b.getPlz());
    adresse.setOrt(b.getOrt());
    adresse.setStrasse(b.getStrasse());
    adresse.setHausnummer(b.getHausnummer());

    PersoenlicheVersichertendatenXML.Land land = new PersoenlicheVersichertendatenXML.Land();
    land.setWohnsitzlaendercode(b.getWohnsitzlaendercode());

    adresse.setLand(land);
    person.setStrassenAdresse(adresse);
    versicherter.setPerson(person);
    xml.setVersicherter(versicherter);

    return marshalInnerXml(xml, PersoenlicheVersichertendatenXML.class);
  }

  private String generateAllgemeineVersicherungsdatenXml(VsdmBundle b) throws Exception {
    AllgemeineVersicherungsdatenXML xml = new AllgemeineVersicherungsdatenXML();
    AllgemeineVersicherungsdatenXML.Versicherter versicherter =
        new AllgemeineVersicherungsdatenXML.Versicherter();

    AllgemeineVersicherungsdatenXML.Versicherungsschutz schutz =
        new AllgemeineVersicherungsdatenXML.Versicherungsschutz();
    schutz.setBeginn(b.getVersicherungsschutzBeginn());
    schutz.setEnde(b.getVersicherungsschutzEnde());

    AllgemeineVersicherungsdatenXML.Kostentraeger kostentraeger =
        new AllgemeineVersicherungsdatenXML.Kostentraeger();
    kostentraeger.setKostentraegerkennung(b.getKostentraegerKennung());
    kostentraeger.setName(b.getKostentraegerName());

    AllgemeineVersicherungsdatenXML.Zusatzinfos zusatzinfos =
        new AllgemeineVersicherungsdatenXML.Zusatzinfos();
    AllgemeineVersicherungsdatenXML.ZusatzinfosGKV gkv =
        new AllgemeineVersicherungsdatenXML.ZusatzinfosGKV();
    gkv.setVersichertenart(DEFAULT_VERSICHERTENART);

    AllgemeineVersicherungsdatenXML.ZusatzinfosAbrechnungGKV abrechnung =
        new AllgemeineVersicherungsdatenXML.ZusatzinfosAbrechnungGKV();
    abrechnung.setVersichertenstatus(b.getVersichertenStatus());
    abrechnung.setWop(b.getWop());
    gkv.setZusatzinfosAbrechnungGKV(abrechnung);

    schutz.setKostentraeger(kostentraeger);
    zusatzinfos.setZusatzinfosGKV(gkv);
    versicherter.setVersicherungsschutz(schutz);
    versicherter.setZusatzinfos(zusatzinfos);
    xml.setVersicherter(versicherter);

    return marshalInnerXml(xml, AllgemeineVersicherungsdatenXML.class);
  }

  private String generateGeschuetzteVersichertendatenXml(VsdmBundle b) throws Exception {
    GeschuetzteVersichertendatenXML xml = new GeschuetzteVersichertendatenXML();
    GeschuetzteVersichertendatenXML.Versicherter versicherter =
        new GeschuetzteVersichertendatenXML.Versicherter();

    GeschuetzteVersichertendatenXML.Zuzahlungsstatus zuzahlung =
        new GeschuetzteVersichertendatenXML.Zuzahlungsstatus();
    zuzahlung.setStatus(b.getZuzahlungsstatus());

    versicherter.setZuzahlungsstatus(zuzahlung);
    versicherter.setBesonderePersonengruppe(b.getBesonderePersonengruppe());
    versicherter.setDmpKennzeichnung(b.getDmpKennzeichnung());
    xml.setVersicherter(versicherter);

    return marshalInnerXml(xml, GeschuetzteVersichertendatenXML.class);
  }

  private String generatePruefungsnachweisXml(String token) throws Exception {
    PruefungsnachweisXML xml = new PruefungsnachweisXML();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
    xml.setTs(sdf.format(new Date()));
    xml.setE("0");
    xml.setPz(token);
    return marshalInnerXml(xml, PruefungsnachweisXML.class);
  }

  private <T> String marshalInnerXml(T object, Class<T> clazz) throws Exception {
    JAXBContext context =
        innerContexts.computeIfAbsent(
            clazz,
            c -> {
              try {
                return JAXBContext.newInstance(c);
              } catch (JAXBException e) {
                throw new RuntimeException(e);
              }
            });
    Marshaller m = context.createMarshaller();
    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    m.setProperty(Marshaller.JAXB_ENCODING, ENCODING_ISO_8859_15);
    StringWriter sw = new StringWriter();
    m.marshal(object, sw);
    return sw.toString();
  }

  private byte[] compress(String data) throws Exception {
    if (data == null) return new byte[0];
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
      gzip.write(data.getBytes(ENCODING_ISO_8859_15));
    }
    return baos.toByteArray();
  }
}
