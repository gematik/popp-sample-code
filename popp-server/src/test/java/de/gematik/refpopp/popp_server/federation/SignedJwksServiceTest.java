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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import de.gematik.refpopp.popp_server.security.jwk.JwkKidGenerator;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.jose4j.json.JsonUtil;
import org.jose4j.jws.JsonWebSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignedJwksServiceTest {

  private static final String KEYSTORE_PASSWORD = "secret";
  private static final String ENTITY_SIGNING_ALIAS = "entity-signing";
  private static final String POPP_TOKEN_SIGNING_ALIAS = "popp-token-signing";

  @Mock private JwkKidGenerator jwkKidGenerator;

  @Mock private KeyStore poppKeyStore;

  @Mock private KeyStore federationKeyStore;

  @Mock private Certificate entitySigningCertificate;

  @Mock private Certificate poppTokenCertificate;

  private SignedJwksService service;

  @BeforeEach
  void setUp() {
    FederationProperties federationProperties = new FederationProperties();
    federationProperties.setEntitySigningAlias(ENTITY_SIGNING_ALIAS);
    federationProperties.setPoppTokenSigningAlias(POPP_TOKEN_SIGNING_ALIAS);

    service =
        new SignedJwksService(
            jwkKidGenerator,
            federationProperties,
            poppKeyStore,
            federationKeyStore,
            KEYSTORE_PASSWORD);
  }

  @Test
  void createReturnsSignedJwksWithExpectedHeadersAndPayload() throws Exception {
    var entitySigningKeyPair = generateEcKeyPair();
    var entitySigningPublicKey = (ECPublicKey) entitySigningKeyPair.getPublic();
    var entitySigningPrivateKey = entitySigningKeyPair.getPrivate();

    var poppTokenKeyPair = generateEcKeyPair();
    var poppTokenPublicKey = (ECPublicKey) poppTokenKeyPair.getPublic();

    var entitySigningKid = "entity-kid";
    var poppTokenKid = "popp-kid";
    var encodedCertificate = new byte[] {1, 2, 3, 4, 5};
    var expectedX5cEntry = Base64.getEncoder().encodeToString(encodedCertificate);

    given(federationKeyStore.getCertificate(ENTITY_SIGNING_ALIAS))
        .willReturn(entitySigningCertificate);
    given(entitySigningCertificate.getPublicKey()).willReturn(entitySigningPublicKey);

    given(poppKeyStore.getCertificate(POPP_TOKEN_SIGNING_ALIAS)).willReturn(poppTokenCertificate);
    given(poppTokenCertificate.getPublicKey()).willReturn(poppTokenPublicKey);
    given(poppTokenCertificate.getEncoded()).willReturn(encodedCertificate);

    given(jwkKidGenerator.generate(entitySigningPublicKey)).willReturn(entitySigningKid);
    given(jwkKidGenerator.generate(poppTokenPublicKey)).willReturn(poppTokenKid);

    given(federationKeyStore.getKey(ENTITY_SIGNING_ALIAS, KEYSTORE_PASSWORD.toCharArray()))
        .willReturn(entitySigningPrivateKey);

    var compactJws = service.create();
    assertThat(compactJws).isNotBlank();

    var jws = new JsonWebSignature();
    jws.setCompactSerialization(compactJws);
    jws.setKey(entitySigningPublicKey);

    assertThat(jws.verifySignature()).isTrue();
    assertThat(jws.getAlgorithmHeaderValue()).isEqualTo("ES256");
    assertThat(jws.getHeader("typ")).isEqualTo("jwk-set+jwt");
    assertThat(jws.getHeader("kid")).isEqualTo(entitySigningKid);

    Map<String, Object> payload = JsonUtil.parseJson(jws.getPayload());

    assertThat(payload).containsOnlyKeys("keys");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> keys = (List<Map<String, Object>>) payload.get("keys");

    assertThat(keys).hasSize(1);

    Map<String, Object> jwk = keys.getFirst();
    assertThat(jwk)
        .containsEntry("kty", "EC")
        .containsEntry("crv", "P-256")
        .containsEntry("kid", poppTokenKid)
        .containsEntry("alg", "ES256")
        .containsEntry("use", "sig");

    assertThat(jwk.get("x")).isInstanceOf(String.class);
    assertThat(jwk.get("y")).isInstanceOf(String.class);
    assertThat(Base64.getUrlDecoder().decode((String) jwk.get("x"))).hasSize(32);
    assertThat(Base64.getUrlDecoder().decode((String) jwk.get("y"))).hasSize(32);

    @SuppressWarnings("unchecked")
    List<String> x5c = (List<String>) jwk.get("x5c");

    assertThat(x5c).containsExactly(expectedX5cEntry);

    verify(jwkKidGenerator).generate(entitySigningPublicKey);
    verify(jwkKidGenerator).generate(poppTokenPublicKey);
    verify(federationKeyStore).getCertificate(ENTITY_SIGNING_ALIAS);
    verify(poppKeyStore).getCertificate(POPP_TOKEN_SIGNING_ALIAS);
    verify(federationKeyStore).getKey(ENTITY_SIGNING_ALIAS, KEYSTORE_PASSWORD.toCharArray());
  }

  @Test
  void createNormalizesCoordinatesWithLeadingSignByte() throws Exception {
    var entitySigningKeyPair = generateEcKeyPair();
    var entitySigningPublicKey = (ECPublicKey) entitySigningKeyPair.getPublic();
    var entitySigningPrivateKey = entitySigningKeyPair.getPrivate();

    // Forces BigInteger#toByteArray() to produce 33 bytes with leading zero.
    var x =
        new BigInteger(
            1,
            new byte[] {
              (byte) 0x80,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1,
              1
            });
    var y =
        new BigInteger(
            1,
            new byte[] {
              (byte) 0x81,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2,
              2
            });
    var poppTokenPublicKey = mockEcPublicKey(x, y);

    given(federationKeyStore.getCertificate(ENTITY_SIGNING_ALIAS))
        .willReturn(entitySigningCertificate);
    given(entitySigningCertificate.getPublicKey()).willReturn(entitySigningPublicKey);

    given(poppKeyStore.getCertificate(POPP_TOKEN_SIGNING_ALIAS)).willReturn(poppTokenCertificate);
    given(poppTokenCertificate.getPublicKey()).willReturn(poppTokenPublicKey);
    given(poppTokenCertificate.getEncoded()).willReturn(new byte[] {9, 8, 7});

    given(jwkKidGenerator.generate(entitySigningPublicKey)).willReturn("entity-signing-kid");
    given(jwkKidGenerator.generate(poppTokenPublicKey)).willReturn("popp-token-kid");

    given(federationKeyStore.getKey(ENTITY_SIGNING_ALIAS, KEYSTORE_PASSWORD.toCharArray()))
        .willReturn(entitySigningPrivateKey);

    var compactJws = service.create();

    var payload = extractPayload(compactJws);
    @SuppressWarnings("unchecked")
    var keys = (List<Map<String, Object>>) payload.get("keys");
    var jwk = keys.getFirst();

    var normalizedX = Base64.getUrlDecoder().decode((String) jwk.get("x"));
    var normalizedY = Base64.getUrlDecoder().decode((String) jwk.get("y"));

    assertThat(normalizedX).hasSize(32);
    assertThat(normalizedY).hasSize(32);
    assertThat(normalizedX[0]).isEqualTo((byte) 0x80);
    assertThat(normalizedY[0]).isEqualTo((byte) 0x81);
  }

  @Test
  void createPadsShortCoordinatesToFixedLength() throws Exception {
    var entitySigningKeyPair = generateEcKeyPair();
    var entitySigningPublicKey = (ECPublicKey) entitySigningKeyPair.getPublic();
    var entitySigningPrivateKey = entitySigningKeyPair.getPrivate();

    var poppTokenPublicKey = mockEcPublicKey(BigInteger.ONE, BigInteger.TWO);

    given(federationKeyStore.getCertificate(ENTITY_SIGNING_ALIAS))
        .willReturn(entitySigningCertificate);
    given(entitySigningCertificate.getPublicKey()).willReturn(entitySigningPublicKey);
    given(federationKeyStore.getKey(ENTITY_SIGNING_ALIAS, KEYSTORE_PASSWORD.toCharArray()))
        .willReturn(entitySigningPrivateKey);
    given(poppKeyStore.getCertificate(POPP_TOKEN_SIGNING_ALIAS)).willReturn(poppTokenCertificate);
    given(poppTokenCertificate.getPublicKey()).willReturn(poppTokenPublicKey);
    given(poppTokenCertificate.getEncoded()).willReturn(new byte[] {1, 2, 3});
    given(jwkKidGenerator.generate(entitySigningPublicKey)).willReturn("entity-kid");
    given(jwkKidGenerator.generate(poppTokenPublicKey)).willReturn("popp-kid");

    var compactJws = service.create();

    var payload = extractPayload(compactJws);
    @SuppressWarnings("unchecked")
    var keys = (List<Map<String, Object>>) payload.get("keys");
    var jwk = keys.getFirst();

    var normalizedX = Base64.getUrlDecoder().decode((String) jwk.get("x"));
    var normalizedY = Base64.getUrlDecoder().decode((String) jwk.get("y"));

    assertThat(normalizedX).hasSize(32);
    assertThat(normalizedY).hasSize(32);
    assertThat(normalizedX[31]).isEqualTo((byte) 1);
    assertThat(normalizedY[31]).isEqualTo((byte) 2);
  }

  @Test
  void createThrowsWhenCoordinateIsTooLarge() throws Exception {
    var entitySigningKeyPair = generateEcKeyPair();
    var entitySigningPublicKey = (ECPublicKey) entitySigningKeyPair.getPublic();

    var tooLarge =
        new BigInteger(
            1,
            new byte[] {
              1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
              1, 1, 1, 1
            });
    var poppTokenPublicKey = mockEcPublicKey(tooLarge, BigInteger.ONE);

    given(federationKeyStore.getCertificate(ENTITY_SIGNING_ALIAS))
        .willReturn(entitySigningCertificate);
    given(entitySigningCertificate.getPublicKey()).willReturn(entitySigningPublicKey);
    given(poppKeyStore.getCertificate(POPP_TOKEN_SIGNING_ALIAS)).willReturn(poppTokenCertificate);
    given(poppTokenCertificate.getPublicKey()).willReturn(poppTokenPublicKey);
    given(jwkKidGenerator.generate(entitySigningPublicKey)).willReturn("entity-kid");
    given(jwkKidGenerator.generate(poppTokenPublicKey)).willReturn("popp-kid");

    assertThatThrownBy(() -> service.create())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Coordinate too large");
  }

  @Test
  void createWhenGettingEntitySigningCertificateFailsThrowsSignedJwksCreationException()
      throws Exception {
    given(federationKeyStore.getCertificate(ENTITY_SIGNING_ALIAS))
        .willThrow(new java.security.KeyStoreException("boom"));

    assertThatThrownBy(() -> service.create())
        .isInstanceOf(SignedJwksCreationException.class)
        .hasMessage("Failed to create signed JWKS")
        .hasCauseInstanceOf(java.security.KeyStoreException.class);
  }

  @Test
  void createWhenGettingEntitySigningPrivateKeyFailsThrowsSignedJwksCreationException()
      throws Exception {
    var entitySigningKeyPair = generateEcKeyPair();
    var entitySigningPublicKey = (ECPublicKey) entitySigningKeyPair.getPublic();

    var poppTokenKeyPair = generateEcKeyPair();
    var poppTokenPublicKey = (ECPublicKey) poppTokenKeyPair.getPublic();

    given(federationKeyStore.getCertificate(ENTITY_SIGNING_ALIAS))
        .willReturn(entitySigningCertificate);
    given(entitySigningCertificate.getPublicKey()).willReturn(entitySigningPublicKey);

    given(poppKeyStore.getCertificate(POPP_TOKEN_SIGNING_ALIAS)).willReturn(poppTokenCertificate);
    given(poppTokenCertificate.getPublicKey()).willReturn(poppTokenPublicKey);
    given(poppTokenCertificate.getEncoded()).willReturn(new byte[] {1, 2, 3});

    given(jwkKidGenerator.generate(entitySigningPublicKey)).willReturn("entity-kid");
    given(jwkKidGenerator.generate(poppTokenPublicKey)).willReturn("popp-kid");

    given(federationKeyStore.getKey(ENTITY_SIGNING_ALIAS, KEYSTORE_PASSWORD.toCharArray()))
        .willThrow(new UnrecoverableKeyException("wrong password"));

    assertThatThrownBy(() -> service.create())
        .isInstanceOf(SignedJwksCreationException.class)
        .hasMessage("Failed to create signed JWKS")
        .hasCauseInstanceOf(UnrecoverableKeyException.class);
  }

  @Test
  void createWhenCertificateEncodingFailsThrowsSignedJwksCreationException() throws Exception {
    var entitySigningKeyPair = generateEcKeyPair();
    var entitySigningPublicKey = (ECPublicKey) entitySigningKeyPair.getPublic();

    var poppTokenKeyPair = generateEcKeyPair();
    var poppTokenPublicKey = (ECPublicKey) poppTokenKeyPair.getPublic();

    given(federationKeyStore.getCertificate(ENTITY_SIGNING_ALIAS))
        .willReturn(entitySigningCertificate);
    given(entitySigningCertificate.getPublicKey()).willReturn(entitySigningPublicKey);

    given(poppKeyStore.getCertificate(POPP_TOKEN_SIGNING_ALIAS)).willReturn(poppTokenCertificate);
    given(poppTokenCertificate.getPublicKey()).willReturn(poppTokenPublicKey);
    given(poppTokenCertificate.getEncoded())
        .willThrow(new CertificateEncodingException("bad cert"));

    given(jwkKidGenerator.generate(entitySigningPublicKey)).willReturn("entity-kid");
    given(jwkKidGenerator.generate(poppTokenPublicKey)).willReturn("popp-kid");

    assertThatThrownBy(() -> service.create())
        .isInstanceOf(SignedJwksCreationException.class)
        .hasMessage("Failed to create signed JWKS")
        .hasCauseInstanceOf(CertificateEncodingException.class);
  }

  @Test
  void createUsesEntitySigningKeyForJwsSignatureAndHeaderKid() throws Exception {
    var entitySigningKeyPair = generateEcKeyPair();
    var entitySigningPublicKey = (ECPublicKey) entitySigningKeyPair.getPublic();
    var entitySigningPrivateKey = entitySigningKeyPair.getPrivate();

    var poppTokenKeyPair = generateEcKeyPair();
    var poppTokenPublicKey = (ECPublicKey) poppTokenKeyPair.getPublic();

    given(federationKeyStore.getCertificate(ENTITY_SIGNING_ALIAS))
        .willReturn(entitySigningCertificate);
    given(entitySigningCertificate.getPublicKey()).willReturn(entitySigningPublicKey);

    given(poppKeyStore.getCertificate(POPP_TOKEN_SIGNING_ALIAS)).willReturn(poppTokenCertificate);
    given(poppTokenCertificate.getPublicKey()).willReturn(poppTokenPublicKey);
    given(poppTokenCertificate.getEncoded()).willReturn(new byte[] {9, 8, 7});

    given(jwkKidGenerator.generate(entitySigningPublicKey)).willReturn("entity-signing-kid");
    given(jwkKidGenerator.generate(poppTokenPublicKey)).willReturn("popp-token-kid");

    given(federationKeyStore.getKey(ENTITY_SIGNING_ALIAS, KEYSTORE_PASSWORD.toCharArray()))
        .willReturn(entitySigningPrivateKey);

    var compactJws = service.create();

    var jws = new JsonWebSignature();
    jws.setCompactSerialization(compactJws);
    jws.setKey(entitySigningPublicKey);

    assertThat(jws.verifySignature()).isTrue();
    assertThat(jws.getHeader("kid")).isEqualTo("entity-signing-kid");
  }

  private static ECPublicKey mockEcPublicKey(BigInteger x, BigInteger y) {
    var key = org.mockito.Mockito.mock(ECPublicKey.class);
    given(key.getW()).willReturn(new ECPoint(x, y));
    return key;
  }

  private static Map<String, Object> extractPayload(String compactJws) throws Exception {
    var payloadPart = compactJws.split("\\.")[1];
    var payloadJson =
        new String(Base64.getUrlDecoder().decode(payloadPart), StandardCharsets.UTF_8);
    return JsonUtil.parseJson(payloadJson);
  }

  private static KeyPair generateEcKeyPair() throws Exception {
    var keyPairGenerator = KeyPairGenerator.getInstance("EC");
    keyPairGenerator.initialize(256);
    return keyPairGenerator.generateKeyPair();
  }
}
