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

package de.gematik.refpopp.util;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class SpringApplicationStartUtil {
  private SpringApplicationStartUtil() {}

  public static Path findBootJar(Path moduleDir) throws IOException {
    try (Stream<Path> files = Files.list(moduleDir)) {
      return files
          .filter(p -> p.getFileName().toString().endsWith(".jar"))
          .filter(p -> !p.getFileName().toString().endsWith("-sources.jar"))
          .filter(p -> !p.getFileName().toString().endsWith("-javadoc.jar"))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("No runnable JAR found in " + moduleDir));
    }
  }

  public static Process startApp(Path jar, Map<String, String> env, List<String> args)
      throws IOException {

    List<String> command = new ArrayList<>();
    command.add("java");
    command.add("-jar");
    command.add(jar.toAbsolutePath().toString());
    command.addAll(args);

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.environment().putAll(env);
    pb.redirectErrorStream(true);

    Path logDir = Path.of("target", "e2e-logs");
    Files.createDirectories(logDir);
    String logFileName = jar.getFileName().toString().replaceAll("\\.jar$", "") + ".log";
    Path logFile = logDir.resolve(logFileName);
    pb.redirectOutput(logFile.toFile());

    return pb.start();
  }

  public static void waitForPort(int port, Duration timeout) {
    waitForPort(port, timeout, null);
  }

  public static void waitForPort(int port, Duration timeout, Path logFile) {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    while (System.currentTimeMillis() < deadline) {
      try (Socket socket = new Socket("localhost", port)) {
        return; // success
      } catch (IOException ignored) {
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
    String logTail = logFile == null ? "" : readLogTail(logFile, 200);
    String message = "Port " + port + " did not open in time";
    if (!logTail.isBlank()) {
      message += "\n--- last log lines (" + logFile.getFileName() + ") ---\n" + logTail;
    }
    throw new RuntimeException(message);
  }

  private static String readLogTail(Path logFile, int maxLines) {
    if (!Files.exists(logFile)) {
      return "";
    }
    try {
      List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
      int from = Math.max(0, lines.size() - maxLines);
      return String.join("\n", lines.subList(from, lines.size()));
    } catch (IOException e) {
      return "Unable to read log file: " + e.getMessage();
    }
  }
}
