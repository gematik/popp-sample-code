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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.refpopp.popp_server.federation.FederationEntityStatementCreationException;
import de.gematik.refpopp.popp_server.federation.FederationEntityStatementService;
import de.gematik.refpopp.popp_server.federation.SignedJwksCreationException;
import de.gematik.refpopp.popp_server.federation.SignedJwksService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WellKnownController.class)
class WellKnownControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FederationEntityStatementService federationEntityStatementService;

  @MockitoBean private SignedJwksService signedJwksService;

  @Test
  void openIdFederationReturnsJwtWithCorrectContentType() throws Exception {
    var expectedJwt = "header.payload.signature";
    when(federationEntityStatementService.create(anyString())).thenReturn(expectedJwt);

    mockMvc
        .perform(get("/.well-known/openid-federation").accept("application/entity-statement+jwt"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/entity-statement+jwt;charset=UTF-8"))
        .andExpect(content().string(expectedJwt));

    verify(federationEntityStatementService).create(anyString());
  }

  @Test
  void openIdFederationServiceReturnsNullReturns200WithNullBody() throws Exception {
    when(federationEntityStatementService.create(anyString())).thenReturn(null);

    mockMvc
        .perform(get("/.well-known/openid-federation").accept("application/entity-statement+jwt"))
        .andExpect(status().isOk());
  }

  @Test
  void signedJwksReturnsSignedJwksWithCorrectContentType() throws Exception {
    var expectedSignedJwks = "header.payload.signature";
    when(signedJwksService.create()).thenReturn(expectedSignedJwks);

    mockMvc
        .perform(get("/.well-known/signed-jwks"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/jwk-set+jwt;charset=UTF-8"))
        .andExpect(content().string(expectedSignedJwks));

    verify(signedJwksService).create();
  }

  @Test
  void signedJwksServiceReturnsNullReturns200WithNullBody() throws Exception {
    when(signedJwksService.create()).thenReturn(null);

    mockMvc.perform(get("/.well-known/signed-jwks")).andExpect(status().isOk());
  }

  @Test
  void openIdFederationWrongAcceptHeaderreturns406() throws Exception {
    mockMvc
        .perform(get("/.well-known/openid-federation").accept(APPLICATION_JSON))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void openIdFederationPostRequestReturns405() throws Exception {
    mockMvc
        .perform(post("/.well-known/openid-federation"))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void signedJwksPostRequestReturns405() throws Exception {
    mockMvc.perform(post("/.well-known/signed-jwks")).andExpect(status().isMethodNotAllowed());
  }

  @Test
  void openIdFederationWhenServiceFailsReturns500WithErrorResponse() throws Exception {
    // given
    given(federationEntityStatementService.create(anyString()))
        .willThrow(new FederationEntityStatementCreationException("boom", new RuntimeException()));

    // when // then
    mockMvc
        .perform(get("/.well-known/openid-federation"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "code": "FEDERATION_ENTITY_STATEMENT_CREATION_FAILED",
                      "message": "Failed to create federation entity statement"
                    }
                    """));
  }

  @Test
  void signedJwksWhenServiceFailsReturns500WithErrorResponse() throws Exception {
    // given
    given(signedJwksService.create())
        .willThrow(new SignedJwksCreationException("boom", new RuntimeException()));

    // when // then
    mockMvc
        .perform(get("/.well-known/signed-jwks"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "code": "SIGNED_JWKS_CREATION_FAILED",
                      "message": "Failed to create signed JWKS"
                    }
                    """));
  }
}
