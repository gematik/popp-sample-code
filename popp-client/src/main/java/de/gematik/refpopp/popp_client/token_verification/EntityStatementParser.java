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
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import de.gematik.refpopp.popp_client.controller.ErrorCode;
import de.gematik.refpopp.popp_client.controller.PoppTokenValidationException;
import java.security.PublicKey;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EntityStatementParser {

  public String extractSignedJwksUri(String entityStatementJwt) {
    try {
      var signedJWT = SignedJWT.parse(entityStatementJwt);

      Map<String, Object> claims = signedJWT.getJWTClaimsSet().getClaims();

      Object metadataObj = claims.get("metadata");
      if (!(metadataObj instanceof Map<?, ?> metadata)) {
        throw new PoppTokenValidationException(
            ErrorCode.TOKEN_MALFORMED, "metadata claim is missing in entity statement");
      }

      Object oauthResourceObj = metadata.get("oauth_resource");
      if (!(oauthResourceObj instanceof Map<?, ?> oauthResource)) {
        throw new PoppTokenValidationException(
            ErrorCode.TOKEN_MALFORMED, "metadata.oauth_resource is missing in  entity statement");
      }

      Object signedJwksUriObj = oauthResource.get("signed_jwks_uri");
      if (!(signedJwksUriObj instanceof String signedJwksUri) || signedJwksUri.isBlank()) {
        throw new PoppTokenValidationException(
            ErrorCode.TOKEN_MALFORMED,
            "metadata.oauth_resource.signed_jwks_uri is missing in  entity statement");
      }

      return signedJwksUri;

    } catch (ParseException e) {
      throw new PoppTokenValidationException(
          ErrorCode.ENTITY_STATEMENT_INVALID, "Entity Statement is not a valid JWT", e);
    }
  }

  public String extractSubject(String entityStatementJwt) {
    try {
      var signedJwt = SignedJWT.parse(entityStatementJwt);
      var claims = signedJwt.getJWTClaimsSet();

      var subject = claims.getSubject();

      if (subject == null || subject.isBlank()) {
        throw new PoppTokenValidationException(
            ErrorCode.TOKEN_MALFORMED, "Missing sub claim in entity statement");
      }

      return subject;

    } catch (ParseException e) {
      throw new PoppTokenValidationException(
          ErrorCode.TOKEN_MALFORMED, "Failed to parse entity statement JWT", e);
    }
  }

  public PublicKey extractSigningPublicKey(String entityStatementJwt) {
    try {
      var entityStatement = SignedJWT.parse(entityStatementJwt);
      var jwksClaim = entityStatement.getJWTClaimsSet().getJSONObjectClaim("jwks");
      if (jwksClaim == null) {
        throw new PoppTokenValidationException(
            ErrorCode.TOKEN_MALFORMED, "jwks claim is missing in entity statement");
      }

      var jwkSet = JWKSet.parse(jwksClaim);
      var signingJwk = selectSigningJwk(jwkSet.getKeys());
      if (signingJwk == null) {
        throw new PoppTokenValidationException(
            ErrorCode.TOKEN_MALFORMED, "no signing key found in entity statement jwks claim");
      }

      if (signingJwk instanceof ECKey ecKey) {
        return ecKey.toECPublicKey();
      }

      if (signingJwk instanceof RSAKey rsaKey) {
        return rsaKey.toRSAPublicKey();
      }
    } catch (ParseException e) {
      throw new PoppTokenValidationException(
          ErrorCode.TOKEN_MALFORMED, "Failed to parse entity statement JWT", e);
    } catch (JOSEException e) {
      throw new PoppTokenValidationException(
          ErrorCode.INTERNAL_ERROR,
          "Failed to load entity signing public key from entity statement jwks claim",
          e);
    }

    throw new PoppTokenValidationException(
        ErrorCode.PUBLIC_KEY_UNSUPPORTED,
        "Unsupported entity signing public key type in entity statement jwks claim");
  }

  private JWK selectSigningJwk(List<JWK> jwks) {
    if (jwks == null || jwks.isEmpty()) {
      return null;
    }

    return jwks.stream()
        .filter(jwk -> jwk.getKeyUse() == null || KeyUse.SIGNATURE.equals(jwk.getKeyUse()))
        .findFirst()
        .orElse(null);
  }
}
