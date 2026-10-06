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

package de.gematik.refpopp.popp_client.client.session;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.refpopp.popp_client.connector.ConnectorCommunicationServiceWrapper;
import java.util.Map;

/**
 * Provides typed access to communication-specific attributes stored in an SSL session.
 *
 * <p>The wrapper manages the card connection type, client session identifier, and flags for
 * virtual-card and connector-mock flows.
 */
public class CommunicationSslSession {

  private static final String VIRTUAL_CARD = "virtualCard";
  private static final String CARD_CONNECTION_TYPE = "cardConnectionType";
  private static final String CLIENT_SESSION_ID = "clientSessionId";

  private final Map<String, Object> attributes;

  public CommunicationSslSession(final Map<String, Object> attributes) {
    this.attributes = attributes;
  }

  public CardConnectionType getCardConnectionType() {
    return (CardConnectionType) attributes.get(CARD_CONNECTION_TYPE);
  }

  public String getClientSessionId() {
    return (String) attributes.get(CLIENT_SESSION_ID);
  }

  public boolean isConnectorMock() {
    return Boolean.TRUE.equals(attributes.get(ConnectorCommunicationServiceWrapper.CONNECTOR_MOCK));
  }

  public boolean isVirtualCard() {
    return Boolean.TRUE.equals(attributes.get(VIRTUAL_CARD));
  }

  public void setCardConnectionType(final CardConnectionType cardConnectionType) {
    attributes.put(CARD_CONNECTION_TYPE, cardConnectionType);
  }

  public void setClientSessionId(final String clientSessionId) {
    attributes.put(CLIENT_SESSION_ID, clientSessionId);
  }

  public void setConnectorMock(final boolean connectorMock) {
    attributes.put(ConnectorCommunicationServiceWrapper.CONNECTOR_MOCK, connectorMock);
  }

  public void setVirtualCard(final boolean virtualCard) {
    attributes.put(VIRTUAL_CARD, virtualCard);
  }
}
