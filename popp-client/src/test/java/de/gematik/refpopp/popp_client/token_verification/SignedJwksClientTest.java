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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_client.controller.ErrorCode;
import de.gematik.refpopp.popp_client.controller.PoppTokenValidationException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@DisplayName("SignedJwksClient Tests")
class SignedJwksClientTest {

  private static final String SIGNED_JWKS_URI = "https://example.com/signed-jwks";
  private static final String SIGNED_JWKS_JWT = "header.payload.signature";

  @Mock private RestClient.Builder restClientBuilderMock;
  @Mock private RestClient restClientMock;
  @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpecMock;
  @Mock private RestClient.RequestHeadersSpec requestHeadersSpecMock;
  @Mock private RestClient.ResponseSpec responseSpecMock;

  private AutoCloseable closeable;
  private SignedJwksClient client;

  @BeforeEach
  void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
    when(restClientBuilderMock.build()).thenReturn(restClientMock);
    when(restClientMock.get()).thenReturn(requestHeadersUriSpecMock);
    when(requestHeadersUriSpecMock.uri(SIGNED_JWKS_URI)).thenReturn(requestHeadersSpecMock);
    doReturn(requestHeadersSpecMock)
        .when(requestHeadersSpecMock)
        .accept(any(org.springframework.http.MediaType[].class));
    when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);
    client = new SignedJwksClient(restClientBuilderMock);
  }

  @AfterEach
  void tearDown() throws Exception {
    closeable.close();
  }

  @Test
  @DisplayName("Should successfully fetch signed JWKS")
  void fetchSignedJwksReturnsJwtWhenResponseIsValid() {
    when(responseSpecMock.body(String.class)).thenReturn(SIGNED_JWKS_JWT);

    String result = client.fetchSignedJwks(SIGNED_JWKS_URI);

    assertEquals(SIGNED_JWKS_JWT, result);
    verify(restClientMock).get();
    verify(requestHeadersUriSpecMock).uri(SIGNED_JWKS_URI);
    verify(requestHeadersSpecMock).retrieve();
    verify(responseSpecMock).body(String.class);
  }

  @Test
  @DisplayName("Should return null when endpoint responds with null body")
  void fetchSignedJwksReturnsNullWhenResponseBodyIsNull() {
    when(responseSpecMock.body(String.class)).thenReturn(null);

    String result = client.fetchSignedJwks(SIGNED_JWKS_URI);

    assertNull(result);
  }

  @Test
  @DisplayName("Should throw PoppTokenValidationException when HTTP 404 is returned")
  void fetchSignedJwksThrowsExceptionOnHttpNotFound() {
    RestClientResponseException responseException =
        new RestClientResponseException(
            "Not Found",
            404,
            "Not Found",
            null,
            "{}".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);
    when(responseSpecMock.body(String.class)).thenThrow(responseException);

    PoppTokenValidationException exception =
        assertThrows(
            PoppTokenValidationException.class, () -> client.fetchSignedJwks(SIGNED_JWKS_URI));
    assertEquals(ErrorCode.JWKS_FETCH_FAILED, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("HTTP status"));
    assertEquals(responseException, exception.getCause());
  }

  @Test
  @DisplayName("Should throw PoppTokenValidationException when HTTP 500 is returned")
  void fetchSignedJwksThrowsExceptionOnHttpInternalServerError() {
    RestClientResponseException responseException =
        new RestClientResponseException(
            "Internal Server Error",
            500,
            "Internal Server Error",
            null,
            "{}".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);
    when(responseSpecMock.body(String.class)).thenThrow(responseException);

    PoppTokenValidationException exception =
        assertThrows(
            PoppTokenValidationException.class, () -> client.fetchSignedJwks(SIGNED_JWKS_URI));
    assertEquals(ErrorCode.JWKS_FETCH_FAILED, exception.getErrorCode());
    assertEquals(responseException, exception.getCause());
  }

  @Test
  @DisplayName("Should throw PoppTokenValidationException when endpoint is not reachable")
  void fetchSignedJwksThrowsExceptionWhenEndpointIsNotReachable() {
    RestClientException clientException = new RestClientException("Connection refused");
    when(responseSpecMock.body(String.class)).thenThrow(clientException);

    PoppTokenValidationException exception =
        assertThrows(
            PoppTokenValidationException.class, () -> client.fetchSignedJwks(SIGNED_JWKS_URI));
    assertEquals(ErrorCode.JWKS_FETCH_FAILED, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("not reachable"));
    assertEquals(clientException, exception.getCause());
  }

  @Test
  @DisplayName("Should throw PoppTokenValidationException when network timeout occurs")
  void fetchSignedJwksThrowsExceptionOnNetworkTimeout() {
    RestClientException timeoutException = new RestClientException("Connection timeout");
    when(responseSpecMock.body(String.class)).thenThrow(timeoutException);

    PoppTokenValidationException exception =
        assertThrows(
            PoppTokenValidationException.class, () -> client.fetchSignedJwks(SIGNED_JWKS_URI));
    assertEquals(ErrorCode.JWKS_FETCH_FAILED, exception.getErrorCode());
    assertEquals(timeoutException, exception.getCause());
  }
}
