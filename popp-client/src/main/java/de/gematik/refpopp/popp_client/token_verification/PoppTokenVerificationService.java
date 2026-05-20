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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import de.gematik.refpopp.popp_client.controller.ErrorCode;
import de.gematik.refpopp.popp_client.controller.PoppTokenValidationException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
public class PoppTokenVerificationService {

  private final EntityStatementClient entityStatementClient;

  private final EntityStatementParser entityStatementParser;

  private final SignedJwksClient signedJwksClient;

  private final KeyStore keyStore;

  public PoppTokenVerificationService(
      EntityStatementClient entityStatementClient,
      EntityStatementParser entityStatementParser,
      SignedJwksClient signedJwksClient,
      KeyStore keyStore) {
    this.entityStatementClient = entityStatementClient;
    this.entityStatementParser = entityStatementParser;
    this.signedJwksClient = signedJwksClient;
    this.keyStore = keyStore;
  }

  public PoppTokenVerificationResult verifyToken(@NonNull String token) {
    var entityStatementJwt = entityStatementClient.fetchEntityStatementJwt();
    var jwksUri = entityStatementParser.extractSignedJwksUri(entityStatementJwt);
    var expectedIssuer = entityStatementParser.extractSubject(entityStatementJwt);
    var signedJwks = signedJwksClient.fetchSignedJwks(jwksUri);
    var entitySigningPublicKey = loadEntitySigningPublicKey();

    JWTClaimsSet claims;
    claims = verify(signedJwks, entitySigningPublicKey, token);
    validatePoppClaims(claims, expectedIssuer);

    return new PoppTokenVerificationResult(
        true,
        claims.getIssuer(),
        (String) claims.getClaim("actorId"),
        claims.getIssueTime().toInstant(),
        null);
  }

  private void validatePoppClaims(JWTClaimsSet claimsSet, String expectedIssuer) {

    var issuer = claimsSet.getIssuer();
    if (issuer == null || issuer.isBlank()) {
      throw new PoppTokenValidationException(ErrorCode.TOKEN_MALFORMED, "missing iss claim");
    }

    if (!issuer.equals(expectedIssuer)) {
      throw new PoppTokenValidationException(
          ErrorCode.ISSUER_INVALID, "PoPP token iss does not match entity statement sub");
    }

    String actorId;
    try {
      actorId = claimsSet.getStringClaim("actorId");
    } catch (ParseException e) {
      throw new PoppTokenValidationException(
          ErrorCode.TOKEN_MALFORMED, "actorId claim is not a string", e);
    }

    if (actorId == null || actorId.isBlank()) {
      throw new PoppTokenValidationException(ErrorCode.TOKEN_MALFORMED, "missing actorId claim");
    }
  }

  private PublicKey loadEntitySigningPublicKey() {
    PublicKey entitySigningPublicKey;
    try {
      var aliases = keyStore.aliases();
      if (!aliases.hasMoreElements()) {
        throw new PoppTokenValidationException(
            ErrorCode.INTERNAL_ERROR, "No aliases found in keystore");
      }

      var alias = aliases.nextElement();
      var certificate = keyStore.getCertificate(alias);
      if (certificate == null) {
        throw new PoppTokenValidationException(
            ErrorCode.INTERNAL_ERROR, "No certificate found for alias: " + alias);
      }

      entitySigningPublicKey = certificate.getPublicKey();
    } catch (KeyStoreException e) {
      throw new PoppTokenValidationException(
          ErrorCode.INTERNAL_ERROR, "Failed to load entity signing public key from keystore", e);
    }
    return entitySigningPublicKey;
  }

  public JWTClaimsSet verify(
      String signedJwksJwt, PublicKey entitySigningPublicKey, String poppToken) {
    try {
      var signedJwks = SignedJWT.parse(signedJwksJwt);

      if (!verifyWithPublicKey(signedJwks, entitySigningPublicKey)) {
        throw new PoppTokenValidationException(
            ErrorCode.SIGNATURE_INVALID, "signed JWKS signature invalid");
      }

      var typ =
          signedJwks.getHeader().getType() != null
              ? signedJwks.getHeader().getType().toString()
              : null;

      if (!"jwk-set+jwt".equals(typ)) {
        throw new PoppTokenValidationException(
            ErrorCode.TOKEN_MALFORMED, "unexpected signed JWKS typ");
      }

      var jwksJson = signedJwks.getPayload().toString();
      var jwkSet = JWKSet.parse(jwksJson);

      var poppJwt = SignedJWT.parse(poppToken);
      verifyPoppJwtHeader(poppJwt);

      var kid = getKid(poppJwt);

      var matchingJwk = jwkSet.getKeyByKeyId(kid);

      if (matchingJwk == null) {
        throw new PoppTokenValidationException(
            ErrorCode.TOKEN_MALFORMED, "no matching JWK for kid " + kid);
      }

      if (!(matchingJwk instanceof ECKey ecKey)) {
        throw new PoppTokenValidationException(
            ErrorCode.TOKEN_MALFORMED, "matching JWK is not an EC key");
      }

      var verifier = new ECDSAVerifier(ecKey.toECPublicKey());

      if (!poppJwt.verify(verifier)) {
        throw new PoppTokenValidationException(
            ErrorCode.SIGNATURE_INVALID, "PoPP token signature invalid");
      }

      return poppJwt.getJWTClaimsSet();

    } catch (ParseException e) {
      throw new PoppTokenValidationException(
          ErrorCode.TOKEN_MALFORMED, "Malformed signed JWKS or PoPP token", e);
    } catch (JOSEException e) {
      throw new PoppTokenValidationException(
          ErrorCode.INTERNAL_ERROR, "JOSE verification failed", e);
    }
  }

  private void verifyPoppJwtHeader(SignedJWT poppJwt) {
    var poppJwtHeader = poppJwt.getHeader();
    if (!JWSAlgorithm.ES256.equals(poppJwtHeader.getAlgorithm())) {
      throw new PoppTokenValidationException(
          ErrorCode.ALGORITHM_INVALID, "unexpected PoPP token alg");
    }
    var type = poppJwtHeader.getType();
    if (type == null || !"vnd.telematik.popp+jwt".equals(type.getType())) {
      throw new PoppTokenValidationException(
          ErrorCode.TOKEN_MALFORMED, "unexpected PoPP token typ");
    }
    var kid = poppJwtHeader.getKeyID();
    if (kid == null || kid.isBlank()) {
      throw new PoppTokenValidationException(ErrorCode.KID_NOT_FOUND, "missing kid");
    }
  }

  private @NonNull String getKid(SignedJWT poppJwt) {
    return poppJwt.getHeader().getKeyID();
  }

  private boolean verifyWithPublicKey(SignedJWT jwt, PublicKey publicKey) throws JOSEException {
    if (publicKey instanceof ECPublicKey ecPublicKey) {
      return jwt.verify(new ECDSAVerifier(ecPublicKey));
    }

    if (publicKey instanceof RSAPublicKey rsaPublicKey) {
      return jwt.verify(new RSASSAVerifier(rsaPublicKey));
    }

    throw new PoppTokenValidationException(
        ErrorCode.PUBLIC_KEY_UNSUPPORTED,
        "Unsupported entity signing public key type: " + publicKey.getAlgorithm());
  }
}
