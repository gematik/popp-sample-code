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

package de.gematik.refpopp.popp_server.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import de.gematik.refpopp.popp_server.federation.FederationEntityStatementCreationException;
import de.gematik.refpopp.popp_server.federation.SignedJwksCreationException;
import org.junit.jupiter.api.Test;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void handleFederationEntityStatementCreationReturnsExpectedErrorResponse() {
    var exception =
        new FederationEntityStatementCreationException("boom", new RuntimeException("cause"));

    var response = handler.handleFederationEntityStatementCreation(exception);

    assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_JSON);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("FEDERATION_ENTITY_STATEMENT_CREATION_FAILED");
    assertThat(response.getBody().message())
        .isEqualTo("Failed to create federation entity statement");
  }

  @Test
  void handleSignedJwksCreationReturnsExpectedErrorResponse() {
    var exception = new SignedJwksCreationException("boom", new RuntimeException("cause"));

    var response = handler.handleSignedJwksCreation(exception);

    assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_JSON);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("SIGNED_JWKS_CREATION_FAILED");
    assertThat(response.getBody().message()).isEqualTo("Failed to create signed JWKS");
  }

  @Test
  void handleIllegalArgumentReturnsExpectedErrorResponse() {
    var exception = new IllegalArgumentException("invalid input");

    var response = handler.handleIllegalArgument(exception);

    assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_JSON);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.getBody().message()).isEqualTo("invalid input");
  }
}
