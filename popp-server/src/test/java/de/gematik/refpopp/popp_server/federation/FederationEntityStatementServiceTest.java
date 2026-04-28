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
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import de.gematik.refpopp.popp_server.security.jwk.JwkKidGenerator;
import java.security.*;
import java.security.cert.Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.Map;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FederationEntityStatementServiceTest {

  private static final String KEYSTORE_PASSWORD = "secret";
  private static final String SIGNING_ALIAS = "entity-signing";
  private static final String ORGANIZATION_NAME = "My Organization";
  private static final String FEDERATION_MASTER_ISSUER = "https://federation-master.example";

  @Mock private JwkKidGenerator jwkKidGenerator;

  @Mock private FederationProperties federationProperties;

  @Mock private KeyStore keyStore;

  @Mock private Certificate certificate;

  private FederationEntityStatementService service;

  @BeforeEach
  void setUp() {
    service =
        new FederationEntityStatementService(
            jwkKidGenerator, federationProperties, keyStore, KEYSTORE_PASSWORD);
  }

  @Test
  void createReturnsSignedEntityStatementWithExpectedHeadersClaimsAndNormalizedBaseUrl()
      throws Exception {
    // given
    var keyPair = generateEcKeyPair();
    var publicKey = (ECPublicKey) keyPair.getPublic();
    var privateKey = keyPair.getPrivate();
    var kid = "kid-123";
    var baseUrlWithTrailingSlash = "https://popp.example/";

    given(keyStore.getCertificate(any())).willReturn(certificate);
    given(certificate.getPublicKey()).willReturn(publicKey);
    given(jwkKidGenerator.generate(publicKey)).willReturn(kid);
    given(keyStore.getKey(any(), any())).willReturn(privateKey);
    given(federationProperties.getOrgName()).willReturn(ORGANIZATION_NAME);
    given(federationProperties.getMasterIssuer()).willReturn(FEDERATION_MASTER_ISSUER);
    given(federationProperties.getIssuer()).willReturn("https://popp.example");
    given(federationProperties.getSubject()).willReturn("https://popp.example");
    given(federationProperties.getEntitySigningAlias()).willReturn(SIGNING_ALIAS);
    given(federationProperties.getBaseUrl()).willReturn(baseUrlWithTrailingSlash);

    // when
    var compactJws = service.create();

    // then
    assertThat(compactJws).isNotBlank();

    var jws = new JsonWebSignature();
    jws.setCompactSerialization(compactJws);
    jws.setKey(publicKey);

    assertThat(jws.verifySignature()).isTrue();
    assertThat(jws.getAlgorithmHeaderValue()).isEqualTo("ES256");
    assertThat(jws.getHeader("typ")).isEqualTo("entity-statement+jwt");
    assertThat(jws.getHeader("kid")).isEqualTo(kid);

    var claims = JwtClaims.parse(jws.getPayload());
    var normalizedBaseUrl = "https://popp.example";

    assertThat(claims.getIssuer()).isEqualTo(normalizedBaseUrl);
    assertThat(claims.getSubject()).isEqualTo(normalizedBaseUrl);

    assertThat(claims.getClaimValue("authority_hints"))
        .asInstanceOf(list(String.class))
        .containsExactly(FEDERATION_MASTER_ISSUER);

    assertThat(claims.getClaimValue("metadata"))
        .asInstanceOf(map(String.class, Object.class))
        .containsKey("federation_entity")
        .extractingByKey("federation_entity", map(String.class, Object.class))
        .containsEntry("organization_name", ORGANIZATION_NAME);

    assertThat(claims.getClaimValue("metadata"))
        .asInstanceOf(map(String.class, Object.class))
        .containsKey("oauth_resource")
        .extractingByKey("oauth_resource", map(String.class, Object.class))
        .containsEntry("signed_jwks_uri", "https://popp.example/.well-known/signed-jwks");

    var jwks = claims.getClaimValue("jwks", Map.class);
    var keys = (List<?>) jwks.get("keys");
    assertThat(keys).hasSize(1);

    @SuppressWarnings("unchecked")
    Map<String, Object> jwk = (Map<String, Object>) keys.getFirst();
    assertThat(jwk)
        .containsEntry("kty", "EC")
        .containsEntry("crv", "P-256")
        .containsEntry("kid", kid)
        .containsEntry("alg", "ES256")
        .containsEntry("use", "sig");

    assertThat(claims.getIssuedAt()).isNotNull();
    assertThat(claims.getExpirationTime()).isNotNull();
    assertThat(claims.getExpirationTime().getValue())
        .isGreaterThan(claims.getIssuedAt().getValue());

    verify(jwkKidGenerator).generate(publicKey);
    verify(keyStore).getCertificate(SIGNING_ALIAS);
    verify(keyStore).getKey(SIGNING_ALIAS, KEYSTORE_PASSWORD.toCharArray());
  }

  @Test
  void createGeneratesJwtThatCanBeValidatedWithPublicKey() throws Exception {
    // given
    var keyPair = generateEcKeyPair();
    var publicKey = (ECPublicKey) keyPair.getPublic();
    var privateKey = keyPair.getPrivate();
    var kid = "kid-456";

    given(keyStore.getCertificate(SIGNING_ALIAS)).willReturn(certificate);
    given(certificate.getPublicKey()).willReturn(publicKey);
    given(jwkKidGenerator.generate(publicKey)).willReturn(kid);
    given(keyStore.getKey(any(), any())).willReturn(privateKey);
    given(federationProperties.getIssuer()).willReturn("https://issuer.example");
    given(federationProperties.getSubject()).willReturn("https://issuer.example");
    given(federationProperties.getOrgName()).willReturn(ORGANIZATION_NAME);
    given(federationProperties.getMasterIssuer()).willReturn(FEDERATION_MASTER_ISSUER);
    given(federationProperties.getEntitySigningAlias()).willReturn(SIGNING_ALIAS);
    given(federationProperties.getBaseUrl()).willReturn("https://issuer.example");

    // when
    var token = service.create();

    // then
    var claims =
        new JwtConsumerBuilder()
            .setRequireSubject()
            .setSkipAllValidators()
            .setDisableRequireSignature()
            .setVerificationKey(publicKey)
            .build()
            .processToClaims(token);

    assertThat(claims.getIssuer()).isEqualTo("https://issuer.example");
    assertThat(claims.getSubject()).isEqualTo("https://issuer.example");
  }

  @Test
  void createPassesEntitySigningPublicKeyToKidGenerator() throws Exception {
    // given
    var keyPair = generateEcKeyPair();
    var publicKey = (ECPublicKey) keyPair.getPublic();
    var privateKey = keyPair.getPrivate();

    given(keyStore.getCertificate(SIGNING_ALIAS)).willReturn(certificate);
    given(certificate.getPublicKey()).willReturn(publicKey);
    given(jwkKidGenerator.generate(publicKey)).willReturn("kid-789");
    given(keyStore.getKey(SIGNING_ALIAS, KEYSTORE_PASSWORD.toCharArray())).willReturn(privateKey);
    given(federationProperties.getOrgName()).willReturn(ORGANIZATION_NAME);
    given(federationProperties.getMasterIssuer()).willReturn(FEDERATION_MASTER_ISSUER);
    given(federationProperties.getIssuer()).willReturn("https://popp.example");
    given(federationProperties.getSubject()).willReturn("https://popp.example");
    given(federationProperties.getEntitySigningAlias()).willReturn(SIGNING_ALIAS);
    given(federationProperties.getBaseUrl()).willReturn("https://issuer.example");

    // when
    service.create();

    // then
    verify(jwkKidGenerator).generate(publicKey);
  }

  @Test
  void createWhenGettingCertificateFailsThrowsIllegalStateException() throws Exception {
    // given
    given(federationProperties.getBaseUrl()).willReturn("https://issuer.example");
    given(keyStore.getCertificate(any())).willThrow(new KeyStoreException("boom"));

    // when // then
    assertThatThrownBy(() -> service.create())
        .isInstanceOf(FederationEntityStatementCreationException.class)
        .hasMessage("Failed to create federation entity statement")
        .hasCauseInstanceOf(KeyStoreException.class);
  }

  @Test
  void createWhenGettingPrivateKeyFailsThrowsIllegalStateException() throws Exception {
    // given
    var keyPair = generateEcKeyPair();
    var publicKey = (ECPublicKey) keyPair.getPublic();

    given(keyStore.getCertificate(any())).willReturn(certificate);
    given(certificate.getPublicKey()).willReturn(publicKey);
    given(jwkKidGenerator.generate(publicKey)).willReturn("kid-123");
    given(federationProperties.getMasterIssuer()).willReturn("https://issuer.example");
    given(federationProperties.getOrgName()).willReturn("orgName");
    given(keyStore.getKey(any(), any())).willThrow(new UnrecoverableKeyException("wrong password"));
    given(federationProperties.getBaseUrl()).willReturn("https://issuer.example");

    // when // then
    assertThatThrownBy(() -> service.create())
        .isInstanceOf(FederationEntityStatementCreationException.class)
        .hasMessage("Failed to create federation entity statement")
        .hasCauseInstanceOf(UnrecoverableKeyException.class);
  }

  private static KeyPair generateEcKeyPair() throws Exception {
    var keyPairGenerator = KeyPairGenerator.getInstance("EC");
    keyPairGenerator.initialize(256);
    return keyPairGenerator.generateKeyPair();
  }
}
