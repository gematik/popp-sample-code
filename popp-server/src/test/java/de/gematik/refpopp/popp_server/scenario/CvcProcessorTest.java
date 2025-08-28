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

package de.gematik.refpopp.popp_server.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.certificates.CvcFactory;
import de.gematik.refpopp.popp_server.scenario.common.cvc.CvcProcessor;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult.ScenarioResultStep;
import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResultFinder;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionAccessor;
import de.gematik.smartcards.g2icc.cvc.CertificateDate;
import de.gematik.smartcards.g2icc.cvc.Cvc;
import de.gematik.smartcards.g2icc.cvc.Cvc.SignatureStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CvcProcessorTest {

  private CvcProcessor sut;
  private CvcFactory cvcFactoryMock;
  private SessionAccessor sessionAccessorMock;

  @BeforeEach
  void setUp() {
    cvcFactoryMock = mock(CvcFactory.class);
    final var scenarioResultFinder = new ScenarioResultFinder();
    sessionAccessorMock = mock(SessionAccessor.class);
    sut = new CvcProcessor(cvcFactoryMock, scenarioResultFinder, sessionAccessorMock);
  }

  @Test
  void createAndValidateCvcSuccess() {
    // given
    final var sessionId = "sessionId";
    final var scenarioResultStep = new ScenarioResultStep("step1", "9000", "data".getBytes());
    final var scenarioResult = new ScenarioResult("firstResult", List.of(scenarioResultStep));
    final var stepName = "step1";
    final var cvcMock = mock(Cvc.class);
    when(cvcFactoryMock.create(any())).thenReturn(cvcMock);
    final var certDateMoc = mock(CertificateDate.class);
    when(cvcMock.getCxd()).thenReturn(certDateMoc);
    when(certDateMoc.getDate()).thenReturn(LocalDate.now().plusYears(1));
    when(cvcMock.getSignatureStatus()).thenReturn(SignatureStatus.SIGNATURE_VALID);

    // when
    final var cvc = sut.createAndValidateCvc(sessionId, scenarioResult, stepName);

    // then
    assertThat(cvc).isNotNull();
    verify(cvcFactoryMock).create("data".getBytes());
    verify(sessionAccessorMock).storeCvc(sessionId, "data".getBytes());
  }

  @Test
  void createAndValidateCvcCaSuccess() {
    // given
    final var sessionId = "sessionId";
    final var scenarioResultStep = new ScenarioResultStep("step1", "9000", "data".getBytes());
    final var scenarioResult = new ScenarioResult("firstResult", List.of(scenarioResultStep));
    final var stepName = "step1";
    final var cvcMock = mock(Cvc.class);
    when(cvcFactoryMock.create(any())).thenReturn(cvcMock);
    final var certDateMoc = mock(CertificateDate.class);
    when(cvcMock.getCxd()).thenReturn(certDateMoc);
    when(certDateMoc.getDate()).thenReturn(LocalDate.now().plusYears(1));
    when(cvcMock.getSignatureStatus()).thenReturn(SignatureStatus.SIGNATURE_VALID);

    // when
    final var cvc = sut.createAndValidateCvcCa(sessionId, scenarioResult, stepName);

    // then
    assertThat(cvc).isNotNull();
    verify(cvcFactoryMock).create("data".getBytes());
    verify(sessionAccessorMock).storeCvcCA(sessionId, "data".getBytes());
  }

  @ParameterizedTest
  @CsvSource({"true, 'End-Entity CVC is expired'", "false, 'SubCA CVC is expired'"})
  void createAndValidateCvcFailedWithExpiredDate(
      final boolean isEndEntity, final String exceptionMessage) {
    // given
    final var sessionId = "sessionId";
    final var scenarioResultStep = new ScenarioResultStep("step1", "9000", "data".getBytes());
    final var scenarioResult = new ScenarioResult("firstResult", List.of(scenarioResultStep));
    final var stepName = "step1";
    final var cvcMock = mock(Cvc.class);
    when(cvcFactoryMock.create(any())).thenReturn(cvcMock);
    final var certDateMoc = mock(CertificateDate.class);
    when(cvcMock.getCxd()).thenReturn(certDateMoc);
    when(certDateMoc.getDate()).thenReturn(LocalDate.now().minusMonths(1));
    when(cvcMock.isEndEntity()).thenReturn(isEndEntity);

    // when
    assertThatThrownBy(() -> sut.createAndValidateCvc(sessionId, scenarioResult, stepName))
        .isInstanceOf(ScenarioException.class)
        .hasMessage(exceptionMessage);

    // then
    verify(cvcFactoryMock).create("data".getBytes());
  }

  @ParameterizedTest
  @CsvSource({
    "true, 'Signature of End-Entity CVC is not valid'",
    "false, 'Signature of SubCA CVC is not valid'"
  })
  void createAndValidateCvcFailedWithWrongSignature(
      final boolean isEndEntity, final String exceptionMessage) {
    // given
    final var sessionId = "sessionId";
    final var scenarioResultStep = new ScenarioResultStep("step1", "9000", "data".getBytes());
    final var scenarioResult = new ScenarioResult("firstResult", List.of(scenarioResultStep));
    final var stepName = "step1";
    final var cvcMock = mock(Cvc.class);
    when(cvcFactoryMock.create(any())).thenReturn(cvcMock);
    final var certDateMoc = mock(CertificateDate.class);
    when(cvcMock.getCxd()).thenReturn(certDateMoc);
    when(certDateMoc.getDate()).thenReturn(LocalDate.now().plusYears(1));
    when(cvcMock.getSignatureStatus()).thenReturn(SignatureStatus.SIGNATURE_INVALID);
    when(cvcMock.isEndEntity()).thenReturn(isEndEntity);

    // when
    assertThatThrownBy(() -> sut.createAndValidateCvc(sessionId, scenarioResult, stepName))
        .isInstanceOf(ScenarioException.class)
        .hasMessage(exceptionMessage);

    // then
    verify(cvcFactoryMock).create("data".getBytes());
  }
}
