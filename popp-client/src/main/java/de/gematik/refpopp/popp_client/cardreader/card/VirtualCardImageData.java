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

package de.gematik.refpopp.popp_client.cardreader.card;

import java.util.Arrays;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

record VirtualCardImageData(
    String cvCertificate,
    String authCertificate,
    String subCaCvCertificate,
    String version2,
    byte[] egkAuthCvcPrivateKey) {

  VirtualCardImageData {
    egkAuthCvcPrivateKey =
        egkAuthCvcPrivateKey == null
            ? new byte[0]
            : Arrays.copyOf(egkAuthCvcPrivateKey, egkAuthCvcPrivateKey.length);
  }

  @Override
  public byte[] egkAuthCvcPrivateKey() {
    return Arrays.copyOf(egkAuthCvcPrivateKey, egkAuthCvcPrivateKey.length);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    VirtualCardImageData that = (VirtualCardImageData) o;
    return Objects.equals(version2, that.version2)
        && Objects.equals(cvCertificate, that.cvCertificate)
        && Objects.equals(authCertificate, that.authCertificate)
        && Objects.equals(subCaCvCertificate, that.subCaCvCertificate)
        && Objects.deepEquals(egkAuthCvcPrivateKey, that.egkAuthCvcPrivateKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        cvCertificate,
        authCertificate,
        subCaCvCertificate,
        version2,
        Arrays.hashCode(egkAuthCvcPrivateKey));
  }

  @Override
  public @NotNull String toString() {
    return "VirtualCardImageData{"
        + "cvCertificate='"
        + cvCertificate
        + '\''
        + ", authCertificate='"
        + authCertificate
        + '\''
        + ", subCaCvCertificate='"
        + subCaCvCertificate
        + '\''
        + ", version2='"
        + version2
        + '\''
        + '}';
  }
}
