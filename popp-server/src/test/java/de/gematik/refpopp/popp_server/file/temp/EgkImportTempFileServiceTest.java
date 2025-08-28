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

package de.gematik.refpopp.popp_server.file.temp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EgkImportTempFileServiceTest {

  private EgkImportTempFileService sut;

  @BeforeEach
  void setUp() {
    sut = new EgkImportTempFileService();
  }

  @Test
  void createFileWithEmptyPathThrowsIOException() {
    // given
    final String path = "";

    // when / then
    assertThatThrownBy(() -> sut.createFile(path))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Path must not be empty or blank.");
  }

  @Test
  void createFileWithValidDirectoryCreatesTempFileInGivenDirectory(@TempDir final Path tempDir)
      throws IOException {
    // given
    final String path = tempDir.toString();

    // when
    final Path tempFile = sut.createFile(path);

    // then
    assertThat(tempFile).exists().hasParentRaw(tempDir);
    assertThat(tempFile.getFileName().toString()).startsWith("egk-").endsWith(".dat");

    // cleanup
    Files.deleteIfExists(tempFile);
  }

  @Test
  void createFileWithInvalidPathThrowsIOException() {
    final Path notADir = Paths.get(System.getProperty("java.io.tmpdir"), "someFile.txt");
    try {
      Files.writeString(notADir, "dummy");
    } catch (final IOException e) {
      fail("Failed");
    }
    final String path = notADir.toString();

    // when / then
    assertThatThrownBy(() -> sut.createFile(path))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Path is not a directory or not writable");

    // cleanup
    try {
      Files.deleteIfExists(notADir);
    } catch (final IOException ignored) {
    }
  }

  @Test
  void deleteFileExistingFileDeletesFile(@TempDir final Path tempDir) throws IOException {
    // given
    final Path file = Files.createTempFile(tempDir, "toDelete-", ".dat");
    assertThat(file).exists();

    // when
    sut.deleteFile(file);

    // then
    assertThat(file).doesNotExist();
  }

  @Test
  void deleteFileNonExistingFileThrowsNoException() {
    // given
    final Path file =
        Paths.get(
            System.getProperty("java.io.tmpdir"), "nonexistent-" + System.nanoTime() + ".dat");
    assertThat(file).doesNotExist();

    // when
    sut.deleteFile(file);

    // then
    assertThat(file).doesNotExist();
  }
}
