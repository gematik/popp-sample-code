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

package de.gematik.refpopp.popp_server.scenario.contactbased.readx509cert;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.refpopp.popp_server.repository.CertHashRepository;
import de.gematik.refpopp.popp_server.scenario.BaseIntegrationTest;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

class ReadX509ScenarioResultProcessorIT extends BaseIntegrationTest {

  @Autowired private SessionContainer sessionContainer;

  @Autowired private ReadX509ScenarioResultProcessor sut;

  @Autowired private CertHashRepository certHashRepository;

  AutoCloseable autoCloseable;

  @BeforeEach
  void setUp() {
    autoCloseable = MockitoAnnotations.openMocks(this);
    certHashRepository.deleteAll();
  }

  @AfterEach
  void tearDown() throws Exception {
    autoCloseable.close();
    certHashRepository.deleteAll();
  }

  @Test
  void process() throws JsonProcessingException {
    // given
    final var sessionId = "sessionId";
    final var autAsHex =
        "30820356308202fda003020102020701e3568f9f2bfe300a06082a8648ce3d040302308196310b3009060355040613024445311f301d060355040a0c1667656d6174696b20476d6248204e4f542d56414c494431453043060355040b0c3c456c656b74726f6e697363686520476573756e6468656974736b617274652d4341206465722054656c656d6174696b696e667261737472756b747572311f301d06035504030c1647454d2e45474b2d4341353120544553542d4f4e4c59301e170d3234303430323030303030305a170d3239303430313233353935395a3081d6310b3009060355040613024445311d301b060355040a0c145465737420474b562d53564e4f542d56414c494431123010060355040b0c0931303935303039363931133011060355040b0c0a583131303534303538303110300e06035504040c074ec3b674686572312a3028060355042a0c214b7269656d68696c642044c3b6727468652054696e61204d617269652d456c6c61310c300a060355040c0c0344722e3133303106035504030c2a44722e204b7269656d68696c6420442e20542e204d2e2d452e204ec3b674686572544553542d4f4e4c59305a301406072a8648ce3d020106092b2403030208010107034200042d9fcb5ddf077543a2adfd1f3f385aecb59ff3eadcc4f2d959c51461e65d973b143ce6c1b42bd74346b2752bd43873b0b2cd9981f7b788d3efb3c3ee362e3166a381f23081ef303b06082b06010505070101042f302d302b06082b06010505073001861f687474703a2f2f656863612e67656d6174696b2e64652f6563632d6f63737030200603551d2004193017300a06082a8214004c048123300906072a8214004c0446303006052b240803030427302530233021301f301d30100c0e56657273696368657274652f2d72300906072a8214004c0431300e0603551d0f0101ff040403020780300c0603551d130101ff04023000301f0603551d2304183016801474e9f91483e10be611f62aac97ecd9af9943c1f0301d0603551d0e04160414a9a47da95fafcdf07729881fb17570ae8935f875300a06082a8648ce3d040302034700304402200b2bc991e86003291d358e10fa96112ee3626bfdd3aae37d3cc9a8237d1a57f602203bdffe6088ac00cbd5d111e01ef53d1792ac049436b155180d6bb2dd30ee38bc";
    final var autAsBytes = HexFormat.of().parseHex(autAsHex);
    final var cvcAsHex =
        "7f2181d87f4e81915f290170420844454758588702227f494d06082a8648ce3d04030286410428405a0ccc5c53b6780356a5141eb47fed5f56be44bc22f2046fc053fedbc25e50e24a6d6af95c1cfee9497acce359a253f7d0b7abaea5d1a62de030145f0c975f200844454758581102237f4c1306082a8214004c0481185307800000000000005f25060203000703015f24060301000703005f37404cd260c0803b125a001ba81ba9f2e2b1390de4f14691c822a28cc776a186d7ba7f08704c27fdcdaeb1f8b243a37976cf37bf7c121858d0f0419de83217a395de";
    final var cvcAsBytes = HexFormat.of().parseHex(cvcAsHex);
    final var resultStep = new ScenarioResultStep("read-ef-c-ch-aut-e256", "9000", autAsBytes);
    final var resultStep2 = new ScenarioResultStep("description2", "6985", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));

    sessionContainer.storeSessionData(
        sessionId, SessionStorageKey.CARD_CONNECTION_TYPE, CardConnectionType.CONTACT_STANDARD);
    sessionContainer.storeSessionData(sessionId, SessionStorageKey.CVC, cvcAsBytes);

    // when
    sut.process(sessionId, scenarioResult);

    // then
    final var jwt =
        sessionContainer.retrieveSessionData(sessionId, SessionStorageKey.JWT_TOKEN, String.class);
    assertThat(jwt).isPresent();
    final var token = (String) jwt.get();

    final String[] parts = token.split("\\.");
    assertThat(parts).hasSize(3);

    final String header = new String(Base64.getUrlDecoder().decode(parts[0]));
    final String payload = new String(Base64.getUrlDecoder().decode(parts[1]));

    final var objectMapper = new ObjectMapper();
    final Map<String, Object> headerMap = objectMapper.readValue(header, Map.class);
    final Map<String, Object> payloadMap = objectMapper.readValue(payload, Map.class);

    assertThat(headerMap)
        .containsEntry("alg", "ES256")
        .containsEntry("typ", "vnd.telematik.popp+jwt")
        .containsKey("kid");

    assertThat(payloadMap)
        .containsEntry("iss", "https://popp.example.com")
        .containsEntry("version", "1.0.0")
        .containsKeys(
            "iat",
            "proofMethod",
            "patientProofTime",
            "patientId",
            "insurerId",
            "actorId",
            "actorProfessionOid",
            "authorization_details");
  }
}
