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

package de.gematik.refpopp.popp_client.configuration.helper;

import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.refpopp.popp_client.connector.soap.SoapActions;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SoapActionVersionHelper {

  public static String buildSoapAction(
      ServiceEndpointProvider endpointProvider, SoapActions soapAction) {
    String version = getVersionFromServiceEndpoint(endpointProvider);
    return soapAction.getServiceEndpoint() + version + soapAction.getCommand();
  }

  private static String getVersionFromServiceEndpoint(ServiceEndpointProvider endpointProvider) {
    String version = endpointProvider.getCardServiceEndpoint().getVersion();
    if (version == null || version.isBlank()) {
      throw new IllegalStateException(
          "Version is missing for " + endpointProvider.getCardServiceEndpoint().getEndpoint());
    }
    String[] parts = version.split("\\.");
    if (parts.length < 2) {
      throw new IllegalArgumentException(
          "Unsupported version format (need at least x.y or x.y.z), but got: " + version);
    }
    return parts[0] + "." + parts[1];
  }
}
