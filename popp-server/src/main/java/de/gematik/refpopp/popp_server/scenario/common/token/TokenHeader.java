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

package de.gematik.refpopp.popp_server.scenario.common.token;

import de.gematik.poppcommons.api.exceptions.CertificateParserException;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
final class TokenHeader {

  @Value("${jwt-token.popp.typ:vnd.telematik.popp+jwt}")
  private String poppTokenType;

  @Value("${jwt-token.connector.typ:JWT}")
  private String connectorTokenType;

  @Value("${certificates.ocsp-response:}")
  private String ocspResponsePath;

  private static final ThreadLocal<MessageDigest> THREAD_LOCAL_DIGEST =
      ThreadLocal.withInitial(
          () -> {
            try {
              return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
              throw new IllegalStateException("SHA-256 algorithm not available", e);
            }
          });

  private static final Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  Map<String, Object> createHeader(
      final X509Certificate signerCertificate,
      final ECPublicKey publicKey,
      final String sessionId,
      final TokenType tokenType) {
    final Map<String, Object> headers;

    if (tokenType == TokenType.POPP) {
      headers = createHeadersForPoppToken(publicKey);
    } else {
      headers = createHeadersForConnectorToken(signerCertificate, sessionId);
    }
    return headers;
  }

  private Map<String, Object> createHeadersForConnectorToken(
      final X509Certificate signerCertificate, final String sessionId) {
    final var headers = new HashMap<String, Object>();
    headers.put(Header.TYP.value, connectorTokenType);
    final String x5c;
    try {
      x5c = Base64.getEncoder().encodeToString(signerCertificate.getEncoded());
    } catch (final CertificateEncodingException e) {
      throw new CertificateParserException(sessionId, "Could not encode certificate", "errorCode");
    }
    headers.put(Header.X5C.value, x5c);
    final var ocspResponse = readOcspResponse(sessionId);
    headers.put(Header.STPL.value, ocspResponse);

    return headers;
  }

  private Map<String, Object> createHeadersForPoppToken(final ECPublicKey publicKey) {
    final var headers = new HashMap<String, Object>();
    headers.put(Header.TYP.value, poppTokenType);
    headers.put(Header.KID.value, getKeyId(publicKey));

    return headers;
  }

  private String readOcspResponse(final String sessionId) {
    final var response = new ClassPathResource(ocspResponsePath);
    try (final var inputStream = response.getInputStream()) {
      return Base64.getEncoder().encodeToString(inputStream.readAllBytes());
    } catch (final Exception e) {
      throw new ScenarioException(sessionId, "Could not read OCSP response", "errorCode");
    }
  }

  private String getKeyId(final ECPublicKey publicKey) {
    final var messageDigest = THREAD_LOCAL_DIGEST.get();
    try {
      final byte[] x = publicKey.getW().getAffineX().toByteArray();
      final byte[] y = publicKey.getW().getAffineY().toByteArray();
      final var jwkBytes =
          ("{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\""
                  + BASE64_URL_ENCODER.encodeToString(x)
                  + "\",\"y\":\""
                  + BASE64_URL_ENCODER.encodeToString(y)
                  + "\"}")
              .getBytes(StandardCharsets.UTF_8);
      final byte[] hash = messageDigest.digest(jwkBytes);
      return BASE64_URL_ENCODER.encodeToString(hash);
    } finally {
      messageDigest.reset();
      THREAD_LOCAL_DIGEST.remove();
    }
  }

  private enum Header {
    TYP("typ"),
    KID("kid"),
    X5C("x5c"),
    STPL("stpl");

    private final String value;

    Header(final String value) {
      this.value = value;
    }
  }
}
