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

package de.gematik.refpopp.popp_client.connector.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPMessage;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapBody;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

class SoapClientInterceptorTest {

  private SoapClientInterceptor sut;

  @BeforeEach
  void setUp() {
    sut = new SoapClientInterceptor();
  }

  @Test
  void handleRequestReturnsTrue() {
    // given
    final var messageContext = mock(MessageContext.class);

    // when
    final var actual = sut.handleRequest(messageContext);

    // then
    assertThat(actual).isTrue();
  }

  @Test
  void handleResponseReturnsTrue() {
    // given
    final var messageContext = mock(MessageContext.class);
    final var responseDocument = mock(SaajSoapMessage.class);
    when(messageContext.getResponse()).thenReturn(responseDocument);
    when(responseDocument.getDocument()).thenReturn(mock(Document.class));

    // when
    final var actual = sut.handleResponse(messageContext);

    // then
    assertThat(actual).isTrue();
  }

  @Test
  void handleResponseThrowsExceptionWhenSoapFaultMessage() throws Exception {
    // given
    final var soapFaultDocument = createSoapFaultDocument();
    final var messageContext = mock(MessageContext.class);
    final var responseDocument = mock(SaajSoapMessage.class);
    when(messageContext.getResponse()).thenReturn(responseDocument);
    when(responseDocument.getDocument()).thenReturn(soapFaultDocument);
    MessageFactory mf = MessageFactory.newInstance();
    SOAPMessage soapMessage = mf.createMessage();
    soapMessage.getSOAPPart().setContent(new DOMSource(soapFaultDocument));
    SaajSoapMessage springSaajMsg = new SaajSoapMessage(soapMessage);

    SoapBody soapBody = springSaajMsg.getSoapBody();
    when(responseDocument.getSoapBody()).thenReturn(soapBody);

    // when / then
    assertThatThrownBy(() -> sut.handleResponse(messageContext))
        .isInstanceOf(SoapFaultClientException.class);
  }

  @Test
  void handleFaultReturnsTrue() {
    // given
    final var messageContext = mock(MessageContext.class);

    // when
    final var actual = sut.handleFault(messageContext);

    // then
    assertThat(actual).isTrue();
  }

  @Test
  void afterCompletionReturnsNothing() {
    // given
    final var messageContext = mock(MessageContext.class);

    // when
    try {
      sut.afterCompletion(messageContext, null);
    } catch (final Exception e) {
      // then
      fail("Exception should not be thrown", e);
    }
  }

  @Test
  void handleResponseThrowsExceptionWhenXPathExpressionException() {
    // given
    final var messageContext = mock(MessageContext.class);
    final var responseDocument = mock(SaajSoapMessage.class);
    when(messageContext.getResponse()).thenReturn(responseDocument);
    try (final var xpathFactoryMock = mockStatic(XPathFactory.class)) {
      final var xpathFactory = mock(XPathFactory.class);
      xpathFactoryMock.when(XPathFactory::newInstance).thenReturn(xpathFactory);
      final var xpath = mock(javax.xml.xpath.XPath.class);
      when(xpathFactory.newXPath()).thenReturn(xpath);
      when(xpath.compile(any())).thenThrow(new XPathExpressionException("Test exception"));
      when(responseDocument.getDocument()).thenReturn(mock(Document.class));

      // when / then
      assertThatThrownBy(() -> sut.handleResponse(messageContext))
          .isInstanceOf(IllegalStateException.class)
          .hasCauseInstanceOf(XPathExpressionException.class);
    } catch (final Exception e) {
      fail("Exception should not be thrown", e);
    }
  }

  private Document createSoapFaultDocument() throws Exception {
    final String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "  <soap:Body>"
            + "    <soap:Fault>"
            + "      <faultcode>soap:Server</faultcode>"
            + "      <faultstring>Ein unerwarteter Fehler ist aufgetreten.</faultstring>"
            + "    </soap:Fault>"
            + "  </soap:Body>"
            + "</soap:Envelope>";

    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    final DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(new InputSource(new StringReader(xml)));
  }
}
