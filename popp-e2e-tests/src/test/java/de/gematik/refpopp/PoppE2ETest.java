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

package de.gematik.refpopp;

import static de.gematik.refpopp.util.JwtAssertions.checkTokenBody;
import static de.gematik.refpopp.util.JwtAssertions.checkTokenHeader;
import static de.gematik.refpopp.util.JwtAssertions.extractJwt;
import static de.gematik.refpopp.util.SpringApplicationStartUtil.findBootJar;
import static de.gematik.refpopp.util.SpringApplicationStartUtil.startApp;
import static de.gematik.refpopp.util.SpringApplicationStartUtil.waitForPort;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
public class PoppE2ETest {

  static final int SERVER_PORT = Integer.getInteger("popp.server.port", 14443);
  static final int CLIENT_PORT = Integer.getInteger("popp.client.port", 14081);
  static final String CONNECTOR_URL = System.getProperty("connector.end-point-url");
  private static final Logger log = LoggerFactory.getLogger(PoppE2ETest.class);

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine")
          .withDatabaseName("egk_hash_db")
          .withUsername("poppserver")
          .withPassword("verysafe");

  static Process server;
  static Process client;
  static Path serverLogPath;
  static Path clientLogPath;

  @BeforeAll
  static void startSystem() throws Exception {
    Path serverJar = findBootJar(Path.of("../popp-server/target/"));
    Path clientJar = findBootJar(Path.of("../popp-client/target/"));
    Path logDir = Path.of("target", "e2e-logs");
    serverLogPath =
        logDir.resolve(serverJar.getFileName().toString().replaceAll("\\.jar$", "") + ".log");
    clientLogPath =
        logDir.resolve(clientJar.getFileName().toString().replaceAll("\\.jar$", "") + ".log");

    server =
        startApp(
            serverJar,
            Map.of(
                "SPRING_DATASOURCE_URL", postgres.getJdbcUrl(),
                "SPRING_DATASOURCE_USERNAME", postgres.getUsername(),
                "SPRING_DATASOURCE_PASSWORD", postgres.getPassword(),
                "IDENTITIES_LOCATION", "classpath:identities",
                "CERT_HASH_IMPORT_LOCATION", ""),
            List.of("--server.port=" + SERVER_PORT, "--spring.profiles.active=dev"));
    waitForPort(
        SERVER_PORT,
        Duration.ofSeconds(60),
        Path.of(
            "target",
            "e2e-logs",
            serverJar.getFileName().toString().replaceAll("\\.jar$", "") + ".log"));

    Map<String, String> clientEnv = new java.util.HashMap<>();
    clientEnv.put("POPP_SERVER_URL", "wss://localhost:" + SERVER_PORT + "/ws");
    putIfPresent(
        clientEnv, "CONNECTOR_END_POINT_URL", System.getProperty("connector.end-point-url"));
    putIfPresent(
        clientEnv, "CONNECTOR_SECURE_TRUST_ALL", System.getProperty("connector.secure.trust-all"));
    putIfPresent(
        clientEnv, "CONNECTOR_SECURE_ENABLE", System.getProperty("connector.secure.enable"));
    putIfPresent(
        clientEnv,
        "CONNECTOR_SECURE_HOSTNAME_VALIDATION",
        System.getProperty("connector.secure.hostname-validation"));
    putIfPresent(
        clientEnv,
        "CONNECTOR_SECURE_KEYSTORE",
        firstNonBlank(
            System.getenv("CONNECTOR_SECURE_KEYSTORE"),
            System.getProperty("connector.secure.keystore")));
    putIfPresent(
        clientEnv,
        "CONNECTOR_SECURE_KEYSTORE_PASSWORD",
        firstNonBlank(
            System.getenv("CONNECTOR_SECURE_KEYSTORE_PASSWORD"),
            System.getProperty("connector.secure.keystore-password")));
    putIfPresent(
        clientEnv,
        "CONNECTOR_SECURE_TRUSTSTORE",
        firstNonBlank(
            System.getenv("CONNECTOR_SECURE_TRUSTSTORE"),
            System.getProperty("connector.secure.truststore")));
    putIfPresent(
        clientEnv,
        "CONNECTOR_SECURE_TRUSTSTORE_PASSWORD",
        firstNonBlank(
            System.getenv("CONNECTOR_SECURE_TRUSTSTORE_PASSWORD"),
            System.getProperty("connector.secure.truststore-password")));

    client =
        startApp(
            clientJar,
            clientEnv,
            List.of("--server.port=" + CLIENT_PORT, "--spring.profiles.active=dev"));

    waitForPort(
        CLIENT_PORT,
        Duration.ofSeconds(30),
        Path.of(
            "target",
            "e2e-logs",
            clientJar.getFileName().toString().replaceAll("\\.jar$", "") + ".log"));
  }

  @AfterAll
  static void stopSystem() {
    if (client != null) client.destroy();
    if (server != null) server.destroy();
  }

  private static void putIfPresent(Map<String, String> env, String key, String value) {
    if (value != null && !value.isBlank()) {
      env.put(key, value);
    }
  }

  private static String firstNonBlank(String value, String fallback) {
    if (value != null && !value.isBlank()) {
      return value;
    }
    return fallback;
  }

  @Test
  void fullE2ETokenGenerationWithVirtualCard() throws Exception {
    RestTemplate rest = new RestTemplate();

    Map<String, Object> body =
        Map.of(
            "communicationType", "contact-virtual",
            "clientSessionId", "123456");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

    ResponseEntity<String> response =
        rest.postForEntity("http://localhost:" + CLIENT_PORT + "/token", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    String jwt = extractJwt(response.getBody());

    assertThat(jwt).isNotBlank();
    checkTokenHeader(jwt);
    checkTokenBody(jwt);
  }

  @Test
  @EnabledIfSystemProperty(named = "popp.e2etest.connector", matches = "true")
  void fullE2ETokenGenerationWithConnector() throws Exception {
    log.info("Running fullE2ETokenGenerationWithConnector with CONNECTOR_URL: {}", CONNECTOR_URL);
    RestTemplate rest = new RestTemplate();

    Map<String, Object> body = Map.of("communicationType", "contact-connector");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

    ResponseEntity<String> response;
    try {
      response =
          rest.postForEntity("http://localhost:" + CLIENT_PORT + "/token", request, String.class);
    } catch (Exception e) {
      String logDump = buildLogDump();
      throw new AssertionError(
          "Connector E2E call failed. Logs:\n" + logDump + "\n--- end logs ---", e);
    }

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    String jwt = extractJwt(response.getBody());

    assertThat(jwt).isNotBlank();
    checkTokenHeader(jwt);
    checkTokenBody(jwt);
  }

  private static String buildLogDump() {
    return List.of(formatLog("popp-server", serverLogPath), formatLog("popp-client", clientLogPath))
        .stream()
        .filter(s -> !s.isBlank())
        .collect(Collectors.joining("\n"));
  }

  private static String formatLog(String name, Path path) {
    if (path == null) {
      return "";
    }
    String tail = readLogTail(path, 200);
    if (tail.isBlank()) {
      return name + " log empty or missing: " + path;
    }
    return "--- " + name + " (" + path + ") ---\n" + tail;
  }

  private static String readLogTail(Path logFile, int maxLines) {
    if (logFile == null || !Files.exists(logFile)) {
      return "";
    }
    try {
      List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
      int from = Math.max(0, lines.size() - maxLines);
      return String.join("\n", lines.subList(from, lines.size()));
    } catch (Exception e) {
      return "Unable to read log file: " + e.getMessage();
    }
  }
}
