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

package de.servicehealth.refpopp.vsdm_client.service;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSD;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.servicehealth.refpopp.vsdm_client.converter.VsdmConverter;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.RestTemplate;

@Service
public class VsdService {

  private static final Logger logger = LoggerFactory.getLogger(VsdService.class);

  private final VsdmConverter vsdmConverter;
  private final RestTemplate restTemplate;

  @Value("${popp.client.api.url:http://localhost:8081/token}")
  private String poppClientApiUrl;

  @Value("${popp.client.communication-type:contact-virtual}")
  private String communicationType;

  @Value("${popp.client.session-id:123456}")
  private String clientSessionId;

  public VsdService(VsdmConverter vsdmConverter) {
    this.vsdmConverter = vsdmConverter;

    // Set timeouts using SimpleClientHttpRequestFactory instead of RestTemplateBuilder
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000); // 5000 ms = 5 seconds
    factory.setReadTimeout(10000); // 10000 ms = 10 seconds
    this.restTemplate = new RestTemplate(factory);
  }

  public ReadVSDResponse processReadVsd(ReadVSD request) {
    logger.info("Processing ReadVSD request in the service layer.");

    try {
      // Read example FHIR bundle file from the resources folder
      ClassPathResource resource =
          new ClassPathResource("fhir_bundle/019aa697-e026-7735-b898-09ead32a7fa5.xml");
      byte[] bundleData = FileCopyUtils.copyToByteArray(resource.getInputStream());
      String fhirBundle = new String(bundleData, StandardCharsets.UTF_8);

      // Retrieve the POPP token via the API (via POST)
      String poppToken = fetchPoppToken();

      // Call the conversion via the VSDM Converter
      return vsdmConverter.createReadVSDResponse(fhirBundle, poppToken);

    } catch (Exception e) {
      logger.error("Error reading or converting the FHIR bundle", e);
      ReadVSDResponse fallbackResponse = new ReadVSDResponse();
      return fallbackResponse;
    }
  }

  private String fetchPoppToken() {
    logger.info("Retrieving POPP token from API: {}", poppClientApiUrl);

    TokenRequest requestPayload = new TokenRequest(communicationType, clientSessionId);

    try {
      TokenResponse response =
          restTemplate.postForObject(poppClientApiUrl, requestPayload, TokenResponse.class);

      if (response != null && response.token() != null && !response.token().isBlank()) {
        return response.token();
      } else {
        logger.warn(
            "Unexpected response or empty token from POPP client. Response was: {}", response);
        return "";
      }
    } catch (Exception e) {
      logger.error("HTTP request to POPP client failed: {}", e.getMessage());
      return "";
    }
  }

  // Typensichere Datenstrukturen (DTOs) für die REST-Kommunikation
  private record TokenRequest(String communicationType, String clientSessionId) {}

  private record TokenResponse(String token, String error) {}
}
