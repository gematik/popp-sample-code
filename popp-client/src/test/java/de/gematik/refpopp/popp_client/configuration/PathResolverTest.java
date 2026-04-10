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

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathResolverTest {

  @TempDir Path tempDir;

  @Test
  void resolvesRelativePathFromCurrentBaseDirectory() throws Exception {
    final var expected = createFile(tempDir.resolve("docker/zeta/smcb-private/smcb_private.p12"));

    final var actual =
        PathResolver.resolveAgainstConfiguredBaseDirectoryAndWorkingDirectoryAncestors(
            "docker/zeta/smcb-private/smcb_private.p12", null, tempDir);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void resolvesRelativePathFromParentDirectoryWhenStartedInModuleDirectory() throws Exception {
    final var repoRoot = tempDir.resolve("repo");
    final var moduleDir = repoRoot.resolve("popp-client");
    Files.createDirectories(moduleDir);
    final var expected = createFile(repoRoot.resolve("docker/zeta/smcb-private/smcb_private.p12"));

    final var actual =
        PathResolver.resolveAgainstConfiguredBaseDirectoryAndWorkingDirectoryAncestors(
            "docker/zeta/smcb-private/smcb_private.p12", null, moduleDir);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void resolvesRelativePathFromConfiguredBaseDirectoryBeforeWorkingDirectory() throws Exception {
    final var configuredBaseDir = tempDir.resolve("custom-base");
    final var workingDirectory = tempDir.resolve("repo/popp-client");
    Files.createDirectories(workingDirectory);
    final var expected =
        createFile(configuredBaseDir.resolve("docker/zeta/smcb-private/smcb_private.p12"));

    final var actual =
        PathResolver.resolveAgainstConfiguredBaseDirectoryAndWorkingDirectoryAncestors(
            "docker/zeta/smcb-private/smcb_private.p12",
            configuredBaseDir.toString(),
            workingDirectory);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void keepsAbsolutePathUnchanged() throws Exception {
    final var expected = createFile(tempDir.resolve("smcb_private.p12"));

    final var actual =
        PathResolver.resolveAgainstConfiguredBaseDirectoryAndWorkingDirectoryAncestors(
            expected.toString(), null, tempDir);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void fallsBackToBaseDirectoryWhenNoCandidateExists() {
    final var workingDirectory = tempDir.resolve("repo/popp-client");
    final var actual =
        PathResolver.resolveAgainstConfiguredBaseDirectoryAndWorkingDirectoryAncestors(
            "missing/file.p12", null, workingDirectory);

    assertThat(actual)
        .isEqualTo(workingDirectory.toAbsolutePath().normalize().resolve("missing/file.p12"));
  }

  @Test
  void fallsBackToConfiguredBaseDirectoryWhenItIsSet() {
    final var configuredBaseDir = tempDir.resolve("custom-base");
    final var workingDirectory = tempDir.resolve("repo/popp-client");
    final var actual =
        PathResolver.resolveAgainstConfiguredBaseDirectoryAndWorkingDirectoryAncestors(
            "missing/file.p12", configuredBaseDir.toString(), workingDirectory);

    assertThat(actual)
        .isEqualTo(configuredBaseDir.toAbsolutePath().normalize().resolve("missing/file.p12"));
  }

  private Path createFile(final Path path) throws Exception {
    Files.createDirectories(path.getParent());
    return Files.writeString(path, "test").toAbsolutePath().normalize();
  }
}
