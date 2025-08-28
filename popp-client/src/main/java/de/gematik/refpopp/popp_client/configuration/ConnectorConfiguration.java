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

package de.gematik.refpopp.popp_client.configuration;

import de.gematik.ws.conn.servicedirectory.ConnectorServices;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Configuration
@DependsOn("bouncyCastleConfiguration")
public class ConnectorConfiguration {

  @Bean
  public RestTemplate restTemplate(
      @Autowired(required = false) @Qualifier("httpClientWithBC") HttpClient httpClient) {
    log.info("[bcRestTemplate] sslContext present? {}", httpClient != null);
    if (httpClient == null) {
      log.info("[bcRestTemplate] No SSLContext. Returning DEFAULT RestTemplate.");
      return new RestTemplate();
    }

    HttpComponentsClientHttpRequestFactory requestFactory =
        new HttpComponentsClientHttpRequestFactory(httpClient);

    return new RestTemplate(requestFactory);
  }

  @Bean
  public JAXBContext jaxbContext() {
    try {
      return JAXBContext.newInstance(ConnectorServices.class);
    } catch (final JAXBException e) {
      throw new IllegalStateException("Failed to create JAXBContext", e);
    }
  }
}
