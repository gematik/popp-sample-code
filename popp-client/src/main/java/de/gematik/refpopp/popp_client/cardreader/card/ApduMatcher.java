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

import org.springframework.stereotype.Component;

@Component
public final class ApduMatcher {

  boolean isMutualAuthenticationStep1(
      final String normalizedCommandApdu, final String step1Prefix) {
    return normalizedCommandApdu.startsWith(step1Prefix);
  }

  boolean isMutualAuthenticationStep2(
      final String normalizedCommandApdu, final String step2Prefix) {
    return normalizedCommandApdu.startsWith(step2Prefix);
  }

  boolean isReadEndEntityCvCertificate(
      final String normalizedCommandApdu, final String readEndEntityPrefix) {
    return normalizedCommandApdu.startsWith(readEndEntityPrefix);
  }

  boolean isReadEfCChAutE256(
      final String normalizedCommandApdu, final String readPrefix, final String secureReadPrefix) {
    return normalizedCommandApdu.startsWith(readPrefix)
        || normalizedCommandApdu.startsWith(secureReadPrefix);
  }

  boolean isInternalAuthenticate(
      final String normalizedCommandApdu, final String internalAuthenticatePrefix) {
    return normalizedCommandApdu.startsWith(internalAuthenticatePrefix);
  }

  boolean isSecureMessagingProtectedCommand(
      final String normalizedCommandApdu,
      final String secureSelectDfEsignPrefix,
      final String secureReadEfPrefix) {
    return normalizedCommandApdu.startsWith(secureSelectDfEsignPrefix)
        || normalizedCommandApdu.startsWith(secureReadEfPrefix);
  }
}
