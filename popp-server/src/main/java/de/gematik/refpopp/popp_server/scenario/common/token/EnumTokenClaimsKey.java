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

import lombok.Getter;

@Getter
enum EnumTokenClaimsKey {
  VERSION("version"),
  ISSUER("iss"),
  IAT("iat"),
  PROOF_METHOD("proofMethod"),
  PATIENT_PROOF_TIME("patientProofTime"),
  PATIENT_ID("patientId"),
  INSURER_ID("insurerId"),
  ACTOR_ID("actorId"),
  ACTOR_PROFESSION_OID("actorProfessionOid"),
  AUTHORIZATION_DETAILS("authorization_details");

  final String keyValue;

  EnumTokenClaimsKey(final String value) {
    this.keyValue = value;
  }
}
