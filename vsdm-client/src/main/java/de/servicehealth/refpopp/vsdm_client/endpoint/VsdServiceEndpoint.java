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

package de.servicehealth.refpopp.vsdm_client.endpoint;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSD;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.servicehealth.refpopp.vsdm_client.service.VsdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
public class VsdServiceEndpoint {

  private static final Logger logger = LoggerFactory.getLogger(VsdServiceEndpoint.class);
  private static final String NAMESPACE_URI = "http://ws.gematik.de/conn/vsds/VSDService/v5.2";

  private final VsdService vsdService;

  // Dependency Injection via constructor
  public VsdServiceEndpoint(VsdService vsdService) {
    this.vsdService = vsdService;
  }

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "ReadVSD")
  @ResponsePayload
  public ReadVSDResponse readVsd(@RequestPayload ReadVSD request) {
    logger.info("SOAP request 'ReadVSD' received in endpoint. Delegating to service.");

    return vsdService.processReadVsd(request);
  }
}
