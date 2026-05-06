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

package de.gematik.refpopp.popp_server.scenario.contactbased.readcvc;

import de.gematik.openhealth.healthcard.CardDataException;
import de.gematik.openhealth.healthcard.Openhealth_healthcardKt;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KeyIdentifierExtractor {

  private static final byte[] EGK_AID = HexFormat.of().parseHex("d2760001448000");
  private static final HexFormat HEX_FORMAT = HexFormat.of();

  public Set<String> extract(final byte[] data) {
    try (final var listPublicKeys = Openhealth_healthcardKt.parseListPublicKeys(data)) {
      return listPublicKeys.keyReferencesForApplicationIdentifier(EGK_AID).stream()
          .map(HEX_FORMAT::formatHex)
          .collect(Collectors.toUnmodifiableSet());
    } catch (final CardDataException e) {
      throw new IllegalArgumentException("Invalid LIST PUBLIC KEY response", e);
    }
  }
}
