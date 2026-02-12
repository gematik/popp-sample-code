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

package de.gematik.refpopp.popp_client.cardreader.card;

import de.gematik.poppcommons.api.messages.ScenarioStep;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Service
@Setter
@Slf4j
public class VirtualCardService {

  public static final String APDU_RESPONSE_OK = "9000";

  public static final String APDU_RESPONSE_READ_VERSION =
      "ef2bc003020000c103040502c210444549444d4548435f39303030030005c403010000c503020000c703010000";
  public static final String APDU_RESPONSE_READ_SUB_CA_CV_CERTIFICATE =
      "7f2181d87f4e81915f290170420844454758588702227f494d06082a8648ce3d04030286410428405a0ccc5c53b6780356a5141eb47fed5f56be44bc22f2046fc053fedbc25e50e24a6d6af95c1cfee9497acce359a253f7d0b7abaea5d1a62de030145f0c975f200844454758581102237f4c1306082a8214004c0481185307800000000000005f25060203000703015f24060301000703005f37404cd260c0803b125a001ba81ba9f2e2b1390de4f14691c822a28cc776a186d7ba7f08704c27fdcdaeb1f8b243a37976cf37bf7c121858d0f0419de83217a395de";
  public static final String APDU_RESPONSE_RETRIEVE_PUBLIC_KEY_IDENTIFIERS =
      "e0154f07d2760001448000b60a83084445475858870222e0154f07d2760001448000b60a83084445475858120223e0194f07d2760001448000a40e830c000a80276001011699902101e0194f07d2760001448000a40e830c4d6f7270686f414343455353e0164f07d2760001448000b60b83094d6f7270686f564552e0154f07d2760001448000b60a83084445475858860220e0154f07d2760001448000b60a83080000000000000013";

  private String apduReadEndEntityCvCertificate;
  private String apduMutualAuthenticationStep1;
  private String apduReadEfCChAutE256;

  private final ApplicationEventPublisher eventPublisher;

  private HashMap<String, String> staticApduResponses = new HashMap<>();
  private String cvcCertificate = null;
  private String authCertificate = null;

  public VirtualCardService(
      ApplicationEventPublisher eventPublisher,
      @Value("${virtual-card.image-file}") String imageFile,
      @Value("${command-apdus.select-master-file}") String apduSelectMasterFile,
      @Value("${command-apdus.read-version}") String apduReadVersion,
      @Value("${command-apdus.read-sub-ca-cv-certificate}") String apduReadSubCaCvCertificate,
      @Value("${command-apdus.read-end-entity-cv-certificate}")
          String apduReadEndEntityCvCertificate,
      @Value("${command-apdus.retrieve-public-key-identifiers}")
          String apduRetrievePublicKeyIdentifiers,
      @Value("${command-apdus.select-private-key}") String apduSelectPrivateKey,
      @Value("${command-apdus.mutual-authentication-step-1}") String apduMutualAuthenticationStep1,
      @Value("${command-apdus.mutual-authentication-step-2}") String apduMutualAuthenticationStep2,
      @Value("${command-apdus.select-df-esign}") String apduSelectDfEsign,
      @Value("${command-apdus.read-ef-c-ch-aut-e256}") String apduReadEfCChAutE256) {
    this.eventPublisher = eventPublisher;
    log.debug("| Entering VirtualCardService()");

    if (imageFile == null || imageFile.isEmpty()) {
      log.info("| No image file configured for virtual card.");
    } else {
      log.info("| Loading certificates from " + imageFile);
      try {
        InputStream is = VirtualCardService.class.getClassLoader().getResourceAsStream(imageFile);
        String xmlString = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        cvcCertificate = getCertificateData(xmlString, "EF.C.eGK.AUT_CVC.E256");
        log.info("| CVC certificate:  " + cvcCertificate);
        authCertificate = getCertificateData(xmlString, "EF.C.CH.AUT.E256");
        log.info("| X.509 certificate:  " + authCertificate);
      } catch (IOException | SAXException | ParserConfigurationException e) {
        throw new RuntimeException("Error when loading XML card image file", e);
      }
    }

    this.apduReadEndEntityCvCertificate = apduReadEndEntityCvCertificate.replace(" ", "");
    this.apduMutualAuthenticationStep1 = apduMutualAuthenticationStep1.replace(" ", "");
    this.apduReadEfCChAutE256 = apduReadEfCChAutE256.replace(" ", "");

    staticApduResponses.put(apduSelectMasterFile.replace(" ", ""), "");
    staticApduResponses.put(apduReadVersion.replace(" ", ""), APDU_RESPONSE_READ_VERSION);
    staticApduResponses.put(
        apduReadSubCaCvCertificate.replace(" ", ""), APDU_RESPONSE_READ_SUB_CA_CV_CERTIFICATE);
    staticApduResponses.put(
        apduRetrievePublicKeyIdentifiers.replace(" ", ""),
        APDU_RESPONSE_RETRIEVE_PUBLIC_KEY_IDENTIFIERS);
    staticApduResponses.put(apduSelectPrivateKey.replace(" ", ""), "");
    staticApduResponses.put(apduMutualAuthenticationStep2.replace(" ", ""), "");
    staticApduResponses.put(apduSelectDfEsign.replace(" ", ""), "");

    log.debug("| Exiting VirtualCardService()");
  }

  public String getCvcCertificate() {
    return cvcCertificate;
  }

  public String getAuthCertificate() {
    return authCertificate;
  }

  public boolean isConfigured() {
    return cvcCertificate != null && authCertificate != null;
  }

  public List<String> process(final List<ScenarioStep> scenarioStep) {
    final var responses = new ArrayList<String>();
    for (final var step : scenarioStep) {
      responses.add(process(step));
    }

    return responses;
  }

  private String process(final ScenarioStep scenarioStep) {
    final var normalizedCommandApdu = normalize(scenarioStep.getCommandApdu());
    log.info("| APDU command: {}", normalizedCommandApdu);

    String responseAPDU;
    if (normalizedCommandApdu.equals(apduReadEndEntityCvCertificate)) { // READ CVC certificate
      responseAPDU = cvcCertificate;
    } else if (normalizedCommandApdu.equals(
        apduMutualAuthenticationStep1)) { // GENERAL_AUTHENTICATE

      // TODO Alfred: implement proper ephemeral key generation and response construction

      /*
              Der öffentliche Schlüssel ephemeralPK_self MUSS DER codiert wie folgt in die Antwortdaten eingestellt werden:
              responseData = ´	7C L7C [ 85 L85 P2OS(
                      ephemeralPK_self,
                      affectedObject_PrK.domainParameter.L
       			) ]´.
      */
      //      responseAPDU =
      // "7c4385410400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
      responseAPDU = "";
    } else if (normalizedCommandApdu.equals(apduReadEfCChAutE256)) { // READ X.509 certificate
      responseAPDU = authCertificate;
    } else {
      responseAPDU = staticApduResponses.get(normalizedCommandApdu);
    }
    responseAPDU = responseAPDU + APDU_RESPONSE_OK;
    log.info("| APDU response: {}", responseAPDU);
    return responseAPDU;
  }

  private String normalize(final String s) {
    return s.replaceAll("\\s+", "");
  }

  private Element getDOMRootElement(String xmlDoc)
      throws IOException, SAXException, ParserConfigurationException {
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    dbFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document doc = dBuilder.parse(new InputSource(new StringReader(xmlDoc)));
    doc.getDocumentElement().normalize();
    return doc.getDocumentElement();
  }

  private String getCertificateData(String xmlDoc, String certName)
      throws IOException, SAXException, ParserConfigurationException {
    Element e = getDOMRootElement(xmlDoc);
    NodeList nList = e.getElementsByTagName("child");
    for (int i = 0; i < nList.getLength(); i++) {
      Element e2 = (Element) nList.item(i);
      if (certName.equals(e2.getAttribute("id"))) {
        NodeList nList2 = e2.getElementsByTagName("attribute");

        for (int j = 0; j < nList2.getLength(); j++) {
          Element e3 = (Element) nList2.item(j);
          if ("body".equals(e3.getAttribute("id"))) {
            return e3.getTextContent();
          }
        }
      }
    }

    return null;
  }
}
