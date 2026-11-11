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

/** Signs CVC authentication values independently of APDU processing and card-session state. */
public interface CvcSigner {

  /**
   * Signs the supplied value directly, without hashing it.
   *
   * @param privateKey unsigned Brainpool-P256r1 private scalar
   * @param value authentication value, already including any protocol-specific suffix
   * @return the 64-byte signature {@code r || s}, with unsigned, big-endian 32-byte coordinates
   * @throws IllegalStateException if the private key is invalid or signing fails
   */
  byte[] sign(byte[] privateKey, byte[] value);
}
