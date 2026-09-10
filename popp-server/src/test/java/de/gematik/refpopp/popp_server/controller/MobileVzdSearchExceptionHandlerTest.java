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
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import de.gematik.refpopp.popp_server.vzd.VzdSearchException;
import org.junit.jupiter.api.Test;

class MobileVzdSearchExceptionHandlerTest {

  private final MobileVzdSearchExceptionHandler handler = new MobileVzdSearchExceptionHandler();

  @Test
  void handleVzdSearchExceptionReturnsExpectedErrorResponse() {
    // given
    var exception = new VzdSearchException("search backend unavailable");

    // when
    var response = handler.handleVzdSearchException(exception);

    // then
    assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_JSON);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("VZD_SEARCH_FAILED");
    assertThat(response.getBody().message()).isEqualTo("search backend unavailable");
  }

  @Test
  void handleVzdSearchExceptionPropagatesCauseMessage() {
    // given
    var exception =
        new VzdSearchException("wrapped failure", new RuntimeException("underlying cause"));

    // when
    var response = handler.handleVzdSearchException(exception);

    // then
    assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("VZD_SEARCH_FAILED");
    assertThat(response.getBody().message()).isEqualTo("wrapped failure");
  }

  @Test
  void handleIllegalArgumentReturnsExpectedErrorResponse() {
    // given
    var exception = new IllegalArgumentException("invalid query parameter");

    // when
    var response = handler.handleIllegalArgument(exception);

    // then
    assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_JSON);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("MALFORMED_REQUEST");
    assertThat(response.getBody().message()).isEqualTo("invalid query parameter");
  }

  @Test
  void handleGenericReturnsExpectedErrorResponse() {
    // given
    var exception = new Exception("something went wrong");

    // when
    var response = handler.handleGeneric(exception);

    // then
    assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_JSON);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.getBody().message()).isEqualTo("something went wrong");
  }

  @Test
  void handleGenericHandlesNullMessage() {
    // given
    var exception = new Exception();

    // when
    var response = handler.handleGeneric(exception);

    // then
    assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.getBody().message()).isNull();
  }
}
