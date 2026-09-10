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

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Optional;
import org.bouncycastle.asn1.ASN1IA5String;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.springframework.stereotype.Component;

@Component
final class OcspResponderUrlExtractor {

  Optional<String> extract(final X509Certificate certificate) throws IOException {
    final var extensionValue = certificate.getExtensionValue(Extension.authorityInfoAccess.getId());
    if (extensionValue == null) {
      return Optional.empty();
    }

    final var authorityInformationAccess =
        AuthorityInformationAccess.getInstance(
            ASN1Primitive.fromByteArray(ASN1OctetString.getInstance(extensionValue).getOctets()));
    return Arrays.stream(authorityInformationAccess.getAccessDescriptions())
        .filter(
            accessDescription ->
                AccessDescription.id_ad_ocsp.equals(accessDescription.getAccessMethod()))
        .map(AccessDescription::getAccessLocation)
        .filter(
            accessLocation -> accessLocation.getTagNo() == GeneralName.uniformResourceIdentifier)
        .map(accessLocation -> ASN1IA5String.getInstance(accessLocation.getName()).getString())
        .findFirst();
  }
}
