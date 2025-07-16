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

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Temporary workaround to fix wrong http status codes received by Rise Konnektors. */
public class SoapClientInterceptor implements ClientInterceptor {

  @Override
  public boolean handleRequest(final MessageContext messageContext)
      throws WebServiceClientException {
    return true;
  }

  @Override
  public boolean handleResponse(final MessageContext messageContext)
      throws WebServiceClientException {
    final Document responseDocument =
        ((SaajSoapMessage) messageContext.getResponse()).getDocument();

    final boolean isSoapFaultMessage;
    try {
      isSoapFaultMessage = checkForSoapFault(responseDocument);
    } catch (final XPathExpressionException e) {
      throw new IllegalStateException(e);
    }

    if (isSoapFaultMessage) {
      throw new SoapFaultClientException((SaajSoapMessage) messageContext.getResponse());
    }
    return true;
  }

  @Override
  public boolean handleFault(final MessageContext messageContext) throws WebServiceClientException {
    return true;
  }

  @Override
  public void afterCompletion(final MessageContext messageContext, final Exception e)
      throws WebServiceClientException { // Is needed to implement Interface
  }

  private boolean checkForSoapFault(final Document document) throws XPathExpressionException {
    final var xpathFactory = XPathFactory.newInstance();
    final var xpath = xpathFactory.newXPath();
    final var expression = xpath.compile("//*[local-name()='Fault']");
    final var faultNode = (Node) expression.evaluate(document, XPathConstants.NODE);
    return faultNode != null;
  }
}
