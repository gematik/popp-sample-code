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

import de.gematik.poppcommons.api.exceptions.PrivateKeyParserException;
import de.gematik.smartcards.crypto.EafiElcPrkFormat;
import de.gematik.smartcards.crypto.EcPrivateKeyImpl;
import de.gematik.smartcards.tlv.BerTlv;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PrivateKeyParser {

  public EcPrivateKeyImpl parse(final ClassPathResource privateKeyResource) {
    try {
      final var inputStream = privateKeyResource.getInputStream();
      final byte[] bytes = inputStream.readAllBytes();
      final var berTlv = BerTlv.getInstance(bytes);
      return new EcPrivateKeyImpl(berTlv, EafiElcPrkFormat.PKCS8);
    } catch (final IOException e) {
      throw new PrivateKeyParserException(
          "serverSessionId", "Failed to parse private key", "errorCode");
    }
  }
}
