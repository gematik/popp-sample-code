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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public final class EgkImportTempFileService {

  public Path createFile(final String path) throws IOException {
    final Path tmp;

    if (StringUtils.hasText(path)) {
      final var baseDir = Path.of(path).toAbsolutePath().normalize();
      if (!Files.isDirectory(baseDir) || !Files.isWritable(baseDir)) {
        throw new IOException("Path is not a directory or not writable: " + baseDir);
      }
      tmp = Files.createTempFile(baseDir, "egk-", ".dat");
    } else {
      throw new IOException("Path must not be empty or blank.");
    }
    log.info("Temporary file created: {}", tmp);
    return tmp;
  }

  public void deleteFile(final Path path) {
    try {
      Files.deleteIfExists(path);
      log.info("Temporary file deleted: {}", path);
    } catch (final IOException e) {
      log.error("Failed to delete temporary file: {}", path, e);
    }
  }
}
