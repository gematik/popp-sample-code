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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.messages.ScenarioStep;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VirtualCardServiceFactoryTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              VirtualCardService.class,
              VirtualCardServiceFactory.class,
              VirtualCardImageLoader.class,
              ApduMatcher.class,
              BrainpoolP256r1CvcSigner.class);

  @Test
  void missingDelayDisablesWaitingForDefaultAndFactoryCards() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertNoDelay(context.getBean(VirtualCardService.class));
          assertNoDelay(context.getBean(VirtualCardServiceFactory.class).create(""));
        });
  }

  @Test
  void explicitZeroDisablesWaitingForDefaultAndFactoryCards() {
    contextRunner
        .withPropertyValues("virtual-card.response-delay-ms=0")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertNoDelay(context.getBean(VirtualCardService.class));
              assertNoDelay(context.getBean(VirtualCardServiceFactory.class).create(""));
            });
  }

  @Test
  void configuredDelayAppliesToDefaultAndRequestSpecificCards() {
    contextRunner
        .withPropertyValues("virtual-card.response-delay-ms=50")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertConfiguredDelay(context.getBean(VirtualCardService.class), 50);
              final var requestCard =
                  context
                      .getBean(VirtualCardServiceFactory.class)
                      .create("EGK_80276883110000179858_gema5_popp.xml");
              assertThat(requestCard.isConfigured()).isTrue();
              assertConfiguredDelay(requestCard, 50);
            });
  }

  @Test
  void negativeDelayFailsAtStartup() {
    contextRunner
        .withPropertyValues("virtual-card.response-delay-ms=-1")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalArgumentException.class)
                  .hasRootCauseMessage("virtual-card.response-delay-ms must not be negative");
            });
  }

  @Test
  void invalidDelayFailsAtStartup() {
    contextRunner
        .withPropertyValues("virtual-card.response-delay-ms=invalid")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(NumberFormatException.class);
            });
  }

  @Test
  void defaultAndFactoryCardsUseInjectedCvcSigner() throws Exception {
    final var signer = mock(CvcSigner.class);
    final var cardData =
        new VirtualCardImageLoader().load("EGK_80276883110000179858_gema5_popp.xml");
    final byte[] privateKey = cardData.egkAuthCvcPrivateKey();
    final byte[] tau = new byte[25];
    when(signer.sign(privateKey, tau)).thenReturn(new byte[64]);

    new ApplicationContextRunner()
        .withUserConfiguration(
            VirtualCardService.class,
            VirtualCardServiceFactory.class,
            VirtualCardImageLoader.class,
            ApduMatcher.class)
        .withBean(CvcSigner.class, () -> signer)
        .withPropertyValues("virtual-card.image-file=EGK_80276883110000179858_gema5_popp.xml")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              final var steps =
                  List.of(
                      new ScenarioStep(
                          "0088000018" + HexFormat.of().formatHex(new byte[24]) + "00",
                          List.of("9000")));
              final String expectedResponse = "00".repeat(64) + "9000";

              assertThat(context.getBean(VirtualCardService.class).process(steps))
                  .containsExactly(expectedResponse);
              assertThat(
                      context
                          .getBean(VirtualCardServiceFactory.class)
                          .create("EGK_80276883110000179858_gema5_popp.xml")
                          .process(steps))
                  .containsExactly(expectedResponse);
              verify(signer, times(2)).sign(privateKey, tau);
              verifyNoMoreInteractions(signer);
            });
  }

  private static void assertNoDelay(final VirtualCardService service) {
    try {
      Thread.currentThread().interrupt();

      assertThat(service.process(List.of(selectMasterFile()))).containsExactly("9000");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  private static void assertConfiguredDelay(
      final VirtualCardService service, final long expectedDelayMs) {
    final var steps = List.of(selectMasterFile(), selectMasterFile());

    final long start = System.nanoTime();
    final var responses = service.process(steps, new VirtualCardSessionState());
    final long elapsed = System.nanoTime() - start;

    assertThat(responses).containsExactly("9000", "9000");
    assertThat(elapsed)
        .isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(expectedDelayMs * steps.size()));
  }

  private static ScenarioStep selectMasterFile() {
    return new ScenarioStep("00 A4 04 0C 07 D2760001448000", List.of("9000"));
  }
}
