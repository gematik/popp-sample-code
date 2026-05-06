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

import de.gematik.openhealth.asn1.CvCertificate;
import de.gematik.openhealth.crypto.CryptoException;
import de.gematik.openhealth.crypto.Openhealth_cryptoKt;
import org.springframework.stereotype.Component;

@Component
public class CvcSignatureVerifier {

  public boolean verifyCvcEcdsaValueSignature(
      final CvCertificate certificate, final byte[] token, final byte[] signature) {
    try {
      return Openhealth_cryptoKt.verifyCvcEcdsaValueSignature(certificate, token, signature);
    } catch (final CryptoException e) {
      throw new IllegalStateException("CVC ECDSA verification failed", e);
    }
  }
}
