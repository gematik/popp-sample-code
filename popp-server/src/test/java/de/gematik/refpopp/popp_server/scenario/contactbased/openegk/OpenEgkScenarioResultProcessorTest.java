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
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.refpopp.popp_server.scenario.openegk.OpenEgkScenarioResultProcessor;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import de.gematik.smartcards.tlv.BerTlv;
import de.gematik.smartcards.tlv.ConstructedBerTlv;
import de.gematik.smartcards.tlv.PrimitiveBerTlv;
import de.gematik.smartcards.utils.Hex;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class OpenEgkScenarioResultProcessorTest {

  public static final String SESSION_ID = "testSessionId";
  private OpenEgkScenarioResultProcessor sut;
  private SessionAccessor sessionAccessorMock;
  private ScenarioResultFinder scenarioResultFinderMock;

  @BeforeEach
  void setUp() {
    sessionAccessorMock = Mockito.mock(SessionAccessor.class);
    scenarioResultFinderMock = Mockito.mock(ScenarioResultFinder.class);
    sut = new OpenEgkScenarioResultProcessor(sessionAccessorMock, scenarioResultFinderMock);
    ReflectionTestUtils.setField(sut, "readVersion", "read-version");
    ReflectionTestUtils.setField(sut, "supportedG21Cards", List.of("040502"));
    ReflectionTestUtils.setField(sut, "supportedG3Cards", List.of("050000"));
    sessionAccessorMock.storeSessionData(
        SESSION_ID, CARD_CONNECTION_TYPE, CardConnectionType.CONTACT_STANDARD);
  }

  @Test
  void processWithContactBasedCard() {
    // Given
    final var byteArray = Hex.toByteArray("020000");
    final var resultStep = new ScenarioResultStep("description", "9000", byteArray);
    final var resultStep2 = new ScenarioResultStep("description2", "6985", byteArray);
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));
    when(sessionAccessorMock.getCardConnectionType(SESSION_ID))
        .thenReturn(CardConnectionType.CONTACT_STANDARD);
    when(scenarioResultFinderMock.find(
            SESSION_ID, scenarioResult.scenarioResultSteps(), "read-version"))
        .thenReturn(resultStep2);
    final var berTlvMock = Mockito.mock(ConstructedBerTlv.class);
    final var primitiveBerTlvMock1 = Mockito.mock(PrimitiveBerTlv.class);
    final var primitiveBerTlvMock2 = Mockito.mock(PrimitiveBerTlv.class);
    when(berTlvMock.getPrimitive(0xc0L)).thenReturn(Optional.of(primitiveBerTlvMock1));
    when(primitiveBerTlvMock1.getValueField()).thenReturn(byteArray);
    when(berTlvMock.getPrimitive(0xc1L)).thenReturn(Optional.of(primitiveBerTlvMock2));
    when(primitiveBerTlvMock2.getValueField()).thenReturn(Hex.toByteArray("040502"));
    try (final MockedStatic<BerTlv> berTlv = Mockito.mockStatic(BerTlv.class)) {
      berTlv.when(() -> BerTlv.getInstance(byteArray)).thenReturn(berTlvMock);

      // When
      sut.process(SESSION_ID, scenarioResult);

      // Then
      Mockito.verify(sessionAccessorMock)
          .storeCommunicationMode(SESSION_ID, CommunicationMode.CONTACT);
    }
  }

  @Test
  void processWithUnsupportedPtvObjSysThrowsException() {
    // Given
    final var byteArray = Hex.toByteArray("020000");
    final var resultStep = new ScenarioResultStep("name1", "9000", byteArray);
    final var resultStep2 = new ScenarioResultStep("name2", "6985", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));

    when(scenarioResultFinderMock.find(
            SESSION_ID, scenarioResult.scenarioResultSteps(), "read-version"))
        .thenReturn(resultStep);
    final var berTlvMock = Mockito.mock(ConstructedBerTlv.class);
    final var primitiveBerTlvMock1 = Mockito.mock(PrimitiveBerTlv.class);
    final var primitiveBerTlvMock2 = Mockito.mock(PrimitiveBerTlv.class);
    when(berTlvMock.getPrimitive(0xc0L)).thenReturn(Optional.of(primitiveBerTlvMock1));
    when(primitiveBerTlvMock1.getValueField()).thenReturn(byteArray);
    when(berTlvMock.getPrimitive(0xc1L)).thenReturn(Optional.of(primitiveBerTlvMock2));
    when(primitiveBerTlvMock2.getValueField()).thenReturn(Hex.toByteArray("000000"));
    try (final MockedStatic<BerTlv> berTlv = Mockito.mockStatic(BerTlv.class)) {
      berTlv.when(() -> BerTlv.getInstance(byteArray)).thenReturn(berTlvMock);

      // When
      assertThrows(ScenarioException.class, () -> sut.process(SESSION_ID, scenarioResult));

      // Then
      Mockito.verify(sessionAccessorMock, Mockito.never())
          .storeCommunicationMode(anyString(), any());
    }
  }

  @Test
  void processWithUnsupportedVersionOfContentThrowsException() {
    // Given
    final var resultStep = new ScenarioResultStep("description", "9000", "020000".getBytes());
    final var resultStep2 = new ScenarioResultStep("description2", "6985", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));

    when(scenarioResultFinderMock.find(
            SESSION_ID, scenarioResult.scenarioResultSteps(), "read-version"))
        .thenReturn(resultStep);
    final var berTlvMock = Mockito.mock(ConstructedBerTlv.class);
    final var primitiveBerTlvMock = Mockito.mock(PrimitiveBerTlv.class);
    when(berTlvMock.getPrimitive(0xc0L)).thenReturn(Optional.of(primitiveBerTlvMock));
    final var byteArray = Hex.toByteArray("unsupported");
    when(primitiveBerTlvMock.getValueField()).thenReturn(byteArray);
    try (final MockedStatic<BerTlv> berTlv = Mockito.mockStatic(BerTlv.class)) {
      berTlv.when(() -> BerTlv.getInstance("020000".getBytes())).thenReturn(berTlvMock);

      // When
      assertThrows(ScenarioException.class, () -> sut.process(SESSION_ID, scenarioResult));

      // Then
      Mockito.verify(sessionAccessorMock, Mockito.never())
          .storeCommunicationMode(anyString(), any());
    }
  }

  @Test
  void processWithG3Cards() {
    // Given
    final var resultStep = new ScenarioResultStep("description", "9000", "020000".getBytes());
    final var resultStep2 = new ScenarioResultStep("description2", "6985", "020000".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));

    when(scenarioResultFinderMock.find(
            SESSION_ID, scenarioResult.scenarioResultSteps(), "read-version"))
        .thenReturn(resultStep2);
    final var berTlvMock = Mockito.mock(ConstructedBerTlv.class);
    final var primitiveBerTlvMock1 = Mockito.mock(PrimitiveBerTlv.class);
    final var primitiveBerTlvMock2 = Mockito.mock(PrimitiveBerTlv.class);
    when(berTlvMock.getPrimitive(0xc0L)).thenReturn(Optional.of(primitiveBerTlvMock1));
    final var byteArray = Hex.toByteArray("020000");
    when(primitiveBerTlvMock1.getValueField()).thenReturn(byteArray);
    when(berTlvMock.getPrimitive(0xc1L)).thenReturn(Optional.of(primitiveBerTlvMock2));
    when(primitiveBerTlvMock2.getValueField()).thenReturn(Hex.toByteArray("050000"));
    try (final MockedStatic<BerTlv> berTlv = Mockito.mockStatic(BerTlv.class)) {
      berTlv.when(() -> BerTlv.getInstance("020000".getBytes())).thenReturn(berTlvMock);

      // When
      try {
        sut.process(SESSION_ID, scenarioResult);
      } catch (final ScenarioException e) {
        fail("Should not throw exception");
      }
      // Then
      Mockito.verify(sessionAccessorMock).storeCommunicationMode(SESSION_ID, CommunicationMode.G3);
    }
  }

  @Test
  void processWithContactLessCards() {
    // Given
    final var byteArray = Hex.toByteArray("020000");
    final var resultStep = new ScenarioResultStep("description", "9000", byteArray);
    final var resultStep2 = new ScenarioResultStep("description2", "6985", byteArray);
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));
    when(sessionAccessorMock.getCardConnectionType(SESSION_ID))
        .thenReturn(CardConnectionType.CONTACTLESS_STANDARD);
    when(scenarioResultFinderMock.find(
            SESSION_ID, scenarioResult.scenarioResultSteps(), "read-version"))
        .thenReturn(resultStep2);
    final var berTlvMock = Mockito.mock(ConstructedBerTlv.class);
    final var primitiveBerTlvMock1 = Mockito.mock(PrimitiveBerTlv.class);
    final var primitiveBerTlvMock2 = Mockito.mock(PrimitiveBerTlv.class);
    when(berTlvMock.getPrimitive(0xc0L)).thenReturn(Optional.of(primitiveBerTlvMock1));
    when(primitiveBerTlvMock1.getValueField()).thenReturn(byteArray);
    when(berTlvMock.getPrimitive(0xc1L)).thenReturn(Optional.of(primitiveBerTlvMock2));
    when(primitiveBerTlvMock2.getValueField()).thenReturn(Hex.toByteArray("040502"));
    try (final MockedStatic<BerTlv> berTlv = Mockito.mockStatic(BerTlv.class)) {
      berTlv.when(() -> BerTlv.getInstance(byteArray)).thenReturn(berTlvMock);

      // When
      sut.process(SESSION_ID, scenarioResult);

      // Then
      Mockito.verify(sessionAccessorMock)
          .storeCommunicationMode(SESSION_ID, CommunicationMode.CONTACTLESS);
    }
  }

  @Test
  void processThrowsExceptionWhenCardConnectionTypeNotFound() {
    // Given
    final var byteArray = Hex.toByteArray("020000");
    final var resultStep = new ScenarioResultStep("description", "9000", byteArray);
    final var resultStep2 = new ScenarioResultStep("description2", "6985", byteArray);
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));
    when(scenarioResultFinderMock.find(
            SESSION_ID, scenarioResult.scenarioResultSteps(), "read-version"))
        .thenReturn(resultStep2);
    when(sessionAccessorMock.getCardConnectionType(SESSION_ID))
        .thenThrow(new ScenarioException("", "", ""));
    final var berTlvMock = Mockito.mock(ConstructedBerTlv.class);
    final var primitiveBerTlvMock1 = Mockito.mock(PrimitiveBerTlv.class);
    final var primitiveBerTlvMock2 = Mockito.mock(PrimitiveBerTlv.class);
    when(berTlvMock.getPrimitive(0xc0L)).thenReturn(Optional.of(primitiveBerTlvMock1));
    when(primitiveBerTlvMock1.getValueField()).thenReturn(byteArray);
    when(berTlvMock.getPrimitive(0xc1L)).thenReturn(Optional.of(primitiveBerTlvMock2));
    when(primitiveBerTlvMock2.getValueField()).thenReturn(Hex.toByteArray("040502"));
    try (final MockedStatic<BerTlv> berTlv = Mockito.mockStatic(BerTlv.class)) {
      berTlv.when(() -> BerTlv.getInstance(byteArray)).thenReturn(berTlvMock);

      // When
      assertThrows(ScenarioException.class, () -> sut.process(SESSION_ID, scenarioResult));

      // Then
      Mockito.verify(sessionAccessorMock, Mockito.never())
          .storeCommunicationMode(anyString(), any());
    }
  }
}
