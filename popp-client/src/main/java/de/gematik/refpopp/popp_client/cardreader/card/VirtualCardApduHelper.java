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

import de.gematik.openhealth.healthcard.ApduException;
import de.gematik.openhealth.healthcard.CommandBuilderException;
import de.gematik.openhealth.healthcard.HealthCardCommand;
import java.util.HexFormat;

final class VirtualCardApduHelper {
  private VirtualCardApduHelper() {}

  static String buildApduHex(
      final HealthCardCommandSupplier supplier, final boolean supportsExtendedLength) {
    try (final var command = supplier.get();
        final var commandApdu = command.toApdu(supportsExtendedLength);
        final var bytes = commandApdu.toVec()) {
      return HexFormat.of().withUpperCase().formatHex(bytes.cloneAsNonzeroizingVec());
    } catch (final ApduException | CommandBuilderException e) {
      throw new IllegalStateException("Failed to build static virtual-card APDU", e);
    }
  }

  static String buildApduPrefix(
      final HealthCardCommandSupplier supplier, final boolean supportsExtendedLength) {
    return buildApduHex(supplier, supportsExtendedLength).substring(0, 8);
  }

  @FunctionalInterface
  interface HealthCardCommandSupplier {
    HealthCardCommand get() throws CommandBuilderException;
  }
}
