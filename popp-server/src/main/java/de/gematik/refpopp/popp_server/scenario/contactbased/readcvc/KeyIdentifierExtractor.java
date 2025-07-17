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

import de.gematik.smartcards.tlv.BerTlv;
import de.gematik.smartcards.tlv.ConstructedBerTlv;
import de.gematik.smartcards.utils.Hex;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KeyIdentifierExtractor {

  public Set<String> extract(final byte[] data) {
    final Set<String> keyRefs = new HashSet<>();

    final List<BerTlv> template =
        ((ConstructedBerTlv) BerTlv.getInstance(0x20, data)).getTemplate();
    template.stream()
        .filter(tlv -> 0xe0 == tlv.getTag())
        .map(tlv -> ((ConstructedBerTlv) tlv).getTemplate())
        .filter(temp -> temp.size() >= 2)
        .forEach(
            temp -> {
              final var aid = Hex.toHexDigits(temp.getFirst().getValueField());
              final var keyDo = temp.get(1);
              if (keyDo instanceof final ConstructedBerTlv kt) {
                final var keyRef = Hex.toHexDigits(kt.getTemplate().getFirst().getValueField());

                if ("d2760001448000".equals(aid)) {
                  keyRefs.add(keyRef);
                }
              }
            });

    return Collections.unmodifiableSet(keyRefs);
  }
}
