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

public final class VirtualCardSessionState {
  private SecureMessagingSession secureMessagingSession;
  private byte[] pendingCardEphemeralPrivateKey;

  SecureMessagingSession getSecureMessagingSession() {
    return secureMessagingSession;
  }

  boolean hasSecureMessagingSession() {
    return secureMessagingSession != null;
  }

  void setSecureMessagingSession(final SecureMessagingSession secureMessagingSession) {
    this.secureMessagingSession = secureMessagingSession;
  }

  void clearSecureMessagingSession() {
    secureMessagingSession = null;
  }

  byte[] getPendingCardEphemeralPrivateKey() {
    return pendingCardEphemeralPrivateKey == null
        ? null
        : Arrays.copyOf(pendingCardEphemeralPrivateKey, pendingCardEphemeralPrivateKey.length);
  }

  boolean hasPendingCardEphemeralPrivateKey() {
    return pendingCardEphemeralPrivateKey != null;
  }

  void setPendingCardEphemeralPrivateKey(final byte[] pendingCardEphemeralPrivateKey) {
    this.pendingCardEphemeralPrivateKey =
        pendingCardEphemeralPrivateKey == null
            ? null
            : Arrays.copyOf(pendingCardEphemeralPrivateKey, pendingCardEphemeralPrivateKey.length);
  }

  void clearPendingCardEphemeralPrivateKey() {
    pendingCardEphemeralPrivateKey = null;
  }
}
