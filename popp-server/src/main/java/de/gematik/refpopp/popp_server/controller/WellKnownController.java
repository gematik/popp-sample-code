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

import de.gematik.refpopp.popp_server.federation.FederationEntityStatementService;
import de.gematik.refpopp.popp_server.federation.SignedJwksService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class WellKnownController {

  private final FederationEntityStatementService federationEntityStatementService;
  private final SignedJwksService signedJwksService;

  public WellKnownController(
      FederationEntityStatementService federationEntityStatementService,
      SignedJwksService signedJwksService) {
    this.federationEntityStatementService = federationEntityStatementService;
    this.signedJwksService = signedJwksService;
  }

  @GetMapping(
      value = "/.well-known/openid-federation",
      produces = "application/entity-statement+jwt")
  public ResponseEntity<String> openIdFederation() {
    var jwt = federationEntityStatementService.create();
    return ResponseEntity.ok(jwt);
  }

  @GetMapping(value = "/.well-known/signed-jwks", produces = "application/jwk-set+jwt")
  public ResponseEntity<String> signedJwks() {
    var jwt = signedJwksService.create();
    return ResponseEntity.ok(jwt);
  }
}
