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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HexFormat;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Component
public final class VirtualCardImageLoader {
  private static final String EGK_AUT_CVC_PRIVATE_KEY_OBJECT = "PrK.eGK.AUT_CVC.E256";

  VirtualCardImageData load(final String imageFile)
      throws IOException, SAXException, ParserConfigurationException {
    try (var inputStream = openImageFile(imageFile)) {
      final var xmlString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      return new VirtualCardImageData(
          getCertificateData(xmlString, "EF.C.eGK.AUT_CVC.E256"),
          getCertificateData(xmlString, "EF.C.CH.AUT.E256"),
          getCertificateData(xmlString, "EF.C.CA_eGK.CS.E256"),
          getCertificateData(xmlString, "EF.Version2"),
          loadEgkAuthCvcPrivateKey(xmlString));
    }
  }

  private static InputStream openImageFile(final String imageFile) throws IOException {
    if (imageFile.startsWith("classpath:")) {
      final var cp = imageFile.substring("classpath:".length());
      final var inputStream = VirtualCardService.class.getClassLoader().getResourceAsStream(cp);
      if (inputStream == null) {
        throw new FileNotFoundException("Classpath resource not found: " + cp);
      }
      return inputStream;
    }
    final var path = Paths.get(imageFile);
    if (Files.exists(path)) {
      return Files.newInputStream(path);
    }
    final var inputStream =
        VirtualCardService.class.getClassLoader().getResourceAsStream(imageFile);
    if (inputStream == null) {
      throw new FileNotFoundException("File not found on filesystem or classpath: " + imageFile);
    }
    return inputStream;
  }

  private Element getDOMRootElement(final String xmlDoc)
      throws IOException, SAXException, ParserConfigurationException {

    final var dbFactory = DocumentBuilderFactory.newInstance();

    dbFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

    dbFactory.setXIncludeAware(false);
    dbFactory.setExpandEntityReferences(false);

    dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

    final var dBuilder = dbFactory.newDocumentBuilder();
    final var doc = dBuilder.parse(new InputSource(new StringReader(xmlDoc)));

    doc.getDocumentElement().normalize();
    return doc.getDocumentElement();
  }

  private String getCertificateData(final String xmlDoc, final String certName)
      throws IOException, SAXException, ParserConfigurationException {
    final var rootElement = getDOMRootElement(xmlDoc);
    final var children = rootElement.getElementsByTagName("child");
    for (int i = 0; i < children.getLength(); i++) {
      final var child = (Element) children.item(i);
      if (certName.equals(child.getAttribute("id"))) {
        final var attributes = child.getElementsByTagName("attribute");
        for (int j = 0; j < attributes.getLength(); j++) {
          final var attribute = (Element) attributes.item(j);
          if ("body".equals(attribute.getAttribute("id"))) {
            return attribute.getTextContent();
          }
        }
      }
    }
    return null;
  }

  private byte[] loadEgkAuthCvcPrivateKey(final String xmlDoc)
      throws IOException, SAXException, ParserConfigurationException {
    final var privateKeyHex = getChildAttribute(xmlDoc);
    if (privateKeyHex == null) {
      return new byte[0];
    }
    return VirtualCardPureHelper.toFixedLength(HexFormat.of().parseHex(privateKeyHex), 32);
  }

  private String getChildAttribute(final String xmlDoc)
      throws IOException, SAXException, ParserConfigurationException {
    final var rootElement = getDOMRootElement(xmlDoc);
    final var children = rootElement.getElementsByTagName("child");
    for (int i = 0; i < children.getLength(); i++) {
      final var child = (Element) children.item(i);
      if (!EGK_AUT_CVC_PRIVATE_KEY_OBJECT.equals(child.getAttribute("id"))) {
        continue;
      }
      final var attributes = child.getElementsByTagName("attribute");
      for (int j = 0; j < attributes.getLength(); j++) {
        final var attribute = (Element) attributes.item(j);
        if ("privateKey".equals(attribute.getAttribute("id"))) {
          return attribute.getTextContent();
        }
      }
    }
    return null;
  }
}
