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

package de.gematik.refpopp.popp_client.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.refpopp.popp_client.qrcode.StaticQrCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StaticQrCodeControllerTest {

  private MockMvc mockMvc;
  private StaticQrCodeService staticQrCodeService;

  @BeforeEach
  void setUp() {
    staticQrCodeService = Mockito.mock(StaticQrCodeService.class);
  }

  @Test
  void getStaticQrCodeWithoutTelematikIdReturnsBadRequest() throws Exception {
    var sut = new StaticQrCodeController(staticQrCodeService, "", "", 300);
    mockMvc = MockMvcBuilders.standaloneSetup(sut).build();

    mockMvc.perform(get("/qrcode/static")).andExpect(status().isBadRequest());

    verify(staticQrCodeService, never()).createPayloadJson(anyString(), anyString());
    verify(staticQrCodeService, never()).createPngQrCode(anyString(), anyInt());
  }

  @Test
  void getStaticQrCodeUsesProvidedTidAndDefaults() throws Exception {
    var sut = new StaticQrCodeController(staticQrCodeService, "DEFAULT-TID", "DEFAULT-WPID", 300);
    mockMvc = MockMvcBuilders.standaloneSetup(sut).build();

    when(staticQrCodeService.createPayloadJson("TID-1", "DEFAULT-WPID")).thenReturn("{\"x\":1}");
    byte[] png = new byte[] {1, 2, 3};
    when(staticQrCodeService.createPngQrCode("{\"x\":1}", 300)).thenReturn(png);

    mockMvc
        .perform(get("/qrcode/static").param("tid", "TID-1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(content().bytes(png));

    verify(staticQrCodeService).createPayloadJson("TID-1", "DEFAULT-WPID");
    verify(staticQrCodeService).createPngQrCode("{\"x\":1}", 300);
  }

  @Test
  void getStaticQrCodeRejectsInvalidSize() throws Exception {
    var sut = new StaticQrCodeController(staticQrCodeService, "DEFAULT-TID", "DEFAULT-WPID", 300);
    mockMvc = MockMvcBuilders.standaloneSetup(sut).build();

    mockMvc
        .perform(get("/qrcode/static").param("tid", "TID-1").param("size", "63"))
        .andExpect(status().isBadRequest());

    verify(staticQrCodeService, never()).createPayloadJson(anyString(), anyString());
    verify(staticQrCodeService, never()).createPngQrCode(anyString(), anyInt());
  }
}
