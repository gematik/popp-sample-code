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

import de.gematik.refpopp.popp_client.controller.dto.PoppTokenValidationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {PoppTokenVerificationController.class})
public class PoppTokenValidationExceptionHandler {

  @ExceptionHandler(PoppTokenValidationException.class)
  public ResponseEntity<PoppTokenValidationResponse> handlePoppTokenValidationException(
      PoppTokenValidationException e) {

    return ResponseEntity.status(e.getErrorCode().getHttpStatus())
        .body(new PoppTokenValidationResponse(false, null, null, null, e.getErrorCode().name()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<PoppTokenValidationResponse> handleException(Exception e) {

    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
        .body(
            new PoppTokenValidationResponse(
                false, null, null, null, ErrorCode.INTERNAL_ERROR.name()));
  }
}
