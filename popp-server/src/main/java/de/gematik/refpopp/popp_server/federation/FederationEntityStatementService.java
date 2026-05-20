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

package de.gematik.refpopp.popp_server.federation;

import de.gematik.refpopp.popp_server.security.jwk.JwkKidGenerator;
import java.math.BigInteger;
import java.net.URI;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FederationEntityStatementService {

  private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  private final JwkKidGenerator jwkKidGenerator;
  private final KeyStore keyStore;
  private final String keystorePassword;
  private final FederationProperties federationProperties;

  public FederationEntityStatementService(
      JwkKidGenerator jwkKidGenerator,
      FederationProperties federationProperties,
      @Qualifier("federationKeyStore") KeyStore keyStore,
      @Value("${certificates.federation-keystore-password}") String keystorePassword) {
    this.jwkKidGenerator = jwkKidGenerator;
    this.federationProperties = federationProperties;
    this.keyStore = keyStore;
    this.keystorePassword = keystorePassword;
  }

  public String create(String baseUrl) {
    try {
      var identifierUrl = normalizeBaseUrl(baseUrl);
      var entitySigningPublicKey =
          (ECPublicKey)
              keyStore.getCertificate(federationProperties.getEntitySigningAlias()).getPublicKey();
      var kid = jwkKidGenerator.generate(entitySigningPublicKey);
      var claims = getJwtClaims(entitySigningPublicKey, kid, identifierUrl);
      var privateKey =
          (PrivateKey)
              keyStore.getKey(
                  federationProperties.getEntitySigningAlias(), keystorePassword.toCharArray());
      var jws = getJsonWebSignature(claims, privateKey, kid);

      return jws.getCompactSerialization();
    } catch (KeyStoreException
        | UnrecoverableKeyException
        | NoSuchAlgorithmException
        | JoseException e) {
      throw new FederationEntityStatementCreationException(
          "Failed to create federation entity statement", e);
    }
  }

  private static @NonNull JsonWebSignature getJsonWebSignature(
      JwtClaims claims, PrivateKey privateKey, String kid) {
    var jws = new JsonWebSignature();
    jws.setPayload(claims.toJson());
    jws.setKey(privateKey);
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256);
    jws.setHeader("typ", "entity-statement+jwt");
    jws.setHeader("kid", kid);
    return jws;
  }

  private @NonNull JwtClaims getJwtClaims(
      ECPublicKey entitySigningPublicKey, String kid, String identifierUrl) {
    var claims = new JwtClaims();
    claims.setIssuer(federationProperties.getIssuer());
    claims.setSubject(federationProperties.getSubject());
    claims.setIssuedAtToNow();
    claims.setExpirationTimeMinutesInTheFuture(1440); // 24 h
    claims.setClaim("authority_hints", List.of(federationProperties.getMasterIssuer()));
    claims.setClaim(
        "metadata",
        Map.of(
            "federation_entity",
            Map.of("organization_name", federationProperties.getOrgName()),
            "oauth_resource",
            Map.of("signed_jwks_uri", signedJwksUri(identifierUrl))));
    claims.setClaim("jwks", Map.of("keys", List.of(toPublicEcJwk(entitySigningPublicKey, kid))));
    return claims;
  }

  private String signedJwksUri(String identifierUrl) {
    return URI.create(identifierUrl).resolve("/.well-known/signed-jwks").toString();
  }

  private Map<String, Object> toPublicEcJwk(ECPublicKey publicKey, String kid) {
    return Map.of(
        "kty", "EC",
        "crv", "P-256",
        "x", base64UrlUInt(publicKey.getW().getAffineX()),
        "y", base64UrlUInt(publicKey.getW().getAffineY()),
        "kid", kid,
        "alg", "ES256",
        "use", "sig");
  }

  private String base64UrlUInt(BigInteger value) {
    byte[] bytes = EcKeyCoordinateEncoder.toUnsignedFixedLength(value);
    return BASE64_URL_ENCODER.encodeToString(bytes);
  }

  private String normalizeBaseUrl(String url) {
    if (url.endsWith("/")) {
      return url.substring(0, url.length() - 1);
    }
    return url;
  }
}
