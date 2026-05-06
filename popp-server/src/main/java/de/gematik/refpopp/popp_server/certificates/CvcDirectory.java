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
import de.gematik.poppcommons.api.exceptions.CertificateParserException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class CvcDirectory {

  private final Map<String, List<CvCertificate>> cvcByChr;

  private CvcDirectory(final Map<String, List<CvCertificate>> cvcByChr) {
    final Map<String, List<CvCertificate>> copy = new LinkedHashMap<>();
    cvcByChr.forEach((chr, certificates) -> copy.put(chr, List.copyOf(certificates)));
    this.cvcByChr = Map.copyOf(copy);
  }

  public Optional<CvCertificate> findByChr(final String chr) {
    return findAllByChr(chr).stream().findFirst();
  }

  public List<CvCertificate> findAllByChr(final String chr) {
    return cvcByChr.getOrDefault(chr, List.of());
  }

  public List<CvCertificate> all() {
    return cvcByChr.values().stream().flatMap(List::stream).toList();
  }

  static CvcDirectory load(
      final Path identitiesPath, final CvCertificateParser cvCertificateParser) {
    final var trustedPath = identitiesPath.resolve("PKI_CVC.G2").resolve("trusted");
    final Map<String, List<CvCertificate>> cvcByChr = new LinkedHashMap<>();

    try (Stream<Path> paths = Files.walk(trustedPath)) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".cvc"))
          .sorted()
          .map(cvCertificateParser::parse)
          .forEach(
              cvc ->
                  cvcByChr
                      .computeIfAbsent(CvCertificateSupport.chr(cvc), ignored -> new ArrayList<>())
                      .add(cvc));
    } catch (final IOException e) {
      throw new CertificateParserException(
          "Failed to load trusted CVC directory from " + trustedPath, "errorCode");
    }

    return new CvcDirectory(cvcByChr);
  }
}
