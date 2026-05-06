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

import de.gematik.openhealth.asn1.Asn1FfiException;
import de.gematik.openhealth.asn1.Asn1TagClass;
import de.gematik.openhealth.asn1.Asn1TagForm;
import de.gematik.openhealth.asn1.CvCertificate;
import de.gematik.openhealth.asn1.CvCertificateDate;
import de.gematik.openhealth.asn1.Openhealth_asn1Kt;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public final class CvCertificateSupport {

  private CvCertificateSupport() {}

  public static byte[] carBytes(final CvCertificate certificate) {
    return certificate.body().certificationAuthorityReference();
  }

  public static String car(final CvCertificate certificate) {
    return new String(carBytes(certificate), StandardCharsets.ISO_8859_1);
  }

  public static byte[] chrBytes(final CvCertificate certificate) {
    return certificate.body().certificateHolderReference();
  }

  public static String chr(final CvCertificate certificate) {
    return new String(chrBytes(certificate), StandardCharsets.ISO_8859_1);
  }

  public static byte[] publicKeyBytes(final CvCertificate certificate) {
    try {
      return Openhealth_asn1Kt.readTaggedObjectValue(
          certificate.body().publicKey().keyData(),
          Asn1TagClass.CONTEXT_SPECIFIC,
          Asn1TagForm.PRIMITIVE,
          6);
    } catch (final Asn1FfiException e) {
      throw new IllegalStateException("Failed to read CVC public key", e);
    }
  }

  public static byte[] certificateValueField(final CvCertificate certificate) {
    try {
      return Openhealth_asn1Kt.readTaggedObjectValue(
          certificate.encodedCertificateTlv(),
          Asn1TagClass.APPLICATION,
          Asn1TagForm.CONSTRUCTED,
          33);
    } catch (final Asn1FfiException e) {
      throw new IllegalStateException("Failed to read CVC value field", e);
    }
  }

  public static LocalDate expirationDate(final CvCertificate certificate) {
    final var expirationDate = certificate.body().certificateExpirationDate();
    return LocalDate.of(
        2000 + unsignedDatePart(expirationDate, "year-w2LRezQ"),
        unsignedDatePart(expirationDate, "month-w2LRezQ"),
        unsignedDatePart(expirationDate, "day-w2LRezQ"));
  }

  public static boolean isEndEntity(final CvCertificate certificate) {
    final var authorization =
        certificate.body().certificateHolderAuthorizationTemplate().relativeAuthorization();
    return authorization.length > 0 && authorization[0] == 0;
  }

  private static int unsignedDatePart(final CvCertificateDate date, final String methodName) {
    try {
      return Byte.toUnsignedInt((byte) CvCertificateDate.class.getMethod(methodName).invoke(date));
    } catch (final IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new IllegalStateException("Failed to read CVC date", e);
    }
  }
}
