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
import de.gematik.poppcommons.api.enums.BdeErrorCode;
import de.gematik.poppcommons.api.exceptions.CertificateParserException;
import de.gematik.poppcommons.api.exceptions.PrivateKeyLoadingException;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
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
  private static final String IDENTITIES_DIRECTORY = "identities";
  private static final String PKI_CVC_G2_DIRECTORY = IDENTITIES_DIRECTORY + "/PKI_CVC.G2";

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
  @Getter private CvcDirectory cvcDirectory;
  @Getter private CvCertificate cvcSubCertificate;
  @Getter private CvCertificate cvEndEntityCertificate;
  @Getter private KeyStoreData keyStoreDataConnector;
  @Getter private KeyStoreData keyStoreDataPoppToken;

  private final X509CertificateParser x509CertificateParser;
  private final CvCertificateParser cvCertificateParser;
  private final ConfiguredTrustedChannelIdentityValidator configuredTrustedChannelIdentityValidator;
  private final KeyStoreService keyStoreService;
  private final ResourceLoader resourceLoader;

  public CertificateProviderService(
      final X509CertificateParser x509CertificateParser,
      final CvCertificateParser cvCertificateParser,
      final ConfiguredTrustedChannelIdentityValidator configuredTrustedChannelIdentityValidator,
      final KeyStoreService keyStoreService,
      final ResourceLoader resourceLoader) {
    this.x509CertificateParser = x509CertificateParser;
    this.cvCertificateParser = cvCertificateParser;
    this.configuredTrustedChannelIdentityValidator = configuredTrustedChannelIdentityValidator;
    this.keyStoreService = keyStoreService;
    this.resourceLoader = resourceLoader;
  }

  @PostConstruct
  void loadCertificates() {
    log.debug("| Entering loadCertificates");

    this.rootCertificate = x509CertificateParser.parse(rootCertificateResource);

    final var identitiesPath = resolveIdentitiesPath();
    log.info("| Loading trusted CVC directory from {}", identitiesPath);
    this.cvcDirectory = CvcDirectory.load(identitiesPath, cvCertificateParser);
    try {
      this.cvcSubCertificate = cvCertificateParser.parse(cvcSubCertificateResource);
    } catch (final CertificateParserException e) {
      log.error("| Failed to parse CV certificate", e);
      throw new CertificateParserException(
          "Failed to parse Sub-CA-CVC certificate", BdeErrorCode.INVALID_CA_CVC, e);
    }
    try {
      this.cvEndEntityCertificate = cvCertificateParser.parse(cvcEndEntityCertificateResource);
    } catch (final CertificateParserException e) {
      log.error("| Failed to parse End-Entity-CVC certificate", e);
      throw new CertificateParserException(
          "Failed to parse End-Entity-CVC certificate", BdeErrorCode.INVALID_END_ENTITY_CVC, e);
    }
    final var cvcPoppServicePrivateKeyDer = readPrivateKeyDer(cvcPoppServicePrivateKeyResource);
    configuredTrustedChannelIdentityValidator.validate(
        cvcSubCertificate, cvEndEntityCertificate, cvcPoppServicePrivateKeyDer);

    this.keyStoreDataConnector =
        keyStoreService.getConnectorKeyStoreData(
            connectorKeyStoreResource, connectorKeyStorePassword);
    this.keyStoreDataPoppToken =
        keyStoreService.getPoppKeyStoreData(poppKeyStoreResource, poppKeyStorePassword);

    log.debug("| Exiting loadCertificates");
  }

  /**
   * Resolves the identities path based on the configured identities location. If the identities
   * resource is located on the file system, it uses that directory directly. Otherwise, it creates
   * a temporary directory and copies the PKI_CVC resources from the classpath.
   *
   * @return the resolved identities directory containing {@code PKI_CVC.G2}.
   * @throws ScenarioException if an error occurs while loading or processing the identities
   *     resources.
   */
  private Path resolveIdentitiesPath() {
    final var identitiesRoot = resourceLoader.getResource(identitiesLocation);

    try {
      final var url = identitiesRoot.getURL();
      if ("file".equals(url.getProtocol())) {
        return Paths.get(url.toURI());
      } else {
        final var tempFolder = createSecureTempDir();
        log.info("| tempFolder: {}", tempFolder);
        final PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();
        final Resource[] resources =
            resolver.getResources("classpath:" + PKI_CVC_G2_DIRECTORY + "/**");
        for (final Resource resource : resources) {
          if (resource.isReadable()) {
            final String resourceURL = resource.getURL().toString();
            final int index = resourceURL.indexOf(PKI_CVC_G2_DIRECTORY);
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
        return tempFolder.resolve(IDENTITIES_DIRECTORY);
      }
    } catch (final IOException | URISyntaxException e) {
      throw new ScenarioException(
          "| N/A",
          "Unable to load PKI_CVC from " + identitiesLocation + ": " + e.getMessage(),
          BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR,
          e);
    }
  }

  public CvCertificate findIdentityCvcByChr(final String chr) {
    return cvcDirectory
        .findByChr(chr)
        .orElseThrow(
            () ->
                new ScenarioException(
                    "| N/A",
                    "Unable to find trusted CVC for CHR " + chr,
                    BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR));
  }

  private byte[] readPrivateKeyDer(final ClassPathResource privateKeyResource) {
    try (final var inputStream = privateKeyResource.getInputStream()) {
      return inputStream.readAllBytes();
    } catch (final IOException e) {
      throw new PrivateKeyLoadingException(
          "serverSessionId",
          "Failed to load private key",
          BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR);
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
      return Files.createTempDirectory(baseDir, IDENTITIES_DIRECTORY, attrs);
    } else {
      return Files.createTempDirectory(baseDir, IDENTITIES_DIRECTORY);
    }
  }
}
