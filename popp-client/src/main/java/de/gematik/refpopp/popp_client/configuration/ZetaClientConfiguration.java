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

package de.gematik.refpopp.popp_client.configuration;

import de.gematik.zeta.logging.Log;
import de.gematik.zeta.logging.ZetaLogLevel;
import de.gematik.zeta.logging.ZetaLogger;
import de.gematik.zeta.sdk.BuildConfig;
import de.gematik.zeta.sdk.TpmConfig;
import de.gematik.zeta.sdk.ZetaSdk;
import de.gematik.zeta.sdk.ZetaSdkClient;
import de.gematik.zeta.sdk.attestation.model.AttestationConfig;
import de.gematik.zeta.sdk.attestation.model.PlatformProductId;
import de.gematik.zeta.sdk.authentication.AuthConfig;
import de.gematik.zeta.sdk.authentication.SubjectTokenProvider;
import de.gematik.zeta.sdk.authentication.smb.SmbTokenProvider;
import de.gematik.zeta.sdk.network.http.client.ZetaHttpClientBuilder;
import de.gematik.zeta.sdk.storage.StorageConfig;
import io.ktor.client.plugins.logging.LogLevel;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZetaClientConfiguration {
  static final String APPLE_PLATFORM_TYPE_MACOS = "macos";
  static final String LINUX_PACKAGING_TYPE_JAR = "jar";
  static final String PLATFORM_PRODUCT_APPLICATION_ID = "testhub";
  static final String PLATFORM_PRODUCT_VERSION = "latest";

  @Bean
  public ZetaSdkClient zetaSdkClient(
      @Value("${popp-server.url}") final URI serverUri, ZetaConfigProperties zetaConfigProperties) {
    Log.INSTANCE.setLogLevel(mapToZetaLogLevel(zetaConfigProperties.getHttpLogLevel()));

    return ZetaSdk.INSTANCE.build(
        serverUri.toString(),
        new BuildConfig(
            "demo-client",
            "0.2.0",
            "sdk-client",
            new StorageConfig.Default(zetaConfigProperties.getStorage().getAesB64Key(), null),
            new TpmConfig() {},
            new AuthConfig(
                List.of("popp"),
                30L,
                true,
                getTokenProvider(
                    zetaConfigProperties.getAuthentication().getSmb().getKeyfile(),
                    zetaConfigProperties.getAuthentication().getSmb().getAlias(),
                    zetaConfigProperties.getAuthentication().getSmb().getPassword()),
                AttestationConfig.software(),
                ""),
            createPlatformProductId(),
            new ZetaHttpClientBuilder()
                .disableServerValidation(
                    zetaConfigProperties.getClient().isDisableServerValidation())
                .logging(zetaConfigProperties.getHttpLogLevel()),
            null,
            null,
            createZetaLogger()));
  }

  static PlatformProductId createPlatformProductId() {
    return createPlatformProductId(System.getProperty("os.name", ""));
  }

  static PlatformProductId createPlatformProductId(final String osName) {
    final var normalizedOsName = osName.toLowerCase(Locale.ROOT);

    if (normalizedOsName.contains("mac")) {
      return new PlatformProductId.AppleProductId(
          PlatformProductId.PLATFORM_APPLE, APPLE_PLATFORM_TYPE_MACOS, List.of());
    }

    if (normalizedOsName.contains("win")) {
      return new PlatformProductId.WindowsProductId(PlatformProductId.PLATFORM_WINDOWS, "", "");
    }

    if (normalizedOsName.contains("linux")
        || normalizedOsName.contains("nux")
        || normalizedOsName.contains("nix")) {
      return new PlatformProductId.LinuxProductId(
          PlatformProductId.PLATFORM_LINUX,
          LINUX_PACKAGING_TYPE_JAR,
          PLATFORM_PRODUCT_APPLICATION_ID,
          PLATFORM_PRODUCT_VERSION);
    }

    throw new IllegalStateException(
        "Unsupported operating system for ZETA platform product id: " + osName);
  }

  private SubjectTokenProvider getTokenProvider(
      final String keyfile, final String alias, final String password) {
    final Path resolvedKeyfile = PathResolver.resolveAgainstWorkingDirectoryAncestors(keyfile);
    if (!Files.isReadable(resolvedKeyfile)) {
      throw new IllegalStateException("Can't read private key: " + resolvedKeyfile);
    }

    return new SmbTokenProvider(
        new SmbTokenProvider.Credentials(resolvedKeyfile.toString(), alias, password, ""));
  }

  static ZetaLogLevel mapToZetaLogLevel(final LogLevel httpLogLevel) {
    if (httpLogLevel == LogLevel.NONE) return ZetaLogLevel.NONE;
    if (httpLogLevel == LogLevel.INFO) return ZetaLogLevel.INFO;
    return ZetaLogLevel.DEBUG;
  }

  private static final String LOG_FORMAT = "[{}] {}";

  static ZetaLogger createZetaLogger() {
    final org.slf4j.Logger log = LoggerFactory.getLogger("de.gematik.zeta");
    return new ZetaLogger() {
      @Override
      public void d(
          final String tag, @NotNull final Function0<String> message, final Throwable throwable) {
        if (log.isDebugEnabled()) log.debug(LOG_FORMAT, tag, message.invoke(), throwable);
      }

      @Override
      public void i(
          final String tag, @NotNull final Function0<String> message, final Throwable throwable) {
        if (log.isInfoEnabled()) log.info(LOG_FORMAT, tag, message.invoke(), throwable);
      }

      @Override
      public void w(
          final String tag, @NotNull final Function0<String> message, final Throwable throwable) {
        if (log.isWarnEnabled()) log.warn(LOG_FORMAT, tag, message.invoke(), throwable);
      }

      @Override
      public void e(
          final String tag, @NotNull final Function0<String> message, final Throwable throwable) {
        if (log.isErrorEnabled()) log.error(LOG_FORMAT, tag, message.invoke(), throwable);
      }
    };
  }
}
