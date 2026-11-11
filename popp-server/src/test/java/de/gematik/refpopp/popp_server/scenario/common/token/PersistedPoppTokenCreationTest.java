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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.refpopp.popp_server.certificates.CertificateProviderService;
import de.gematik.refpopp.popp_server.certificates.KeyStoreData;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationRecord;
import de.gematik.refpopp.popp_server.security.jwk.JwkKidGenerator;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jose4j.jws.JsonWebSignature;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PersistedPoppTokenCreationTest {

  @Test
  void signsJwtWithClaimsFromPersistedRecordWithoutSessionData() throws Exception {
    final var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(256);
    final var keys = generator.generateKeyPair();
    final var publicKey = (ECPublicKey) keys.getPublic();
    final var certificate = mock(X509Certificate.class);
    when(certificate.getPublicKey()).thenReturn(publicKey);
    final var certificateProvider = mock(CertificateProviderService.class);
    when(certificateProvider.getKeyStoreDataPoppToken())
        .thenReturn(
            KeyStoreData.withoutIssuer((ECPrivateKey) keys.getPrivate(), List.of(certificate)));

    final var sessionContainer = mock(SessionContainer.class);
    final var claims = new PoppTokenClaims(sessionContainer);
    ReflectionTestUtils.setField(claims, "version", "1.0.0");
    ReflectionTestUtils.setField(claims, "issuer", "https://popp.example.com");
    ReflectionTestUtils.setField(claims, "actorProfessionOid", "1.2.276.0.76.4.50");
    ReflectionTestUtils.setField(claims, "authorizationDetails", "details");
    final var header = new PoppTokenHeader(new JwkKidGenerator());
    ReflectionTestUtils.setField(header, "poppTokenType", "vnd.telematik.popp+jwt");
    final var creator =
        new PoppTokenCreator(certificateProvider, claims, header, new JwtTokenBuilder());
    final var proofTime = Instant.parse("2026-09-22T10:00:00Z");
    final var tokenRecord =
        new PoppTokenGenerationRecord(
            UUID.randomUUID(),
            "A123456789",
            "123456789",
            "actor-from-record",
            "workplace-1",
            proofTime);
    final var before = Instant.now().getEpochSecond();

    final var jwt = creator.createPoppToken(tokenRecord);
    final var after = Instant.now().getEpochSecond();

    final var signed = new JsonWebSignature();
    signed.setCompactSerialization(jwt);
    signed.setKey(publicKey);
    assertThat(signed.verifySignature()).isTrue();
    assertThat(signed.getAlgorithmHeaderValue()).isEqualTo("ES256");
    assertThat(signed.getHeader("typ")).isEqualTo("vnd.telematik.popp+jwt");
    assertThat(signed.getKeyIdHeaderValue()).isEqualTo(new JwkKidGenerator().generate(publicKey));

    final var payload = new ObjectMapper().readTree(signed.getPayload());
    assertThat(payload.get("version").asText()).isEqualTo("1.0.0");
    assertThat(payload.get("iss").asText()).isEqualTo("https://popp.example.com");
    assertThat(payload.get("iat").asLong()).isBetween(before, after);
    assertThat(payload.get("patientProofTime").asLong()).isEqualTo(proofTime.getEpochSecond());
    assertThat(payload.get("proofMethod").asText()).isEqualTo("healthid");
    assertThat(payload.get("patientId").asText()).isEqualTo("A123456789");
    assertThat(payload.get("insurerId").asText()).isEqualTo("123456789");
    assertThat(payload.get("actorId").asText()).isEqualTo("actor-from-record");
    assertThat(payload.get("actorProfessionOid").asText()).isEqualTo("1.2.276.0.76.4.50");
    assertThat(payload.get("authorization_details").asText()).isEqualTo("details");
    verifyNoInteractions(sessionContainer);
  }
}
