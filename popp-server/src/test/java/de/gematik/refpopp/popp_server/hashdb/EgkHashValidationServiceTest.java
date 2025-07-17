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

package de.gematik.refpopp.popp_server.hashdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.certificates.X509CertificateParser;
import de.gematik.refpopp.popp_server.model.CheckResult;
import de.gematik.refpopp.popp_server.model.EgkEntry;
import de.gematik.refpopp.popp_server.model.EgkEntryState;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

class EgkHashValidationServiceTest {
  private X509CertificateParser x509CertificateParserMock;
  private EgkEntryPersistenceService egkEntryPersistenceServiceMock;
  private static final String SESSION_ID = "sessionId";

  private EgkHashValidationService sut;

  @BeforeEach
  void setUp() {
    x509CertificateParserMock = mock(X509CertificateParser.class);
    egkEntryPersistenceServiceMock = mock(EgkEntryPersistenceService.class);
    sut = new EgkHashValidationService(x509CertificateParserMock, egkEntryPersistenceServiceMock);
  }

  @Test
  void handleCertificatePairForContactWithUnknownCertificatesReturnsUnknown() {
    // given
    final byte[] cvc = "test-cvc".getBytes();
    final byte[] aut = "test-aut".getBytes();
    final var x509CertificateMock = mock(X509Certificate.class);
    when(x509CertificateParserMock.parse(any(), eq("sessionId"))).thenReturn(x509CertificateMock);
    when(x509CertificateMock.getNotAfter())
        .thenReturn(new Date(System.currentTimeMillis() + 1000000L));
    when(egkEntryPersistenceServiceMock.findByCvcAndAutHash(any(), any()))
        .thenReturn(Optional.empty());
    when(egkEntryPersistenceServiceMock.process(any(), eq(SESSION_ID)))
        .thenReturn(CheckResult.UNKNOWN);

    // when
    final var result = sut.validateAndProcess(cvc, aut, CommunicationMode.CONTACT, SESSION_ID);

    // then
    assertThat(result).isEqualTo(CheckResult.UNKNOWN);
    verify(egkEntryPersistenceServiceMock).findByCvcAndAutHash(any(), any());
    verify(egkEntryPersistenceServiceMock).process(any(), eq(SESSION_ID));
  }

  @ParameterizedTest(name = "{index} => communicationMode={0}")
  @org.junit.jupiter.params.provider.EnumSource(
      value = CommunicationMode.class,
      names = {"CONTACTLESS", "CONTACT"})
  void handleCertificatePairWithKnownCertificates(final CommunicationMode communicationMode) {
    // given
    final byte[] cvc = "test-cvc".getBytes();
    final byte[] aut = "test-aut".getBytes();
    final var x509CertificateMock = mock(X509Certificate.class);
    when(x509CertificateParserMock.parse(aut, "sessionId")).thenReturn(x509CertificateMock);
    when(x509CertificateMock.getNotAfter())
        .thenReturn(new Date(System.currentTimeMillis() + 1000000L));

    final var egkEntry = new EgkEntry(cvc, aut, EgkEntryState.IMPORTED, LocalDateTime.now());
    when(egkEntryPersistenceServiceMock.findByCvcAndAutHash(any(), any()))
        .thenReturn(Optional.of(egkEntry));

    // when
    final var result = sut.validateAndProcess(cvc, aut, communicationMode, SESSION_ID);

    // then
    assertThat(result).isEqualTo(CheckResult.MATCH);
    verify(egkEntryPersistenceServiceMock).findByCvcAndAutHash(any(), any());
  }

  @ParameterizedTest(name = "{index} => communicationMode={0}")
  @org.junit.jupiter.params.provider.EnumSource(
      value = CommunicationMode.class,
      names = {"CONTACTLESS", "CONTACT"})
  void handleCertificatePairWithKnownBlockedCertificates() {
    // given
    final byte[] cvc = "test-cvc".getBytes();
    final byte[] aut = "test-aut".getBytes();
    final var x509CertificateMock = mock(X509Certificate.class);
    when(x509CertificateParserMock.parse(aut, "sessionId")).thenReturn(x509CertificateMock);
    when(x509CertificateMock.getNotAfter())
        .thenReturn(new Date(System.currentTimeMillis() + 1000000L));

    final var blockedEntry = new EgkEntry(cvc, aut, EgkEntryState.BLOCKED, LocalDateTime.now());
    when(egkEntryPersistenceServiceMock.findByCvcAndAutHash(any(), any()))
        .thenReturn(Optional.of(blockedEntry));

    // when
    final var result = sut.validateAndProcess(cvc, aut, CommunicationMode.CONTACT, SESSION_ID);

    // then
    assertThat(result).isEqualTo(CheckResult.BLOCKED);
    verify(egkEntryPersistenceServiceMock).findByCvcAndAutHash(any(), any());
  }

  @Test
  void handleCertificatePairForContactWithUnpairedCertificates() {
    // given
    final byte[] cvc = "test-cvc".getBytes();
    final byte[] aut = "test-aut".getBytes();
    final var x509CertificateMock = mock(X509Certificate.class);
    when(x509CertificateParserMock.parse(any(), anyString())).thenReturn(x509CertificateMock);
    when(x509CertificateMock.getNotAfter())
        .thenReturn(new Date(System.currentTimeMillis() + 1000000L));
    when(egkEntryPersistenceServiceMock.findByCvcAndAutHash(any(), any()))
        .thenReturn(Optional.empty());

    // when
    sut.validateAndProcess(cvc, aut, CommunicationMode.CONTACT, SESSION_ID);

    // then
    verify(egkEntryPersistenceServiceMock).findByCvcAndAutHash(any(), any());
    final var argumentCapture = org.mockito.ArgumentCaptor.forClass(EgkTransferEntry.class);
    verify(egkEntryPersistenceServiceMock).process(argumentCapture.capture(), eq(SESSION_ID));
    assertThat(argumentCapture.getValue().getCommunicationMode())
        .isEqualTo(CommunicationMode.CONTACT);
  }

  @Test
  void handleCertificatePairForContactlessWithUnknownCertificates() {
    // given
    final byte[] cvc = "test-cvc".getBytes();
    final byte[] aut = "test-aut".getBytes();
    final var x509CertificateMock = mock(X509Certificate.class);

    when(x509CertificateParserMock.parse(any(), anyString())).thenReturn(x509CertificateMock);
    when(x509CertificateMock.getNotAfter())
        .thenReturn(new Date(System.currentTimeMillis() + 1000000L));
    when(egkEntryPersistenceServiceMock.findByCvcAndAutHash(any(), any()))
        .thenReturn(Optional.empty());

    // when
    sut.validateAndProcess(cvc, aut, CommunicationMode.CONTACTLESS, SESSION_ID);

    // then
    verify(egkEntryPersistenceServiceMock).findByCvcAndAutHash(any(), any());
    final var argumentCapture = org.mockito.ArgumentCaptor.forClass(EgkTransferEntry.class);
    verify(egkEntryPersistenceServiceMock).process(argumentCapture.capture(), eq(SESSION_ID));
    assertThat(argumentCapture.getValue().getCommunicationMode())
        .isEqualTo(CommunicationMode.CONTACTLESS);
  }

  @Test
  void handleCertificateHashPairThrowsExceptionWhenNoHashAlgorithmFound() {
    // given
    final byte[] cvc = "test-cvc".getBytes();
    final byte[] aut = "test-aut".getBytes();
    when(egkEntryPersistenceServiceMock.findByCvcAndAutHash(any(), any()))
        .thenReturn(Optional.empty());
    final var x509CertificateMock = mock(X509Certificate.class);
    when(x509CertificateParserMock.parse(aut, "sessionId")).thenReturn(x509CertificateMock);
    when(x509CertificateMock.getNotAfter())
        .thenReturn(new Date(System.currentTimeMillis() + 1000000L));

    try (final var messageDigestMockedStatic = mockStatic(MessageDigest.class)) {
      messageDigestMockedStatic
          .when(() -> MessageDigest.getInstance(any()))
          .thenThrow(new NoSuchAlgorithmException("algorithm not found"));

      // when
      assertThatThrownBy(
              () -> sut.validateAndProcess(cvc, aut, CommunicationMode.CONTACT, SESSION_ID))
          .isInstanceOf(ScenarioException.class)
          .hasMessageContaining("algorithm not found");

      // then
      verify(egkEntryPersistenceServiceMock, never()).findByCvcAndAutHash(any(), any());
    }
  }
}
