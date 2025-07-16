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

package de.gematik.refpopp.popp_server.scenario.contactbased.readcvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.smartcards.g2icc.cos.SecureMessagingConverterSoftware;
import de.gematik.smartcards.g2icc.cvc.Cvc;
import de.gematik.smartcards.g2icc.cvc.Cvc.SignatureStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CvcChainBuilderTest {

  private CvcChainBuilder sut;
  private SecureMessagingConverterSoftware secureMessagingConverterSoftwareMock;
  private ScenarioResultFinder scenarioResultFinderMock;
  private KeyIdentifierExtractor keyIdentifierExtractorMock;

  @BeforeEach
  void setUp() {
    secureMessagingConverterSoftwareMock = mock(SecureMessagingConverterSoftware.class);
    scenarioResultFinderMock = mock(ScenarioResultFinder.class);
    keyIdentifierExtractorMock = mock(KeyIdentifierExtractor.class);
    sut =
        new CvcChainBuilder(
            secureMessagingConverterSoftwareMock,
            scenarioResultFinderMock,
            keyIdentifierExtractorMock);
    ReflectionTestUtils.setField(
        sut, "retrievePublicKeyIdsStepName", "retrieve-public-key-identifiers");
  }

  @Test
  void buildCvcChainReturnsEmptyList() {
    // given
    final var sessionId = "sessionId";

    final var resultStep = new ScenarioResultStep("description", "9000", "abcdef".getBytes());
    final var resultStep2 = new ScenarioResultStep("description2", "6985", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));
    final var cvcMock = mock(Cvc.class);
    when(scenarioResultFinderMock.find(anyString(), anyList(), anyString()))
        .thenReturn(resultStep2);
    when(keyIdentifierExtractorMock.extract("result".getBytes())).thenReturn(Set.of());
    when(cvcMock.getSignatureStatus()).thenReturn(SignatureStatus.SIGNATURE_VALID);
    when(secureMessagingConverterSoftwareMock.importCvc(any())).thenReturn(List.of());

    // when
    final var result = sut.build(sessionId, scenarioResult, cvcMock);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void buildReturnsEmptyWhenKeyIdentifiersContainsChr() {
    // given
    final var sessionId = "sessionId";
    final var resultStep = new ScenarioResultStep("description", "9000", "result".getBytes());
    final var resultStep2 = new ScenarioResultStep("description2", "6985", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));

    final var cvcMock = mock(Cvc.class);

    when(scenarioResultFinderMock.find(anyString(), anyList(), anyString())).thenReturn(resultStep);
    when(keyIdentifierExtractorMock.extract(any())).thenReturn(Set.of("chr"));
    when(secureMessagingConverterSoftwareMock.importCvc(cvcMock))
        .thenReturn(List.of(cvcMock, cvcMock));
    when(cvcMock.getChr()).thenReturn("chr");
    when(cvcMock.getSignatureStatus()).thenReturn(SignatureStatus.SIGNATURE_VALID);

    // when
    final var result = sut.build(sessionId, scenarioResult, cvcMock);

    // then
    assertThat(result).isEmpty();
    verify(scenarioResultFinderMock)
        .find(sessionId, scenarioResult.scenarioResultSteps(), "retrieve-public-key-identifiers");
    verify(keyIdentifierExtractorMock).extract("result".getBytes());
    verify(secureMessagingConverterSoftwareMock).importCvc(cvcMock);
  }

  @Test
  void buildReturnsCvcChain() {
    // given
    final var sessionId = "sessionId";
    final var resultStep = new ScenarioResultStep("description", "9000", "result".getBytes());
    final var resultStep2 = new ScenarioResultStep("description2", "6985", "abcdef".getBytes());
    final var scenarioResult = new ScenarioResult("scenario", List.of(resultStep, resultStep2));

    final var cvcMock = mock(Cvc.class);

    when(scenarioResultFinderMock.find(anyString(), anyList(), anyString())).thenReturn(resultStep);
    when(keyIdentifierExtractorMock.extract("result".getBytes())).thenReturn(Set.of());
    when(secureMessagingConverterSoftwareMock.importCvc(cvcMock))
        .thenReturn(List.of(cvcMock, cvcMock));
    when(cvcMock.getChr()).thenReturn("chr");
    when(cvcMock.getSignatureStatus()).thenReturn(SignatureStatus.SIGNATURE_VALID);

    // when
    final var result = sut.build(sessionId, scenarioResult, cvcMock);

    // then
    assertThat(result).isNotEmpty();
    verify(scenarioResultFinderMock)
        .find(sessionId, scenarioResult.scenarioResultSteps(), "retrieve-public-key-identifiers");
    verify(keyIdentifierExtractorMock).extract("result".getBytes());
    verify(secureMessagingConverterSoftwareMock).importCvc(cvcMock);
  }
}
