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

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.smartcards.crypto.EcPrivateKeyImpl;
import de.gematik.smartcards.g2icc.cvc.Cvc;
import de.gematik.smartcards.g2icc.cvc.TrustCenter;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.cert.X509Certificate;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CertificateProviderService {

  @Value("${certificates.identities-location}")
  private String identitiesLocation;

  @Value("${certificates.root}")
  private ClassPathResource rootCertificateResource;

  @Value("${certificates.cvc-certificate}")
  private ClassPathResource cvcSubCertificateResource;

  @Value("${certificates.cvc-end-entity}")
  private ClassPathResource cvcEndEntityCertificateResource;

  @Value("${certificates.connector-keystore}")
  private ClassPathResource connectorKeyStoreResource;

  @Value("${certificates.connector-keystore-password}")
  private String connectorKeyStorePassword;

  @Value("${certificates.popp-keystore}")
  private ClassPathResource poppKeyStoreResource;

  @Value("${certificates.popp-keystore-password}")
  private String poppKeyStorePassword;

  @Value("${certificates.cvc-popp-service-private-key}")
  private ClassPathResource cvcPoppServicePrivateKeyResource;

  @Getter private X509Certificate rootCertificate;
  @Getter private EcPrivateKeyImpl cvcPoppServicePrk;
  @Getter private Cvc cvcSubCertificate;
  @Getter private Cvc cvEndEntityCertificate;
  @Getter private KeyStoreData keyStoreDataConnector;
  @Getter private KeyStoreData keyStoreDataPoppToken;

  private final X509CertificateParser x509CertificateParser;
  private final CVCertificateParser cvCertificateParser;
  private final PrivateKeyParser privateKeyParser;
  private final KeyStoreService keyStoreService;
  private final ResourceLoader resourceLoader;

  public CertificateProviderService(
      final X509CertificateParser x509CertificateParser,
      final CVCertificateParser cvCertificateParser,
      final PrivateKeyParser privateKeyParser,
      final KeyStoreService keyStoreService,
      final ResourceLoader resourceLoader) {
    this.x509CertificateParser = x509CertificateParser;
    this.cvCertificateParser = cvCertificateParser;
    this.privateKeyParser = privateKeyParser;
    this.keyStoreService = keyStoreService;
    this.resourceLoader = resourceLoader;
  }

  @PostConstruct
  void loadCertificates() {
    log.debug("| Entering loadCertificates");

    this.rootCertificate = x509CertificateParser.parse(rootCertificateResource);

    final var identitiesPath = resolveIdentitiesPath();
    log.info("| Loading PKI_CVC from {}", identitiesPath.pkiCvcPath);
    TrustCenter.initializeCache(identitiesPath.pkiCvcPath());
    this.cvcSubCertificate = cvCertificateParser.parse(cvcSubCertificateResource);
    this.cvEndEntityCertificate = cvCertificateParser.parse(cvcEndEntityCertificateResource);

    this.cvcPoppServicePrk = privateKeyParser.parse(cvcPoppServicePrivateKeyResource);
    this.keyStoreDataConnector =
        keyStoreService.getConnectorKeyStoreData(
            connectorKeyStoreResource, connectorKeyStorePassword);
    this.keyStoreDataPoppToken =
        keyStoreService.getPoppKeyStoreData(poppKeyStoreResource, poppKeyStorePassword);

    log.debug("| Exiting loadCertificates");
  }

  /**
   * Needed, because TrustCenter.initializeCache() expects a file system path. Resolves the
   * identities path and loads the PKI_CVC file based on the configured identities location. If the
   * identities resource is located on the file system, it loads the PKI_CVC file and identities
   * directory directly. Otherwise, it creates a temporary directory to resolve and copy the
   * required PKI_CVC resources from the classpath.
   *
   * @return an instance of {@code IdentitiesPkiCvcPath} containing the resolved PKI_CVC file path
   *     and the identities directory path.
   * @throws ScenarioException if an error occurs while loading or processing the identities or
   *     PKI_CVC resources.
   */
  private IdentitiesPkiCvcPath resolveIdentitiesPath() {
    final var identitiesRoot = resourceLoader.getResource(identitiesLocation);
    final var pkiLocation =
        identitiesLocation.endsWith("/")
            ? identitiesLocation + "PKI_CVC.G2"
            : identitiesLocation + "/PKI_CVC.G2";
    final Resource pkiResource = resourceLoader.getResource(pkiLocation);

    try {
      final var url = identitiesRoot.getURL();
      if ("file".equals(url.getProtocol())) {
        final var identitiesDir = Paths.get(url.toURI());
        final var pkiCvcFile = pkiResource.getFile().toPath();
        return new IdentitiesPkiCvcPath(pkiCvcFile, identitiesDir);
      } else {
        final var tempFolder = createSecureTempDir();
        log.info("| tempFolder: {}", tempFolder);
        final var pkiCvcPath = tempFolder.resolve("identities/PKI_CVC.G2");
        final PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();
        final Resource[] resources =
            resolver.getResources("classpath:" + "identities/PKI_CVC.G2" + "/**");
        for (final Resource resource : resources) {
          if (resource.isReadable()) {
            final String resourceURL = resource.getURL().toString();
            final int index = resourceURL.indexOf("identities/PKI_CVC.G2");
            if (index < 0) {
              continue;
            }
            final String relativePath = resourceURL.substring(index);

            final File targetFile = new File(tempFolder.toFile(), relativePath);
            targetFile.getParentFile().mkdirs();

            try (final InputStream is = resource.getInputStream()) {
              Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
          }
        }
        final var identitiesPath = tempFolder.resolve("identities");
        return new IdentitiesPkiCvcPath(pkiCvcPath, identitiesPath);
      }
    } catch (final IOException | URISyntaxException e) {
      throw new ScenarioException(
          "| N/A",
          "Unable to load PKI_CVC from " + pkiLocation + ": " + e.getMessage(),
          "errorcode");
    }
  }

  private Path createSecureTempDir() throws IOException {
    final String tmp = System.getProperty("java.io.tmpdir");
    final Path baseDir = Paths.get(tmp);

    FileAttribute<Set<PosixFilePermission>> attrs = null;
    try {
      final FileStore store = Files.getFileStore(baseDir);
      if (store.supportsFileAttributeView(PosixFileAttributeView.class)) {
        final Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwx------");
        attrs = PosixFilePermissions.asFileAttribute(perms);
      }
    } catch (final IOException ignored) {
    }

    if (attrs != null) {
      return Files.createTempDirectory(baseDir, "identities", attrs);
    } else {
      return Files.createTempDirectory(baseDir, "identities");
    }
  }

  private record IdentitiesPkiCvcPath(Path pkiCvcPath, Path identitiesPath) {}
}
