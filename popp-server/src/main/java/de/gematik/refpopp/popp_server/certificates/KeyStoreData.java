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

import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class KeyStoreData {

  private final ECPrivateKey privateKey;
  private final List<X509Certificate> certificateChain;
  private final X509Certificate issuerCertificate;

  private KeyStoreData(
      final ECPrivateKey privateKey,
      final List<X509Certificate> certificateChain,
      final X509Certificate issuerCertificate) {
    this.privateKey = Objects.requireNonNull(privateKey, "privateKey must not be null");
    this.certificateChain = List.copyOf(certificateChain);
    if (this.certificateChain.isEmpty()) {
      throw new IllegalArgumentException("certificateChain must not be empty");
    }
    this.issuerCertificate = issuerCertificate;
  }

  public static KeyStoreData withoutIssuer(
      final ECPrivateKey privateKey, final List<X509Certificate> certificateChain) {
    return new KeyStoreData(privateKey, certificateChain, null);
  }

  public static KeyStoreData withIssuer(
      final ECPrivateKey privateKey,
      final List<X509Certificate> certificateChain,
      final X509Certificate issuerCertificate) {
    return new KeyStoreData(
        privateKey,
        certificateChain,
        Objects.requireNonNull(issuerCertificate, "issuerCertificate must not be null"));
  }

  public ECPrivateKey privateKey() {
    return privateKey;
  }

  public List<X509Certificate> certificateChain() {
    return certificateChain;
  }

  public X509Certificate certificate() {
    return certificateChain.getFirst();
  }

  public Optional<X509Certificate> issuerCertificate() {
    return Optional.ofNullable(issuerCertificate);
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof KeyStoreData other)) {
      return false;
    }
    return Objects.equals(privateKey, other.privateKey)
        && Objects.equals(certificateChain, other.certificateChain)
        && Objects.equals(issuerCertificate, other.issuerCertificate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(privateKey, certificateChain, issuerCertificate);
  }
}
