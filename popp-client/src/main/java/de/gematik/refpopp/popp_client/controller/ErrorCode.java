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

package de.gematik.refpopp.popp_client.controller;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
  TOKEN_MISSING(HttpStatus.BAD_REQUEST),
  TOKEN_MALFORMED(HttpStatus.BAD_REQUEST),

  SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED),
  ALGORITHM_INVALID(HttpStatus.UNAUTHORIZED),
  KID_NOT_FOUND(HttpStatus.UNAUTHORIZED),

  ISSUER_INVALID(HttpStatus.UNAUTHORIZED),
  ACTOR_MISMATCH(HttpStatus.FORBIDDEN),
  TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),

  JWKS_FETCH_FAILED(HttpStatus.SERVICE_UNAVAILABLE),
  ENTITY_STATEMENT_INVALID(HttpStatus.UNAUTHORIZED),

  OCSP_CHECK_FAILED(HttpStatus.SERVICE_UNAVAILABLE),
  CERTIFICATE_INVALID(HttpStatus.UNAUTHORIZED),

  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

  ENTITY_STATEMENT_FETCH_FAILED(HttpStatus.SERVICE_UNAVAILABLE),
  PUBLIC_KEY_UNSUPPORTED(HttpStatus.UNAUTHORIZED),
  ACTOR_ID_INVALID(HttpStatus.UNAUTHORIZED);

  private final HttpStatus httpStatus;

  ErrorCode(HttpStatus httpStatus) {
    this.httpStatus = httpStatus;
  }
}
