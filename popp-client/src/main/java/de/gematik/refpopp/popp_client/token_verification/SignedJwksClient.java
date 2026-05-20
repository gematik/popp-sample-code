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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class SignedJwksClient {

  private final RestClient restClient;

  public SignedJwksClient(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  public String fetchSignedJwks(String signedJwksUri) {
    try {
      return restClient
          .get()
          .uri(signedJwksUri)
          .accept(
              MediaType.parseMediaType("application/jwk-set+jwt"),
              MediaType.APPLICATION_JSON,
              MediaType.TEXT_PLAIN,
              MediaType.ALL)
          .retrieve()
          .body(String.class);

    } catch (RestClientResponseException ex) {
      throw new PoppTokenValidationException(
          ErrorCode.JWKS_FETCH_FAILED,
          "Signed JWKS could not be fetched. HTTP status: " + ex.getStatusCode(),
          ex);

    } catch (RestClientException ex) {
      throw new PoppTokenValidationException(
          ErrorCode.JWKS_FETCH_FAILED, "Signed JWKS endpoint is not reachable", ex);
    }
  }
}
