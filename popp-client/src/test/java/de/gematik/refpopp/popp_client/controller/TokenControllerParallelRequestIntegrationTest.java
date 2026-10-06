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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.refpopp.popp_client.PoppClientApplication;
import de.gematik.refpopp.popp_client.cardreader.CardReader;
import de.gematik.refpopp.popp_client.client.CommunicationService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(TokenController.class)
@ContextConfiguration(classes = PoppClientApplication.class)
class TokenControllerParallelRequestIntegrationTest {

  private static final int NUMBER_OF_REQUESTS = 10;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CommunicationService communicationService;
  @MockitoBean private CardReader cardReader;

  @Test
  void createsTokensForTenParallelVirtualCardRequests() throws Exception {
    final var allRequestsStarted = new CountDownLatch(NUMBER_OF_REQUESTS);
    final var releaseRequests = new CountDownLatch(1);
    final var startRequests = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              allRequestsStarted.countDown();
              assertThat(releaseRequests.await(5, TimeUnit.SECONDS)).isTrue();
              return "token";
            })
        .when(communicationService)
        .startVirtualCard(any(CardConnectionType.class), anyString(), any());

    final ExecutorService executor = Executors.newFixedThreadPool(NUMBER_OF_REQUESTS);
    try {
      final List<java.util.concurrent.Future<MvcResult>> responses = new ArrayList<>();
      for (int requestNumber = 0; requestNumber < NUMBER_OF_REQUESTS; requestNumber++) {
        final var connectionType =
            requestNumber % 2 == 0
                ? CardConnectionType.CONTACT_VIRTUAL
                : CardConnectionType.CONTACTLESS_VIRTUAL;
        final var sessionId = "parallel-request-" + requestNumber;
        responses.add(
            executor.submit(
                () -> {
                  assertThat(startRequests.await(5, TimeUnit.SECONDS)).isTrue();
                  return mockMvc
                      .perform(
                          post("/token")
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(
                                  """
                                  {"communicationType":"%s","clientSessionId":"%s"}
                                  """
                                      .formatted(connectionType.getType(), sessionId)))
                      .andReturn();
                }));
      }

      startRequests.countDown();
      assertThat(allRequestsStarted.await(5, TimeUnit.SECONDS)).isTrue();
      releaseRequests.countDown();

      for (final var response : responses) {
        assertThat(response.get(5, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
      }
    } finally {
      releaseRequests.countDown();
      executor.shutdownNow();
    }

    verify(communicationService, times(NUMBER_OF_REQUESTS / 2))
        .startVirtualCard(eq(CardConnectionType.CONTACT_STANDARD), anyString(), isNull());
    verify(communicationService, times(NUMBER_OF_REQUESTS / 2))
        .startVirtualCard(eq(CardConnectionType.CONTACTLESS_STANDARD), anyString(), isNull());
  }
}
