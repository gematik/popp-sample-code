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

import java.nio.file.Files;
import java.nio.file.Path;

public final class PathResolver {

  static final String BASEDIR_ENVIRONMENT_VARIABLE = "POPP_BASEDIR";
  static final String BASEDIR_SYSTEM_PROPERTY = "popp.basedir";

  private PathResolver() {}

  public static Path resolveAgainstWorkingDirectoryAncestors(final String location) {
    return resolveAgainstConfiguredBaseDirectoryAndWorkingDirectoryAncestors(
        location, readConfiguredBaseDirectory(), Path.of("").toAbsolutePath().normalize());
  }

  static Path resolveAgainstConfiguredBaseDirectoryAndWorkingDirectoryAncestors(
      final String location, final String configuredBaseDirectory, final Path workingDirectory) {
    final var trimmed = location == null ? "" : location.trim();
    if (trimmed.isEmpty()) {
      return Path.of(trimmed);
    }

    final var configuredPath = Path.of(trimmed);
    if (configuredPath.isAbsolute()) {
      return configuredPath.normalize();
    }

    final var normalizedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
    final var normalizedConfiguredBaseDirectory =
        normalizeConfiguredBaseDirectory(configuredBaseDirectory);
    if (normalizedConfiguredBaseDirectory != null) {
      final var candidate = normalizedConfiguredBaseDirectory.resolve(configuredPath).normalize();
      if (Files.exists(candidate)) {
        return candidate;
      }
    }

    for (Path baseDir = normalizedWorkingDirectory;
        baseDir != null;
        baseDir = baseDir.getParent()) {
      final var candidate = baseDir.resolve(configuredPath).normalize();
      if (Files.exists(candidate)) {
        return candidate;
      }
    }

    if (normalizedConfiguredBaseDirectory != null) {
      return normalizedConfiguredBaseDirectory.resolve(configuredPath).normalize();
    }

    return normalizedWorkingDirectory.resolve(configuredPath).normalize();
  }

  private static String readConfiguredBaseDirectory() {
    final var configuredViaEnvironment = System.getenv(BASEDIR_ENVIRONMENT_VARIABLE);
    if (configuredViaEnvironment != null && !configuredViaEnvironment.trim().isEmpty()) {
      return configuredViaEnvironment;
    }

    return System.getProperty(BASEDIR_SYSTEM_PROPERTY);
  }

  private static Path normalizeConfiguredBaseDirectory(final String configuredBaseDirectory) {
    if (configuredBaseDirectory == null || configuredBaseDirectory.trim().isEmpty()) {
      return null;
    }

    return Path.of(configuredBaseDirectory.trim()).toAbsolutePath().normalize();
  }
}
