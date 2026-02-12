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

package de.gematik.refpopp.popp_client.cardreader.card;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.poppcommons.api.messages.ScenarioStep;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class VirtualCardServiceTest {

  private VirtualCardService virtualCardService;

  @BeforeEach
  void setUp() {
    var eventPublisher = mock(ApplicationEventPublisher.class);

    virtualCardService =
        new VirtualCardService(
            eventPublisher,
            "IMG_eGK_G21_TU_root6 1.xml",
            "00 a4 040c    07 D2760001448000",
            "00 b0 9100    00",
            "00 b0 8700    00",
            "00 b0 8600    00",
            "80 ca 0100    00   0000",
            "00 22 41A4    06   840109  800154",
            "10 86 0000107c0ec30c000a8027600101169990210100",
            "00 86 0000    45"
                + " 7c43854104987fce93bfc191e4db006b56f8fd5f749d256fc5842f0f3f31becf613ce146f66318f77ff7ee51c10b6b6a0f349896400c7601bfc07608ff08fe0ce1d921ca42",
            "00 a4 040c   0a   a000000167455349474e",
            "00 b0 8400   00   0000");
  }

  @Test
  void isConfiguredTrue() {
    assertTrue(virtualCardService.isConfigured());
  }

  @Test
  void isConfiguredFalse() {
    virtualCardService.setCvcCertificate(null);
    virtualCardService.setAuthCertificate(null);
    assertFalse(virtualCardService.isConfigured());
  }

  @Test
  void processScenarioList() {
    ScenarioStep step1 = Mockito.mock(ScenarioStep.class);
    ScenarioStep step2 = Mockito.mock(ScenarioStep.class);
    ScenarioStep step3 = Mockito.mock(ScenarioStep.class);
    ScenarioStep step4 = Mockito.mock(ScenarioStep.class);

    when(step1.getCommandApdu()).thenReturn("00b0860000"); // read-end-entity-cv-certificate
    when(step2.getCommandApdu())
        .thenReturn("10860000107c0ec30c000a8027600101169990210100"); // mutual-authentication-step-1
    when(step3.getCommandApdu()).thenReturn("00b08400000000"); // read-ef-c-ch-aut-e256
    when(step4.getCommandApdu()).thenReturn("00a4040c07D2760001448000"); // select-master-file

    List<String> responses = virtualCardService.process(List.of(step1, step2, step3, step4));

    assertEquals(4, responses.size());

    assertEquals(
        virtualCardService.getCvcCertificate() + VirtualCardService.APDU_RESPONSE_OK,
        responses.get(0)); // read-end-entity-cv-certificate
    assertEquals(
        VirtualCardService.APDU_RESPONSE_OK, responses.get(1)); // mutual-authentication-step-1
    assertEquals(
        virtualCardService.getAuthCertificate() + VirtualCardService.APDU_RESPONSE_OK,
        responses.get(2)); // read-ef-c-ch-aut-e256
    assertEquals(VirtualCardService.APDU_RESPONSE_OK, responses.get(3)); // select-master-file

    verify(step1).getCommandApdu();
    verify(step2).getCommandApdu();
    verify(step3).getCommandApdu();
    verify(step4).getCommandApdu();
    verifyNoMoreInteractions(step1, step2, step3, step4);
  }
}
