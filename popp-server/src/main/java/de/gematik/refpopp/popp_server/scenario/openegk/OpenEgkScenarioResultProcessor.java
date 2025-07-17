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

package de.gematik.refpopp.popp_server.scenario.openegk;

import static de.gematik.poppcommons.api.enums.CardConnectionType.CONTACTLESS_CONNECTOR;
import static de.gematik.poppcommons.api.enums.CardConnectionType.CONTACTLESS_STANDARD;
import static de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode.CONTACT;
import static de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode.CONTACTLESS;
import static de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode.G3;

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultProcessor;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import de.gematik.smartcards.tlv.BerTlv;
import de.gematik.smartcards.tlv.ConstructedBerTlv;
import de.gematik.smartcards.utils.Hex;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OpenEgkScenarioResultProcessor implements ScenarioResultProcessor {

  private final SessionAccessor sessionAccessor;
  private final ScenarioResultFinder scenarioResultFinder;

  @Value("${scenario-names.sce-open-egk}")
  private String openEgkScenarioName;

  @Value("${step-names.read-version}")
  private String readVersion;

  @Value("${scenario-vars.supported-g21-cards}")
  private List<String> supportedG21Cards;

  @Value("${scenario-vars.supported-g3-cards}")
  private List<String> supportedG3Cards;

  public OpenEgkScenarioResultProcessor(
      final SessionAccessor sessionAccessor, final ScenarioResultFinder scenarioResultFinder) {
    this.sessionAccessor = sessionAccessor;
    this.scenarioResultFinder = scenarioResultFinder;
  }

  @Override
  public void process(final String sessionId, final ScenarioResult scenarioResult) {
    log.debug("| Entering processScenarioResult()");

    final var efVersion = readVersion(sessionId, scenarioResult);
    final var ptvObjSys = getPtvObjSys(sessionId, efVersion);
    processPtvObjSys(sessionId, ptvObjSys);

    log.debug("| Exiting processScenarioResult()");
  }

  @Override
  public String getScenarioName() {
    return openEgkScenarioName;
  }

  private void processPtvObjSys(final String sessionId, final String ptvObjSys) {
    if (supportedG21Cards.contains(ptvObjSys)) {
      if (isCardConnectionTypeContactLess(sessionId)) {
        sessionAccessor.storeCommunicationMode(sessionId, CONTACTLESS);
      } else {
        sessionAccessor.storeCommunicationMode(sessionId, CONTACT);
      }
    } else if (supportedG3Cards.contains(ptvObjSys)) {
      sessionAccessor.storeCommunicationMode(sessionId, G3);
    } else {
      throw new ScenarioException(
          sessionId, "unsupported PTV object system: " + ptvObjSys, "errorCode");
    }
  }

  private boolean isCardConnectionTypeContactLess(final String sessionId) {
    final var connectionType = sessionAccessor.getCardConnectionType(sessionId);
    return connectionType == CONTACTLESS_CONNECTOR || connectionType == CONTACTLESS_STANDARD;
  }

  private String getPtvObjSys(final String sessionId, final EfVersion efVersion) {
    final String ptvObjSys;
    if ("020000".equals(efVersion.versionFilling())) {
      ptvObjSys =
          Hex.toHexDigits(efVersion.efV2().getPrimitive(0xc1).orElseThrow().getValueField());
    } else {
      throw new ScenarioException(
          sessionId,
          "unsupported version of content in EF.Version2: " + efVersion.versionFilling(),
          "errorCode");
    }
    return ptvObjSys;
  }

  private EfVersion readVersion(final String sessionId, final ScenarioResult scenarioResult) {
    final var rspEfVersion2 =
        scenarioResultFinder
            .find(sessionId, scenarioResult.scenarioResultSteps(), readVersion)
            .data();
    final var efV2 = (ConstructedBerTlv) BerTlv.getInstance(rspEfVersion2);
    final var versionFilling =
        Hex.toHexDigits(
            efV2.getPrimitive(0xc0)
                .orElseThrow(
                    () -> new ScenarioException(sessionId, "missing version filling", "errorCode"))
                .getValueField());
    return new EfVersion(efV2, versionFilling);
  }

  private record EfVersion(ConstructedBerTlv efV2, String versionFilling) {}
}
