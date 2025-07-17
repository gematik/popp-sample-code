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

import de.gematik.poppcommons.api.exceptions.CertificateParserException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class X509CertificateParser {

  public X509Certificate parse(final ClassPathResource resource) {
    try (final var rootInputStream = resource.getInputStream()) {
      final var certificateFactory = CertificateFactory.getInstance("X.509");
      return (X509Certificate) certificateFactory.generateCertificate(rootInputStream);
    } catch (final CertificateException | IOException e) {
      throw new CertificateParserException("Failed to parse X509 certificate", "errorCode");
    }
  }

  public X509Certificate parse(final byte[] certificate, final String sessionId) {
    try (final var inputStream = new ByteArrayInputStream(certificate)) {
      final var certificateFactory = CertificateFactory.getInstance("X.509");
      return (X509Certificate) certificateFactory.generateCertificate(inputStream);
    } catch (final CertificateException | IOException e) {
      throw new CertificateParserException(
          sessionId, "Failed to parse X509 certificate: " + e, "errorCode");
    }
  }
}
