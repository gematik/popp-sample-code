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

package de.gematik.refpopp.popp_server.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.refpopp.popp_server.certificates.CertificateProviderService;
import de.gematik.refpopp.popp_server.model.PoppMessageRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationRecord;
import de.gematik.refpopp.popp_server.model.PoppTokenGenerationStatus;
import de.gematik.refpopp.popp_server.repository.PoppMessageRecordRepository;
import de.gematik.refpopp.popp_server.repository.PoppTokenGenerationRecordRepository;
import de.gematik.refpopp.popp_server.scenario.BaseIntegrationTest;
import de.gematik.refpopp.popp_server.security.jwk.JwkKidGenerator;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jose4j.jws.JsonWebSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class TokenDeliveryEndpointIT extends BaseIntegrationTest {

  private static final String ENDPOINT = "/popp/practitioner/api/v1/token-deliveries";

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private MockMvc mockMvc;

  @Autowired private PoppMessageRecordRepository messageRepository;
  @Autowired private PoppTokenGenerationRecordRepository recordRepository;
  @Autowired private CertificateProviderService certificateProvider;
  @Autowired private JwkKidGenerator kidGenerator;

  private final ObjectMapper mapper = new ObjectMapper();
  private final List<UUID> createdIds = new java.util.ArrayList<>();

  @AfterEach
  void removeTestRecords() {
    recordRepository.deleteAllById(createdIds);
    messageRepository.deleteAllById(createdIds);
  }

  @Test
  void deliversRealSignedTokensAndPersistsTheirStatusThroughHttp() throws Exception {
    final var actor = "actor-" + UUID.randomUUID();
    final var firstProofTime = Instant.now().minus(Duration.ofMinutes(20));
    final var firstId = persist(actor, "workplace-a", firstProofTime);
    final var secondId = persist(actor, "workplace-b", firstProofTime.plusSeconds(60));

    final var firstResponse = request(actor, "[\"workplace-a\",\"workplace-b\"]", 1);
    final var firstTokens = firstResponse.get("popptokens");
    assertThat(firstTokens.size()).isEqualTo(1);
    assertThat(firstResponse.get("hasmore").asBoolean()).isTrue();
    assertThat(firstTokens.get(0).get("poppStatusRecordId").asString())
        .isEqualTo(firstId.toString());
    assertThat(firstTokens.get(0).get("workplaceId").asString()).isEqualTo("workplace-a");
    assertSignedToken(firstTokens.get(0).get("token").asString(), actor, firstProofTime);
    assertDelivered(firstId);
    assertThat(messageRepository.findById(secondId).orElseThrow().getStatus())
        .isEqualTo(PoppTokenGenerationStatus.PENDING);

    final var secondResponse = request(actor, "[]", 1);
    assertThat(secondResponse.get("popptokens").size()).isEqualTo(1);
    assertThat(secondResponse.get("popptokens").get(0).get("poppStatusRecordId").asString())
        .isEqualTo(secondId.toString());
    assertThat(secondResponse.get("hasmore").asBoolean()).isFalse();
    assertDelivered(secondId);

    final var emptyResponse = request(actor, "[]", 1);
    assertThat(emptyResponse.get("popptokens").size()).isZero();
    assertThat(emptyResponse.get("hasmore").asBoolean()).isFalse();
  }

  private JsonNode request(String actor, String workplaces, int limit) throws Exception {
    final var body =
        """
        {"telematikId":"%s","workplaceIds":%s,"limit":%d}
        """
            .formatted(actor, workplaces, limit);
    final var result =
        mockMvc
            .perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-useragent", "CLIENT-1/2.1")
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();
    return mapper.readTree(result.getResponse().getContentAsString());
  }

  private void assertSignedToken(String jwt, String actor, Instant proofTime) throws Exception {
    final var publicKey =
        (ECPublicKey) certificateProvider.getKeyStoreDataPoppToken().certificate().getPublicKey();
    final var jws = new JsonWebSignature();
    jws.setCompactSerialization(jwt);
    jws.setKey(publicKey);
    assertThat(jws.verifySignature()).isTrue();
    assertThat(jws.getAlgorithmHeaderValue()).isEqualTo("ES256");
    assertThat(jws.getHeader("typ")).isEqualTo("vnd.telematik.popp+jwt");
    assertThat(jws.getKeyIdHeaderValue()).isEqualTo(kidGenerator.generate(publicKey));

    final var claims = mapper.readTree(jws.getPayload());
    assertThat(claims.get("actorId").asString()).isEqualTo(actor);
    assertThat(claims.get("patientId").asString()).isEqualTo("A123456789");
    assertThat(claims.get("insurerId").asString()).isEqualTo("123456789");
    assertThat(claims.get("proofMethod").asString()).isEqualTo("healthid");
    assertThat(claims.get("patientProofTime").asLong()).isEqualTo(proofTime.getEpochSecond());
    assertThat(claims.get("iss").asString()).isEqualTo("http://popp-server:8443");
    assertThat(claims.get("iat").asLong())
        .isBetween(proofTime.getEpochSecond(), Instant.now().getEpochSecond());
  }

  private void assertDelivered(UUID id) {
    final var message = messageRepository.findById(id).orElseThrow();
    assertThat(message.getStatus()).isEqualTo(PoppTokenGenerationStatus.SUCCESS);
    assertThat(message.getTokenDeliveredAt()).isNotNull();
  }

  private UUID persist(String actor, String workplace, Instant timestamp) {
    final var id = UUID.randomUUID();
    messageRepository.saveAndFlush(new PoppMessageRecord(id, "client-id", timestamp));
    recordRepository.saveAndFlush(
        new PoppTokenGenerationRecord(id, "A123456789", "123456789", actor, workplace, timestamp));
    createdIds.add(id);
    return id;
  }
}
