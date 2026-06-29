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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import de.gematik.refpopp.popp_client.controller.ErrorCode;
import de.gematik.refpopp.popp_client.controller.PoppTokenValidationException;
import java.util.Date;
import java.util.stream.Stream;
import net.minidev.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PoppTokenVerificationServiceTest {

  private EntityStatementClient entityStatementClientMock;
  private EntityStatementParser entityStatementParserMock;
  private SignedJwksClient signedJwksClientMock;

  private PoppTokenVerificationService sut;

  @BeforeEach
  void setUp() {
    entityStatementClientMock = mock(EntityStatementClient.class);
    entityStatementParserMock = mock(EntityStatementParser.class);
    signedJwksClientMock = mock(SignedJwksClient.class);
    sut =
        new PoppTokenVerificationService(
            entityStatementClientMock, entityStatementParserMock, signedJwksClientMock);
  }

  @Test
  void verifyReturnsClainsWhenSignedJwksAndPoppTokenAreValid() throws JOSEException {
    // given
    final var ecJwk = new ECKeyGenerator(Curve.P_256).keyID("kid-1").generate();

    final String signedJwksJwt = buildSignedJwks(ecJwk);
    final String poppToken = buildPoppToken(ecJwk);
    final var entitySigningPublicKey = ecJwk.toECPublicKey();

    // when
    final var claims = sut.verify(signedJwksJwt, entitySigningPublicKey, poppToken);

    // then
    assertThat(claims).isNotNull();
    assertThat(claims.getIssuer()).isEqualTo("https://server.example.com");
  }

  @Test
  void verifyThrowsWhenSignedJwksSignatureIsInvalid() throws JOSEException {
    // given
    final var signingKey = new ECKeyGenerator(Curve.P_256).generate();
    final var differentKey = new ECKeyGenerator(Curve.P_256).keyID("kid-1").generate();

    final String signedJwksJwt = buildSignedJwks(differentKey);
    final String poppToken = buildPoppToken(differentKey);
    // Use a different public key - signature will not match
    final var wrongPublicKey = signingKey.toECPublicKey();

    // when / then
    assertThatThrownBy(() -> sut.verify(signedJwksJwt, wrongPublicKey, poppToken))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SIGNATURE_INVALID));
  }

  @Test
  void verifyThrowsWhenSignedJwksTypIsWrong() throws JOSEException {
    // given
    final var ecJwk = new ECKeyGenerator(Curve.P_256).keyID("kid-1").generate();

    final var header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new com.nimbusds.jose.JOSEObjectType("wrong-typ"))
            .build();
    final var claims = new JWTClaimsSet.Builder().build();
    final var jwt = new SignedJWT(header, claims);
    jwt.sign(new ECDSASigner(ecJwk));
    final var rawJwt = buildSignedJwksWithTyp(ecJwk, "wrong-typ");
    final String poppToken = buildPoppToken(ecJwk);
    final var publicKey = ecJwk.toECPublicKey();

    // when / then
    assertThatThrownBy(() -> sut.verify(rawJwt, publicKey, poppToken))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void verifyThrowsWhenPoppTokenKidIsNotFoundInJwks() throws JOSEException {
    // given
    final var signingKey = new ECKeyGenerator(Curve.P_256).keyID("signing-key").generate();
    final var poppKey = new ECKeyGenerator(Curve.P_256).keyID("unknown-kid").generate();

    // signed JWKS contains signingKey but poppToken references poppKey
    final String signedJwksJwt = buildSignedJwks(signingKey);
    final String poppToken = buildPoppToken(poppKey);
    final var publicKey = signingKey.toECPublicKey();

    // when / then
    assertThatThrownBy(() -> sut.verify(signedJwksJwt, publicKey, poppToken))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void verifyThrowsWhenPoppTokenAlgorithmIsNotEs256() throws JOSEException {
    // given
    final var ecJwk = new ECKeyGenerator(Curve.P_256).keyID("kid-1").generate();
    final String signedJwksJwt = buildSignedJwks(ecJwk);
    // Build popp token with wrong alg (ES384 with P-384)
    final var poppKey384 = new ECKeyGenerator(Curve.P_384).keyID("kid-1").generate();
    final var poppHeader =
        new JWSHeader.Builder(JWSAlgorithm.ES384)
            .type(new com.nimbusds.jose.JOSEObjectType("vnd.telematik.popp+jwt"))
            .keyID("kid-1")
            .build();
    final var poppClaims = new JWTClaimsSet.Builder().issuer("https://server.example.com").build();
    final var poppJwt = new SignedJWT(poppHeader, poppClaims);
    poppJwt.sign(new ECDSASigner(poppKey384));
    final String poppToken = poppJwt.serialize();
    final var publicKey = ecJwk.toECPublicKey();

    // when / then
    assertThatThrownBy(() -> sut.verify(signedJwksJwt, publicKey, poppToken))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ALGORITHM_INVALID));
  }

  @Test
  void verifyThrowsWhenPoppTokenTypIsWrong() throws JOSEException {
    // given
    final var ecJwk = new ECKeyGenerator(Curve.P_256).keyID("kid-1").generate();
    final String signedJwksJwt = buildSignedJwks(ecJwk);

    final var poppHeader =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new com.nimbusds.jose.JOSEObjectType("wrong-typ"))
            .keyID("kid-1")
            .build();
    final var poppClaims = new JWTClaimsSet.Builder().issuer("https://server.example.com").build();
    final var poppJwt = new SignedJWT(poppHeader, poppClaims);
    poppJwt.sign(new ECDSASigner(ecJwk));
    final String poppToken = poppJwt.serialize();
    final var publicKey = ecJwk.toECPublicKey();

    // when / then
    assertThatThrownBy(() -> sut.verify(signedJwksJwt, publicKey, poppToken))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void verifyThrowsWhenPoppTokenKidIsMissing() throws JOSEException {
    // given
    final var ecJwk = new ECKeyGenerator(Curve.P_256).keyID("kid-1").generate();
    final String signedJwksJwt = buildSignedJwks(ecJwk);

    final var poppHeader =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new com.nimbusds.jose.JOSEObjectType("vnd.telematik.popp+jwt"))
            // no keyID
            .build();
    final var poppClaims = new JWTClaimsSet.Builder().issuer("https://server.example.com").build();
    final var poppJwt = new SignedJWT(poppHeader, poppClaims);
    poppJwt.sign(new ECDSASigner(ecJwk));
    final String poppToken = poppJwt.serialize();
    final var publicKey = ecJwk.toECPublicKey();

    // when / then
    assertThatThrownBy(() -> sut.verify(signedJwksJwt, publicKey, poppToken))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.KID_NOT_FOUND));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("entityStatementSigningKeyFailures")
  void verifyTokenThrowsWhenEntityStatementSigningKeyExtractionFails(
      String scenario, PoppTokenValidationException exception) {
    // given
    final String token = "any-token";
    final String entityStatement = setupVerifyTokenFailureDependencies();
    when(entityStatementParserMock.extractSigningPublicKey(entityStatement)).thenThrow(exception);

    // when / then
    assertThatThrownBy(() -> sut.verifyToken(token))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void verifyTokenReturnsValidResultWhenAllInputsAreValid() throws Exception {
    // given
    final var ecJwk = new ECKeyGenerator(Curve.P_256).keyID("kid-1").generate();
    final String entityStatement = "entity-statement";
    final String jwksUri = "https://example.com/signed-jwks";
    final String signedJwksJwt = buildSignedJwks(ecJwk);
    final String poppToken = buildPoppToken(ecJwk);

    when(entityStatementClientMock.fetchEntityStatementJwt()).thenReturn(entityStatement);
    when(entityStatementParserMock.extractSignedJwksUri(entityStatement)).thenReturn(jwksUri);
    when(entityStatementParserMock.extractSubject(entityStatement))
        .thenReturn("https://server.example.com");
    when(signedJwksClientMock.fetchSignedJwks(jwksUri)).thenReturn(signedJwksJwt);
    when(entityStatementParserMock.extractSigningPublicKey(entityStatement))
        .thenReturn(ecJwk.toECPublicKey());

    // when
    final var result = sut.verifyToken(poppToken);

    // then
    assertThat(result.valid()).isTrue();
    assertThat(result.issuer()).isEqualTo("https://server.example.com");
    assertThat(result.actorId()).isEqualTo("telematik-id");
    assertThat(result.error()).isNull();
    assertThat(result.issuedAt()).isNotNull();
  }

  @Test
  void verifyTokenThrowsWhenIssuerClaimIsMissing() throws Exception {
    // given
    final var ecJwk = new ECKeyGenerator(Curve.P_256).keyID("kid-1").generate();
    final String poppToken = buildPoppTokenWithClaims(ecJwk, null, "telematik-id");
    setupVerifyTokenDependencies(ecJwk);

    // when / then
    assertThatThrownBy(() -> sut.verifyToken(poppToken))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void verifyTokenThrowsWhenIssuerDoesNotMatchExpectedIssuer() throws Exception {
    // given
    final var ecJwk = new ECKeyGenerator(Curve.P_256).keyID("kid-1").generate();
    final String poppToken =
        buildPoppTokenWithClaims(ecJwk, "https://other.example.com", "telematik-id");
    setupVerifyTokenDependencies(ecJwk);

    // when / then
    assertThatThrownBy(() -> sut.verifyToken(poppToken))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ISSUER_INVALID));
  }

  @Test
  void verifyTokenThrowsWhenActorIdClaimIsMissing() throws Exception {
    // given
    final var ecJwk = new ECKeyGenerator(Curve.P_256).keyID("kid-1").generate();
    final String poppToken = buildPoppTokenWithClaims(ecJwk, "https://server.example.com", null);
    setupVerifyTokenDependencies(ecJwk);

    // when / then
    assertThatThrownBy(() -> sut.verifyToken(poppToken))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void verifyTokenThrowsWhenActorIdClaimIsNotString() throws Exception {
    // given
    final var ecJwk = new ECKeyGenerator(Curve.P_256).keyID("kid-1").generate();
    final String poppToken = buildPoppTokenWithClaims(ecJwk, "https://server.example.com", 123);
    setupVerifyTokenDependencies(ecJwk);

    // when / then
    assertThatThrownBy(() -> sut.verifyToken(poppToken))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_MALFORMED));
  }

  @Test
  void verifyThrowsWhenEntitySigningPublicKeyTypeIsUnsupported() throws JOSEException {
    // given
    final var ecJwk = new ECKeyGenerator(Curve.P_256).keyID("kid-1").generate();
    final String signedJwksJwt = buildSignedJwks(ecJwk);
    final String poppToken = buildPoppToken(ecJwk);
    final var unsupportedPublicKey = mock(java.security.PublicKey.class);
    when(unsupportedPublicKey.getAlgorithm()).thenReturn("DSA");

    // when / then
    assertThatThrownBy(() -> sut.verify(signedJwksJwt, unsupportedPublicKey, poppToken))
        .isInstanceOf(PoppTokenValidationException.class)
        .satisfies(
            e ->
                assertThat(((PoppTokenValidationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PUBLIC_KEY_UNSUPPORTED));
  }

  /**
   * Builds a signed JWKS JWT whose payload contains the public key of ecJwk as a JWK Set. The typ
   * header is set to "jwk-set+jwt".
   */
  private String buildSignedJwks(ECKey ecJwk) throws JOSEException {
    return buildSignedJwksWithTyp(ecJwk, "jwk-set+jwt");
  }

  private String buildSignedJwksWithTyp(ECKey ecJwk, String typ) throws JOSEException {
    var jwkSetJson = buildJwkSetJson(ecJwk);
    var header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new com.nimbusds.jose.JOSEObjectType(typ))
            .build();
    var claimsBuilder = new JWTClaimsSet.Builder();
    jwkSetJson.forEach(claimsBuilder::claim);
    var signedJwt = new SignedJWT(header, claimsBuilder.build());
    signedJwt.sign(new ECDSASigner(ecJwk));
    return signedJwt.serialize();
  }

  private JSONObject buildJwkSetJson(ECKey ecJwk) {
    var jwkSetObj = new JSONObject();
    var keysArray = new net.minidev.json.JSONArray();
    keysArray.add(ecJwk.toPublicJWK().toJSONObject());
    jwkSetObj.put("keys", keysArray);
    return jwkSetObj;
  }

  /** Builds a valid PoPP token signed with the given EC key. */
  private String buildPoppToken(ECKey ecJwk) throws JOSEException {
    var header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new com.nimbusds.jose.JOSEObjectType("vnd.telematik.popp+jwt"))
            .keyID(ecJwk.getKeyID())
            .build();
    var claims =
        new JWTClaimsSet.Builder()
            .issuer("https://server.example.com")
            .issueTime(new Date())
            .claim("actorId", "telematik-id")
            .build();
    var signedJwt = new SignedJWT(header, claims);
    signedJwt.sign(new ECDSASigner(ecJwk));
    return signedJwt.serialize();
  }

  private String buildPoppTokenWithClaims(ECKey ecJwk, String issuer, Object actorId)
      throws JOSEException {
    var header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new com.nimbusds.jose.JOSEObjectType("vnd.telematik.popp+jwt"))
            .keyID(ecJwk.getKeyID())
            .build();
    var claimsBuilder = new JWTClaimsSet.Builder().issueTime(new Date());
    if (issuer != null) {
      claimsBuilder.issuer(issuer);
    }
    if (actorId != null) {
      claimsBuilder.claim("actorId", actorId);
    }
    var signedJwt = new SignedJWT(header, claimsBuilder.build());
    signedJwt.sign(new ECDSASigner(ecJwk));
    return signedJwt.serialize();
  }

  private void setupVerifyTokenDependencies(ECKey ecJwk) throws Exception {
    final String entityStatement = "entity-statement";
    final String jwksUri = "https://example.com/signed-jwks";

    when(entityStatementClientMock.fetchEntityStatementJwt()).thenReturn(entityStatement);
    when(entityStatementParserMock.extractSignedJwksUri(entityStatement)).thenReturn(jwksUri);
    when(entityStatementParserMock.extractSubject(entityStatement))
        .thenReturn("https://server.example.com");
    when(signedJwksClientMock.fetchSignedJwks(jwksUri)).thenReturn(buildSignedJwks(ecJwk));
    when(entityStatementParserMock.extractSigningPublicKey(entityStatement))
        .thenReturn(ecJwk.toECPublicKey());
  }

  private String setupVerifyTokenFailureDependencies() {
    final String entityStatement = "entity-statement";
    final String jwksUri = "https://example.com/signed-jwks";

    when(entityStatementClientMock.fetchEntityStatementJwt()).thenReturn(entityStatement);
    when(entityStatementParserMock.extractSignedJwksUri(entityStatement)).thenReturn(jwksUri);
    when(entityStatementParserMock.extractSubject(entityStatement))
        .thenReturn("https://server.example.com");
    when(signedJwksClientMock.fetchSignedJwks(jwksUri)).thenReturn("signed-jwks-jwt");
    return entityStatement;
  }

  private static Stream<Arguments> entityStatementSigningKeyFailures() {
    return Stream.of(
        Arguments.of(
            Named.of("missing jwks claim", "missing jwks claim"),
            new PoppTokenValidationException(ErrorCode.TOKEN_MALFORMED, "jwks missing")),
        Arguments.of(
            Named.of("missing signing key", "missing signing key"),
            new PoppTokenValidationException(
                ErrorCode.TOKEN_MALFORMED, "no signing key found in entity statement jwks claim")),
        Arguments.of(
            Named.of("invalid entity statement jwt", "invalid entity statement jwt"),
            new PoppTokenValidationException(
                ErrorCode.TOKEN_MALFORMED, "Failed to parse entity statement JWT")));
  }
}
