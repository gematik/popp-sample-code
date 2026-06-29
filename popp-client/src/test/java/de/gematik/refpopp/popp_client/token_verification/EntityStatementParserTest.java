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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import de.gematik.refpopp.popp_client.controller.ErrorCode;
import de.gematik.refpopp.popp_client.controller.PoppTokenValidationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityStatementParserTest {

  private EntityStatementParser sut;

  @BeforeEach
  void setUp() {
    sut = new EntityStatementParser();
  }

  private String buildSignedJwt(JWTClaimsSet claims) throws JOSEException {
    var ecJwk = new ECKeyGenerator(Curve.P_256).generate();
    var header = new JWSHeader(JWSAlgorithm.ES256);
    var signedJWT = new SignedJWT(header, claims);
    signedJWT.sign(new ECDSASigner(ecJwk));
    return signedJWT.serialize();
  }

  @Test
  void extractSignedJwksUriReturnsUriWhenClaimsAreValid() throws JOSEException {
    // given
    final String expectedUri = "https://example.com/signed-jwks";
    var claims =
        new JWTClaimsSet.Builder()
            .subject("https://server.example.com")
            .claim("metadata", Map.of("oauth_resource", Map.of("signed_jwks_uri", expectedUri)))
            .build();
    final String jwt = buildSignedJwt(claims);

    // when
    final String result = sut.extractSignedJwksUri(jwt);

    // then
    assertThat(result).isEqualTo(expectedUri);
  }

  @Test
  void extractSignedJwksUriThrowsWhenMetadataClaimIsMissing() throws JOSEException {
    // given
    var claims = new JWTClaimsSet.Builder().subject("https://server.example.com").build();
    final String jwt = buildSignedJwt(claims);

    // when / then
    assertThatThrownBy(() -> sut.extractSignedJwksUri(jwt))
        .isInstanceOf(PoppTokenValidationException.class)
        .hasMessageContaining("metadata claim is missing")
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void extractSignedJwksUriThrowsWhenOauthResourceIsMissing() throws JOSEException {
    // given
    var claims =
        new JWTClaimsSet.Builder()
            .subject("https://server.example.com")
            .claim("metadata", Map.of("other_key", "other_value"))
            .build();
    final String jwt = buildSignedJwt(claims);

    // when / then
    assertThatThrownBy(() -> sut.extractSignedJwksUri(jwt))
        .isInstanceOf(PoppTokenValidationException.class)
        .hasMessageContaining("oauth_resource")
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void extractSignedJwksUriThrowsWhenSignedJwksUriIsBlank() throws JOSEException {
    // given
    var claims =
        new JWTClaimsSet.Builder()
            .subject("https://server.example.com")
            .claim("metadata", Map.of("oauth_resource", Map.of("signed_jwks_uri", "  ")))
            .build();
    final String jwt = buildSignedJwt(claims);

    // when / then
    assertThatThrownBy(() -> sut.extractSignedJwksUri(jwt))
        .isInstanceOf(PoppTokenValidationException.class)
        .hasMessageContaining("signed_jwks_uri")
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void extractSignedJwksUriThrowsWhenInputIsNotAValidJwt() {
    // given
    final String invalidJwt = "this-is-not-a-jwt";

    // when / then
    assertThatThrownBy(() -> sut.extractSignedJwksUri(invalidJwt))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ENTITY_STATEMENT_INVALID));
  }

  @Test
  void extractSubjectReturnsSubjectWhenJwtIsValid() throws JOSEException {
    // given
    final String expectedSubject = "https://server.example.com";
    var claims = new JWTClaimsSet.Builder().subject(expectedSubject).build();
    final String jwt = buildSignedJwt(claims);

    // when
    final String result = sut.extractSubject(jwt);

    // then
    assertThat(result).isEqualTo(expectedSubject);
  }

  @Test
  void extractSubjectThrowsWhenSubjectIsMissing() throws JOSEException {
    // given
    var claims = new JWTClaimsSet.Builder().issuer("some-issuer").build();
    final String jwt = buildSignedJwt(claims);

    // when / then
    assertThatThrownBy(() -> sut.extractSubject(jwt))
        .isInstanceOf(PoppTokenValidationException.class)
        .hasMessageContaining("Missing sub claim")
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void extractSubjectThrowsWhenInputIsNotAValidJwt() {
    // given
    final String invalidJwt = "not.valid.jwt";

    // when / then
    assertThatThrownBy(() -> sut.extractSubject(invalidJwt))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void extractSigningPublicKeyReturnsEcPublicKeyWhenJwksClaimIsValid() throws Exception {
    // given
    final ECKey signingKey = new ECKeyGenerator(Curve.P_256).keyID("signing-kid").generate();
    var claims =
        new JWTClaimsSet.Builder()
            .claim(
                "jwks", Map.of("keys", java.util.List.of(signingKey.toPublicJWK().toJSONObject())))
            .build();
    final String jwt = buildSignedJwt(claims);

    // when
    final var result = sut.extractSigningPublicKey(jwt);

    // then
    assertThat(result).isEqualTo(signingKey.toECPublicKey());
  }

  @Test
  void extractSigningPublicKeyThrowsWhenJwksClaimIsMissing() throws JOSEException {
    // given
    var claims = new JWTClaimsSet.Builder().subject("https://server.example.com").build();
    final String jwt = buildSignedJwt(claims);

    // when / then
    assertThatThrownBy(() -> sut.extractSigningPublicKey(jwt))
        .isInstanceOf(PoppTokenValidationException.class)
        .hasMessageContaining("jwks claim is missing")
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void extractSigningPublicKeyThrowsWhenNoSigningKeyExistsInJwks() throws JOSEException {
    // given
    final ECKey encryptionKey =
        new ECKeyGenerator(Curve.P_256).keyID("enc-kid").keyUse(KeyUse.ENCRYPTION).generate();
    var claims =
        new JWTClaimsSet.Builder()
            .claim(
                "jwks",
                Map.of("keys", java.util.List.of(encryptionKey.toPublicJWK().toJSONObject())))
            .build();
    final String jwt = buildSignedJwt(claims);

    // when / then
    assertThatThrownBy(() -> sut.extractSigningPublicKey(jwt))
        .isInstanceOf(PoppTokenValidationException.class)
        .hasMessageContaining("no signing key found")
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void extractSigningPublicKeyThrowsWhenInputIsNotAValidJwt() {
    // given
    final String invalidJwt = "not-a-jwt";

    // when / then
    assertThatThrownBy(() -> sut.extractSigningPublicKey(invalidJwt))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void extractSigningPublicKeyReturnsRsaPublicKeyWhenJwksContainsRsaKey() throws Exception {
    // given
    final var rsaKey = new RSAKeyGenerator(2048).keyID("rsa-kid").generate();
    var claims =
        new JWTClaimsSet.Builder()
            .claim("jwks", Map.of("keys", List.of(rsaKey.toPublicJWK().toJSONObject())))
            .build();
    final String jwt = buildSignedJwt(claims);

    // when
    final var result = sut.extractSigningPublicKey(jwt);

    // then
    assertThat(result).isEqualTo(rsaKey.toRSAPublicKey());
  }

  @Test
  void extractSigningPublicKeyThrowsWhenJwksKeysListIsEmpty() throws JOSEException {
    // given
    var claims = new JWTClaimsSet.Builder().claim("jwks", Map.of("keys", List.of())).build();
    final String jwt = buildSignedJwt(claims);

    // when / then
    assertThatThrownBy(() -> sut.extractSigningPublicKey(jwt))
        .isInstanceOf(PoppTokenValidationException.class)
        .hasMessageContaining("no signing key found")
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void extractSigningPublicKeyThrowsWhenKeyTypeIsUnsupported() throws Exception {
    // given - OctetSequenceKey (symmetric) is neither ECKey nor RSAKey
    final var symmetricKey =
        new OctetSequenceKey.Builder(new byte[32])
            .keyID("sym-kid")
            .keyUse(KeyUse.SIGNATURE)
            .build();
    var claims =
        new JWTClaimsSet.Builder()
            .claim("jwks", Map.of("keys", List.of(symmetricKey.toJSONObject())))
            .build();
    final String jwt = buildSignedJwt(claims);

    // when / then
    assertThatThrownBy(() -> sut.extractSigningPublicKey(jwt))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PUBLIC_KEY_UNSUPPORTED));
  }

  @Test
  void extractSubjectThrowsWhenSubjectIsBlank() throws JOSEException {
    // given
    var claims = new JWTClaimsSet.Builder().subject("   ").build();
    final String jwt = buildSignedJwt(claims);

    // when / then
    assertThatThrownBy(() -> sut.extractSubject(jwt))
        .isInstanceOf(PoppTokenValidationException.class)
        .hasMessageContaining("Missing sub claim")
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void extractSignedJwksUriThrowsWhenSignedJwksUriIsNotAString() throws JOSEException {
    // given
    var claims =
        new JWTClaimsSet.Builder()
            .subject("https://server.example.com")
            .claim("metadata", Map.of("oauth_resource", Map.of("signed_jwks_uri", 42)))
            .build();
    final String jwt = buildSignedJwt(claims);

    // when / then
    assertThatThrownBy(() -> sut.extractSignedJwksUri(jwt))
        .isInstanceOf(PoppTokenValidationException.class)
        .hasMessageContaining("signed_jwks_uri")
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }
}
