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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.certificates.X509CertificateParser;
import de.gematik.refpopp.popp_server.model.CheckResult;
import de.gematik.refpopp.popp_server.model.EgkEntry;
import de.gematik.refpopp.popp_server.model.EgkEntryState;
import de.gematik.refpopp.popp_server.repository.CertHashRepository;
import de.gematik.refpopp.popp_server.scenario.BaseIntegrationTest;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class EgkHashDbServiceIT extends BaseIntegrationTest {

  @Autowired private EgkHashValidationService egkHashValidationService;
  @Autowired private CertHashRepository certHashRepository;

  @MockitoBean private X509CertificateParser x509CertificateParserMock;

  @BeforeEach
  void setUp() {
    certHashRepository.deleteAll();
  }

  @Test
  void handleCertificatePairWithUnknownCertificatesReturnsUnknown() {
    // given
    final byte[] cvc = "test-cvc".getBytes();
    final byte[] aut = "test-aut".getBytes();
    final var x509CertificateMock = mock(X509Certificate.class);
    when(x509CertificateParserMock.parse(any(), anyString())).thenReturn(x509CertificateMock);
    when(x509CertificateMock.getNotAfter())
        .thenReturn(new Date(System.currentTimeMillis() + 1000000L));

    // when
    final var result =
        egkHashValidationService.validateAndProcess(
            cvc, aut, CommunicationMode.CONTACT, "sessionId");

    // then
    assertThat(result).isEqualTo(CheckResult.UNKNOWN);
    assertThat(certHashRepository.count()).isEqualTo(1);
  }

  @Test
  void handleCertificatePairWithKnownCertificatesReturnsMatch() throws NoSuchAlgorithmException {
    // given
    final byte[] cvc = "test-cvc".getBytes();
    final byte[] aut = "test-aut".getBytes();
    final var md = MessageDigest.getInstance("SHA-256");
    final var cvcHash = md.digest(cvc);
    final var autHash = md.digest(aut);
    certHashRepository.save(
        new EgkEntry(
            cvcHash, autHash, EgkEntryState.IMPORTED, LocalDateTime.now().plusMinutes(100)));

    // when
    final var result =
        egkHashValidationService.validateAndProcess(
            cvc, aut, CommunicationMode.CONTACT, "sessionId");

    // then
    assertThat(result).isEqualTo(CheckResult.MATCH);
    assertThat(certHashRepository.count()).isEqualTo(1);
  }

  @Test
  void handleCertificatePairWithUnpairedCvcReturnsMismatch() throws NoSuchAlgorithmException {
    // given
    final byte[] cvc = "test-cvc".getBytes();
    final byte[] aut = "test-aut".getBytes();
    final var x509CertificateMock = mock(X509Certificate.class);
    when(x509CertificateParserMock.parse(any(), anyString())).thenReturn(x509CertificateMock);
    final var date = System.currentTimeMillis() + 1000000L;
    when(x509CertificateMock.getNotAfter()).thenReturn(new Date(date));
    final var md = MessageDigest.getInstance("SHA-256");
    final var cvcHash = md.digest(cvc);
    final var autHash = md.digest(aut);
    certHashRepository.save(
        new EgkEntry(
            cvcHash,
            "otherOut".getBytes(),
            EgkEntryState.IMPORTED,
            LocalDateTime.now().plusMinutes(10)));

    // when
    final var result =
        egkHashValidationService.validateAndProcess(
            cvc, aut, CommunicationMode.CONTACT, "sessionId");

    // then
    assertThat(result).isEqualTo(CheckResult.MISMATCH);
    assertThat(certHashRepository.count()).isEqualTo(2);
    final var byCvcHashAndAutHash = certHashRepository.findByCvcHashAndAutHash(cvcHash, autHash);
    assertThat(byCvcHashAndAutHash).isPresent();
    final var egkEntry = byCvcHashAndAutHash.get();
    assertThat(egkEntry.getState()).isEqualTo(EgkEntryState.BLOCKED);
    final var byCvcHash = certHashRepository.findByCvcHash(cvcHash);
    assertThat(byCvcHash).isNotEmpty();
    byCvcHash.forEach(entry -> assertThat(entry.getState()).isEqualTo(EgkEntryState.BLOCKED));
  }
}
