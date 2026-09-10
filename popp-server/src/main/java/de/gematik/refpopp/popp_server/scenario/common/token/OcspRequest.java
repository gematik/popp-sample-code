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

package de.gematik.refpopp.popp_server.scenario.common.token;

import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.Optional;

final class OcspRequest {

  private final String sessionId;
  private final X509Certificate endEntityCertificate;
  private final X509Certificate issuerCertificate;

  private OcspRequest(
      final String sessionId,
      final X509Certificate endEntityCertificate,
      final X509Certificate issuerCertificate) {
    this.sessionId = Objects.requireNonNull(sessionId);
    this.endEntityCertificate = Objects.requireNonNull(endEntityCertificate);
    this.issuerCertificate = issuerCertificate;
  }

  static OcspRequest withoutIssuer(
      final String sessionId, final X509Certificate endEntityCertificate) {
    return new OcspRequest(sessionId, endEntityCertificate, null);
  }

  static OcspRequest withIssuer(
      final String sessionId,
      final X509Certificate endEntityCertificate,
      final X509Certificate issuerCertificate) {
    return new OcspRequest(
        sessionId, endEntityCertificate, Objects.requireNonNull(issuerCertificate));
  }

  String sessionId() {
    return sessionId;
  }

  X509Certificate endEntityCertificate() {
    return endEntityCertificate;
  }

  Optional<X509Certificate> issuerCertificate() {
    return Optional.ofNullable(issuerCertificate);
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof OcspRequest other)) {
      return false;
    }
    return Objects.equals(sessionId, other.sessionId)
        && Objects.equals(endEntityCertificate, other.endEntityCertificate)
        && Objects.equals(issuerCertificate, other.issuerCertificate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sessionId, endEntityCertificate, issuerCertificate);
  }
}
