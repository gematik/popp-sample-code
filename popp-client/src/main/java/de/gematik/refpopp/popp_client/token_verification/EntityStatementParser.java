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

import com.nimbusds.jwt.SignedJWT;
import de.gematik.refpopp.popp_client.controller.ErrorCode;
import de.gematik.refpopp.popp_client.controller.PoppTokenValidationException;
import java.text.ParseException;
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
}
