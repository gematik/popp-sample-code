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

package de.gematik.refpopp.popp_server.scenario.common.x509;

import lombok.Getter;

@Getter
public final class X509Data {

  private final String version;
  private final String serialNumber;
  private final byte[] signature;
  private final String issuer;
  private final String validity;
  private final String signatureAlgorithm;
  private final Subject subject;

  X509Data(
      final String version,
      final String serialNumber,
      final byte[] signature,
      final String issuer,
      final String validity,
      final String signatureAlgorithm,
      final Subject subject) {
    this.version = version;
    this.serialNumber = serialNumber;
    this.signature = signature;
    this.issuer = issuer;
    this.validity = validity;
    this.signatureAlgorithm = signatureAlgorithm;
    this.subject = subject;
  }

  public record Subject(
      String commonName,
      String title,
      String givenName,
      String surname,
      String kvNr,
      String ikNr,
      String organizationName,
      String countryName) {}
}
