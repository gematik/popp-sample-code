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

package de.gematik.refpopp.popp_server.scenario.contactbased.openegk;

import static de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer.SessionStorageKey.CARD_CONNECTION_TYPE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.refpopp.popp_server.scenario.openegk.OpenEgkScenarioResultProcessor;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class OpenEgkScenarioResultProcessorTest {

  public static final String SESSION_ID = "testSessionId";
  private static final HexFormat HEX_FORMAT = HexFormat.of();
  private OpenEgkScenarioResultProcessor sut;
  private SessionAccessor sessionAccessorMock;
  private ScenarioResultFinder scenarioResultFinderMock;

  @BeforeEach
  void setUp() {
    sessionAccessorMock = Mockito.mock(SessionAccessor.class);
    scenarioResultFinderMock = Mockito.mock(ScenarioResultFinder.class);
    sut = new OpenEgkScenarioResultProcessor(sessionAccessorMock, scenarioResultFinderMock);
    ReflectionTestUtils.setField(sut, "supportedG21Cards", List.of("040502"));
    ReflectionTestUtils.setField(sut, "supportedG3Cards", List.of("050000"));
    sessionAccessorMock.storeSessionData(
        SESSION_ID, CARD_CONNECTION_TYPE, CardConnectionType.CONTACT_STANDARD);
  }

  @Test
  void processWithContactBasedCard() {
    // Given
    final var efVersion = efVersion2("020000", "040502");
    final var resultStep = new ScenarioResultStep("description", "9000", efVersion);
    final var resultStep2 = new ScenarioResultStep("description2", "6985", efVersion);
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));
    when(sessionAccessorMock.getCardConnectionType(SESSION_ID))
        .thenReturn(CardConnectionType.CONTACT_STANDARD);
    when(scenarioResultFinderMock.find(
            SESSION_ID, scenarioResult.scenarioResultSteps(), StepId.READ_VERSION))
        .thenReturn(resultStep2);

    // When
    sut.process(SESSION_ID, scenarioResult);

    // Then
    Mockito.verify(sessionAccessorMock)
        .storeCommunicationMode(SESSION_ID, CommunicationMode.CONTACT);
  }

  @Test
  void processWithUnsupportedPtvObjSysThrowsException() {
    // Given
    final var efVersion = efVersion2("020000", "000000");
    final var resultStep = new ScenarioResultStep("name1", "9000", efVersion);
    final var resultStep2 = new ScenarioResultStep("name2", "6985", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));

    when(scenarioResultFinderMock.find(
            SESSION_ID, scenarioResult.scenarioResultSteps(), StepId.READ_VERSION))
        .thenReturn(resultStep);

    // When
    assertThrows(ScenarioException.class, () -> sut.process(SESSION_ID, scenarioResult));

    // Then
    Mockito.verify(sessionAccessorMock, Mockito.never()).storeCommunicationMode(anyString(), any());
  }

  @Test
  void processWithUnsupportedVersionOfContentThrowsException() {
    // Given
    final var resultStep =
        new ScenarioResultStep("description", "9000", efVersion2("010000", "040502"));
    final var resultStep2 = new ScenarioResultStep("description2", "6985", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));

    when(scenarioResultFinderMock.find(
            SESSION_ID, scenarioResult.scenarioResultSteps(), StepId.READ_VERSION))
        .thenReturn(resultStep);

    // When
    assertThrows(ScenarioException.class, () -> sut.process(SESSION_ID, scenarioResult));

    // Then
    Mockito.verify(sessionAccessorMock, Mockito.never()).storeCommunicationMode(anyString(), any());
  }

  @Test
  void processWithG3Cards() {
    // Given
    final var efVersion = efVersion2("020000", "050000");
    final var resultStep = new ScenarioResultStep("description", "9000", efVersion);
    final var resultStep2 = new ScenarioResultStep("description2", "6985", efVersion);
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));

    when(scenarioResultFinderMock.find(
            SESSION_ID, scenarioResult.scenarioResultSteps(), StepId.READ_VERSION))
        .thenReturn(resultStep2);

    // When
    try {
      sut.process(SESSION_ID, scenarioResult);
    } catch (final ScenarioException e) {
      fail("Should not throw exception");
    }

    // Then
    Mockito.verify(sessionAccessorMock).storeCommunicationMode(SESSION_ID, CommunicationMode.G3);
  }

  @Test
  void processWithContactLessCards() {
    // Given
    final var efVersion = efVersion2("020000", "040502");
    final var resultStep = new ScenarioResultStep("description", "9000", efVersion);
    final var resultStep2 = new ScenarioResultStep("description2", "6985", efVersion);
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));
    when(sessionAccessorMock.getCardConnectionType(SESSION_ID))
        .thenReturn(CardConnectionType.CONTACTLESS_STANDARD);
    when(scenarioResultFinderMock.find(
            SESSION_ID, scenarioResult.scenarioResultSteps(), StepId.READ_VERSION))
        .thenReturn(resultStep2);

    // When
    sut.process(SESSION_ID, scenarioResult);

    // Then
    Mockito.verify(sessionAccessorMock)
        .storeCommunicationMode(SESSION_ID, CommunicationMode.CONTACTLESS);
  }

  @Test
  void processWithContactlessConnectorStoresContactlessMode() {
    // Given
    final var efVersion = efVersion2("020000", "040502");
    final var resultStep = new ScenarioResultStep("description", "9000", efVersion);
    final var resultStep2 = new ScenarioResultStep("description2", "6985", efVersion);
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));
    when(sessionAccessorMock.getCardConnectionType(SESSION_ID))
        .thenReturn(CardConnectionType.CONTACTLESS_CONNECTOR);
    when(scenarioResultFinderMock.find(
            SESSION_ID, scenarioResult.scenarioResultSteps(), StepId.READ_VERSION))
        .thenReturn(resultStep2);

    // When
    sut.process(SESSION_ID, scenarioResult);

    // Then
    Mockito.verify(sessionAccessorMock)
        .storeCommunicationMode(SESSION_ID, CommunicationMode.CONTACTLESS);
  }

  @Test
  void processWithContactlessVirtualCardStoresContactlessMode() {
    // Given
    final var efVersion = efVersion2("020000", "040502");
    final var resultStep = new ScenarioResultStep("description", "9000", efVersion);
    final var resultStep2 = new ScenarioResultStep("description2", "6985", efVersion);
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));
    when(sessionAccessorMock.getCardConnectionType(SESSION_ID))
        .thenReturn(CardConnectionType.CONTACTLESS_VIRTUAL);
    when(scenarioResultFinderMock.find(
            SESSION_ID, scenarioResult.scenarioResultSteps(), StepId.READ_VERSION))
        .thenReturn(resultStep2);

    // When
    sut.process(SESSION_ID, scenarioResult);

    // Then
    Mockito.verify(sessionAccessorMock)
        .storeCommunicationMode(SESSION_ID, CommunicationMode.CONTACTLESS);
  }

  @Test
  void processThrowsExceptionWhenCardConnectionTypeNotFound() {
    // Given
    final var efVersion = efVersion2("020000", "040502");
    final var resultStep = new ScenarioResultStep("description", "9000", efVersion);
    final var resultStep2 = new ScenarioResultStep("description2", "6985", efVersion);
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));
    when(scenarioResultFinderMock.find(
            SESSION_ID, scenarioResult.scenarioResultSteps(), StepId.READ_VERSION))
        .thenReturn(resultStep2);
    when(sessionAccessorMock.getCardConnectionType(SESSION_ID))
        .thenThrow(new ScenarioException("", "", ""));

    // When
    assertThrows(ScenarioException.class, () -> sut.process(SESSION_ID, scenarioResult));

    // Then
    Mockito.verify(sessionAccessorMock, Mockito.never()).storeCommunicationMode(anyString(), any());
  }

  private static byte[] efVersion2(
      final String fillingInstructionsVersion, final String objectSystemVersion) {
    return HEX_FORMAT.parseHex(
        "ef2b"
            + "c003"
            + fillingInstructionsVersion
            + "c103"
            + objectSystemVersion
            + "c210444549444d4548435f39303030030005"
            + "c403010000"
            + "c503020000"
            + "c703010000");
  }
}
