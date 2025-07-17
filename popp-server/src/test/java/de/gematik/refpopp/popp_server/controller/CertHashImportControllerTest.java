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

package de.gematik.refpopp.popp_server.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.refpopp.popp_server.file.temp.EgkImportTempFileService;
import de.gematik.refpopp.popp_server.hashdb.EgkHashImportService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CertHashImportControllerTest {

  @Mock private EgkHashImportService egkHashImportService;

  @Mock private EgkImportTempFileService egkImportTempFileService;

  private MockMvc mockMvc;

  private final String importLocation = "/some/import/location";

  private AutoCloseable mockitoAnnotations;

  @BeforeEach
  void setUp() {
    mockitoAnnotations = MockitoAnnotations.openMocks(this);
    final CertHashImportController sut =
        new CertHashImportController(
            egkHashImportService, egkImportTempFileService, importLocation);
    mockMvc = MockMvcBuilders.standaloneSetup(sut).build();
  }

  @AfterEach
  void tearDown() throws Exception {
    mockitoAnnotations.close();
  }

  @Test
  void createFileWithEmptyFileReturnsBadRequest() throws Exception {
    // given
    final var emptyFile =
        new MockMultipartFile("file", "test.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

    // when
    mockMvc
        .perform(multipart("/cert-hash/import").file(emptyFile).param("sessionId", "anySession"))
        // then
        .andExpect(status().isBadRequest())
        .andExpect(content().string("No file uploaded"));
  }

  @Test
  void createFileWithValidFileReturnsOkAndInvokesServices(@TempDir final Path tempDir)
      throws Exception {
    // given
    final byte[] content = "dummy content".getBytes();
    final var multipartFile =
        new MockMultipartFile(
            "file", "dummy.dat", MediaType.APPLICATION_OCTET_STREAM_VALUE, content);
    final var sessionId = "session-123";
    final var fakeTempFile = Files.createTempFile(tempDir, "egk-", ".dat");
    when(egkImportTempFileService.createFile(importLocation)).thenReturn(fakeTempFile);

    // when
    mockMvc
        .perform(multipart("/cert-hash/import").file(multipartFile).param("sessionId", sessionId))
        // then
        .andExpect(status().isOk())
        .andExpect(content().string("Request to import cert hash file was successful"));

    verify(egkImportTempFileService).createFile(importLocation);
    assertThat(Files.readAllBytes(fakeTempFile)).containsExactly(content);
    verify(egkHashImportService).importData(fakeTempFile, sessionId);
    verify(egkImportTempFileService).deleteFile(fakeTempFile);
  }

  @Test
  void createFileWhenCreateTempFileThrowsIOExceptionReturnsInternalServerError() throws Exception {
    // given
    final byte[] content = "irrelevant".getBytes();
    final var multipartFile =
        new MockMultipartFile(
            "file", "dummy.dat", MediaType.APPLICATION_OCTET_STREAM_VALUE, content);
    final var sessionId = "sess-err";
    when(egkImportTempFileService.createFile(importLocation))
        .thenThrow(new IOException("cannot create temp file"));

    // when
    mockMvc
        .perform(multipart("/cert-hash/import").file(multipartFile).param("sessionId", sessionId))
        // then
        .andExpect(status().isInternalServerError())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "Could not save uploaded file: cannot create temp file")));

    verify(egkHashImportService, never()).importData(any(), anyString());
    verify(egkImportTempFileService).deleteFile(null);
  }

  @Test
  void createFileWhenTransferToFailsReturnsInternalServerError(@TempDir final Path tempDir)
      throws Exception {
    // given
    final byte[] content = "dummy".getBytes();
    final var multipartFile =
        new MockMultipartFile(
            "file", "dummy.dat", MediaType.APPLICATION_OCTET_STREAM_VALUE, content);
    final var sessionId = "sess-transfer-fail";
    final var fakeTempFile = tempDir.resolve("readonly.dat");
    Files.createFile(fakeTempFile);
    fakeTempFile.toFile().setWritable(false);
    when(egkImportTempFileService.createFile(importLocation)).thenReturn(fakeTempFile);

    // when
    mockMvc
        .perform(multipart("/cert-hash/import").file(multipartFile).param("sessionId", sessionId))
        // then
        .andExpect(status().isInternalServerError())
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("Could not save uploaded file")));

    verify(egkHashImportService, never()).importData(any(), anyString());
    verify(egkImportTempFileService).deleteFile(fakeTempFile);
  }
}
