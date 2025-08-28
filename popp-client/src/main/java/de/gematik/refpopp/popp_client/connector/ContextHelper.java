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

package de.gematik.refpopp.popp_client.connector;

import de.gematik.ws.conn.connectorcontext.v2.ContextType;

/** Helper class to convert the Context object to a ContextType object for SOAP communication. */
public final class ContextHelper {

  private ContextHelper() {}

  public static ContextType convertToContextType(final Context context) {
    final ContextType contextType = new ContextType();
    contextType.setClientSystemId(context.getClientSystemId());
    contextType.setMandantId(context.getMandantId());
    contextType.setWorkplaceId(context.getWorkplaceId());
    final String user = context.getUserId();
    if (user != null && !user.isEmpty()) {
      contextType.setUserId(context.getUserId());
    }
    return contextType;
  }
}
