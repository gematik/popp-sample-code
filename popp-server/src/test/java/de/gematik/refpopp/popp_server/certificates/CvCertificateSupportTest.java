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

package de.gematik.refpopp.popp_server.certificates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import de.gematik.openhealth.asn1.Asn1FfiException;
import de.gematik.openhealth.asn1.Asn1TagClass;
import de.gematik.openhealth.asn1.Asn1TagForm;
import de.gematik.openhealth.asn1.CvCertificate;
import de.gematik.openhealth.asn1.Openhealth_asn1Kt;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CvCertificateSupportTest {

  private CvCertificateParser parser;

  @BeforeEach
  void setUp() {
    parser = new CvCertificateParser(new CvcFactory());
  }

  @Test
  void readsReferencesAndEncodedFieldsFromEndEntityCertificate() {
    final var certificate =
        parser.parse(new ClassPathResource("certificates/cvc/80276001011699902101-cvc-flag0.crt"));

    assertThat(CvCertificateSupport.car(certificate)).startsWith("DEGXX");
    assertThat(CvCertificateSupport.chrBytes(certificate)).hasSize(12);
    assertThat(CvCertificateSupport.publicKeyBytes(certificate)).hasSize(65);
    assertThat(CvCertificateSupport.certificateValueField(certificate)).isNotEmpty();
    assertThat(CvCertificateSupport.expirationDate(certificate)).isAfter(LocalDate.of(2020, 1, 1));
    assertThat(CvCertificateSupport.isEndEntity(certificate)).isTrue();
  }

  @Test
  void detectsSubCaCertificateAsNonEndEntity() {
    final var certificate = parser.parse(new ClassPathResource("certificates/cvc/DEGXX120223.crt"));

    assertThat(CvCertificateSupport.chr(certificate)).startsWith("DEGXX");
    assertThat(CvCertificateSupport.isEndEntity(certificate)).isFalse();
  }

  @Test
  void detectsMissingAuthorizationAsNonEndEntity() {
    final var certificate = mock(CvCertificate.class, RETURNS_DEEP_STUBS);
    when(certificate.body().certificateHolderAuthorizationTemplate().relativeAuthorization())
        .thenReturn(new byte[0]);

    assertThat(CvCertificateSupport.isEndEntity(certificate)).isFalse();
  }

  @Test
  void publicKeyBytesWrapsAsn1Exception() {
    final var certificate =
        parser.parse(new ClassPathResource("certificates/cvc/80276001011699902101-cvc-flag0.crt"));
    try (final var asn1Mock = mockStatic(Openhealth_asn1Kt.class)) {
      asn1Mock
          .when(
              () ->
                  Openhealth_asn1Kt.readTaggedObjectValue(
                      org.mockito.ArgumentMatchers.any(byte[].class),
                      org.mockito.ArgumentMatchers.any(Asn1TagClass.class),
                      org.mockito.ArgumentMatchers.any(Asn1TagForm.class),
                      org.mockito.ArgumentMatchers.anyInt()))
          .thenThrow(new Asn1FfiException.Decode("invalid"));

      assertThatThrownBy(() -> CvCertificateSupport.publicKeyBytes(certificate))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Failed to read CVC public key");
    }
  }

  @Test
  void certificateValueFieldWrapsAsn1Exception() {
    final var certificate =
        parser.parse(new ClassPathResource("certificates/cvc/80276001011699902101-cvc-flag0.crt"));
    try (final var asn1Mock = mockStatic(Openhealth_asn1Kt.class)) {
      asn1Mock
          .when(
              () ->
                  Openhealth_asn1Kt.readTaggedObjectValue(
                      org.mockito.ArgumentMatchers.any(byte[].class),
                      org.mockito.ArgumentMatchers.any(Asn1TagClass.class),
                      org.mockito.ArgumentMatchers.any(Asn1TagForm.class),
                      org.mockito.ArgumentMatchers.anyInt()))
          .thenThrow(new Asn1FfiException.Decode("invalid"));

      assertThatThrownBy(() -> CvCertificateSupport.certificateValueField(certificate))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Failed to read CVC value field");
    }
  }
}
