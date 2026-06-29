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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.zeta.logging.ZetaLogLevel;
import de.gematik.zeta.sdk.attestation.model.PlatformProductId;
import io.ktor.client.plugins.logging.LogLevel;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ZetaClientConfigurationTest {
  private final ZetaClientConfiguration sut = new ZetaClientConfiguration();

  @Test
  void zetaSdkClientThrowsWhenKeyfileCannotBeRead() {
    final var mockConfig = mock(ZetaConfigProperties.class);
    final var mockAuthentication = mock(ZetaConfigProperties.Authentication.class);
    final var mockSmb = mock(ZetaConfigProperties.Smb.class);
    final var mockStorage = mock(ZetaConfigProperties.Storage.class);
    final var mockClient = mock(ZetaConfigProperties.Client.class);
    when(mockConfig.getAuthentication()).thenReturn(mockAuthentication);
    when(mockAuthentication.getSmb()).thenReturn(mockSmb);
    when(mockSmb.getKeyfile()).thenReturn("does/not/exist.p12");
    when(mockSmb.getAlias()).thenReturn("alias");
    when(mockSmb.getPassword()).thenReturn("00");
    when(mockConfig.getStorage()).thenReturn(mockStorage);
    when(mockStorage.getAesB64Key()).thenReturn("testKey");
    when(mockConfig.getClient()).thenReturn(mockClient);
    when(mockClient.isDisableServerValidation()).thenReturn(false);
    when(mockConfig.getHttpLogLevel()).thenReturn(LogLevel.NONE);
    final URI serverUri = URI.create("wss://example.com");

    assertThatThrownBy(() -> sut.zetaSdkClient(serverUri, mockConfig))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Can't read private key:");
  }

  @Test
  void createPlatformProductIdUsesCurrentOperatingSystemName() {
    String originalOsName = System.getProperty("os.name");
    try {
      System.setProperty("os.name", "Windows 11");
      PlatformProductId productId = ZetaClientConfiguration.createPlatformProductId();
      assertThat(productId).isInstanceOf(PlatformProductId.WindowsProductId.class);
    } finally {
      restoreSystemProperty("os.name", originalOsName);
    }
  }

  @Test
  void createPlatformProductIdReturnsAppleProductIdForMacOs() {
    PlatformProductId productId = ZetaClientConfiguration.createPlatformProductId("Mac OS X");
    assertThat(productId).isInstanceOf(PlatformProductId.AppleProductId.class);
    PlatformProductId.AppleProductId appleProductId = (PlatformProductId.AppleProductId) productId;
    assertThat(appleProductId.getPlatform()).isEqualTo(PlatformProductId.PLATFORM_APPLE);
    assertThat(appleProductId.getPlatformType())
        .isEqualTo(ZetaClientConfiguration.APPLE_PLATFORM_TYPE_MACOS);
    assertThat(appleProductId.getAppBundleIds()).isEqualTo(List.of());
  }

  @Test
  void createPlatformProductIdReturnsLinuxProductIdForLinux() {
    PlatformProductId productId = ZetaClientConfiguration.createPlatformProductId("Linux");
    assertThat(productId).isInstanceOf(PlatformProductId.LinuxProductId.class);
    PlatformProductId.LinuxProductId linuxProductId = (PlatformProductId.LinuxProductId) productId;
    assertThat(linuxProductId.getPlatform()).isEqualTo(PlatformProductId.PLATFORM_LINUX);
    assertThat(linuxProductId.getPackagingType())
        .isEqualTo(ZetaClientConfiguration.LINUX_PACKAGING_TYPE_JAR);
    assertThat(linuxProductId.getApplicationId())
        .isEqualTo(ZetaClientConfiguration.PLATFORM_PRODUCT_APPLICATION_ID);
    assertThat(linuxProductId.getVersion())
        .isEqualTo(ZetaClientConfiguration.PLATFORM_PRODUCT_VERSION);
  }

  @Test
  void createPlatformProductIdReturnsWindowsProductIdForWindows() {
    PlatformProductId productId = ZetaClientConfiguration.createPlatformProductId("Windows 11");
    assertThat(productId).isInstanceOf(PlatformProductId.WindowsProductId.class);
    PlatformProductId.WindowsProductId windowsProductId =
        (PlatformProductId.WindowsProductId) productId;
    assertThat(windowsProductId.getPlatform()).isEqualTo(PlatformProductId.PLATFORM_WINDOWS);
    assertThat(windowsProductId.getStoreId()).isEmpty();
    assertThat(windowsProductId.getPackageFamilyName()).isEmpty();
  }

  @Test
  void createPlatformProductIdThrowsForUnsupportedOperatingSystems() {
    assertThrows(
        IllegalStateException.class,
        () -> ZetaClientConfiguration.createPlatformProductId("FreeBSD"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Linux", "linux-gnu", "GNU/Linux"})
  void createPlatformProductIdReturnsLinuxProductIdForLinuxVariants(String osName) {
    PlatformProductId productId = ZetaClientConfiguration.createPlatformProductId(osName);
    assertThat(productId).isInstanceOf(PlatformProductId.LinuxProductId.class);
    PlatformProductId.LinuxProductId linuxProductId = (PlatformProductId.LinuxProductId) productId;
    assertThat(linuxProductId.getPlatform()).isEqualTo(PlatformProductId.PLATFORM_LINUX);
  }

  @ParameterizedTest
  @ValueSource(strings = {"nux", "FreeBSD/nux", "something-nix"})
  void createPlatformProductIdReturnsLinuxProductIdForNuxAndNixVariants(String osName) {
    PlatformProductId productId = ZetaClientConfiguration.createPlatformProductId(osName);
    assertThat(productId).isInstanceOf(PlatformProductId.LinuxProductId.class);
  }

  @Test
  void mapToZetaLogLevelMapsNone() {
    ZetaLogLevel level = ZetaClientConfiguration.mapToZetaLogLevel(LogLevel.NONE);
    assertThat(level).isEqualTo(ZetaLogLevel.NONE);
  }

  @Test
  void mapToZetaLogLevelMapsInfo() {
    ZetaLogLevel level = ZetaClientConfiguration.mapToZetaLogLevel(LogLevel.INFO);
    assertThat(level).isEqualTo(ZetaLogLevel.INFO);
  }

  @Test
  void mapToZetaLogLevelMapsAllOtherLevelsToDebug() {
    ZetaLogLevel level = ZetaClientConfiguration.mapToZetaLogLevel(LogLevel.ALL);
    assertThat(level).isEqualTo(ZetaLogLevel.DEBUG);
  }

  @Test
  void createZetaLoggerReturnsValidLogger() {
    var logger = ZetaClientConfiguration.createZetaLogger();
    assertThat(logger).isNotNull();
    // Verify logger methods don't throw exceptions
    logger.d("test-tag", () -> "debug message", null);
    logger.i("test-tag", () -> "info message", null);
    logger.w("test-tag", () -> "warn message", null);
    logger.e("test-tag", () -> "error message", null);
  }

  private void restoreSystemProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
