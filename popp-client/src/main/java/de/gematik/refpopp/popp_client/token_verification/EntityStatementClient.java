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

package de.gematik.refpopp.popp_client.token_verification;

import de.gematik.refpopp.popp_client.controller.ErrorCode;
import de.gematik.refpopp.popp_client.controller.PoppTokenValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class EntityStatementClient {

  private final RestClient restClient;
  private final String entityStatementUrl;

  public EntityStatementClient(
      RestClient.Builder restClientBuilder,
      @Value("${federation.entity-statement.url}") String entityStatementUrl) {
    this.restClient = restClientBuilder.build();
    this.entityStatementUrl = entityStatementUrl;
  }

  public String fetchEntityStatementJwt() {
    try {
      return restClient
          .get()
          .uri(entityStatementUrl)
          .accept(
              MediaType.parseMediaType("application/entity-statement+jwt"), MediaType.TEXT_PLAIN)
          .retrieve()
          .body(String.class);

    } catch (RestClientResponseException ex) {
      throw new PoppTokenValidationException(
          ErrorCode.ENTITY_STATEMENT_FETCH_FAILED,
          "Entity Statement could not be fetched. HTTP status: " + ex.getStatusCode(),
          ex);

    } catch (RestClientException ex) {
      throw new PoppTokenValidationException(
          ErrorCode.ENTITY_STATEMENT_FETCH_FAILED,
          "Entity Statement endpoint is not reachable",
          ex);
    }
  }
}
