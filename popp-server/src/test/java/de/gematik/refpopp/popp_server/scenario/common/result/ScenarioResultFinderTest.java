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

package de.gematik.refpopp.popp_server.scenario.common.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

@Component
class ScenarioResultFinderTest {

  @Test
  void testFindScenarioResultStepSuccess() {
    // given
    final var sessionId = "testSessionId";
    final var name = "Step1";
    final var steps =
        List.of(
            new ScenarioResult.ScenarioResultStep("Step1", "9000", new byte[] {1, 2, 3}),
            new ScenarioResult.ScenarioResultStep("Step2", "6831", new byte[] {4, 5, 6}));
    final var finder = new ScenarioResultFinder();

    // when
    final var result = finder.find(sessionId, steps, name);

    // then
    assertThat(result).isNotNull();
    assertThat(result.name()).isEqualTo(name);
  }

  @Test
  void testFindScenarioResultStepThrowsException() {
    // given
    final var sessionId = "testSessionId";
    final var description = "NonExistentStep";
    final var steps =
        List.of(
            new ScenarioResult.ScenarioResultStep("Step1", "9000", new byte[] {1, 2, 3}),
            new ScenarioResult.ScenarioResultStep("Step2", "9000", new byte[] {4, 5, 6}));
    final var finder = new ScenarioResultFinder();

    // when & then
    assertThatThrownBy(() -> finder.find(sessionId, steps, description))
        .isInstanceOf(ScenarioException.class)
        .hasMessageContaining("APDU Result of " + description + " not found")
        .hasFieldOrPropertyWithValue("sessionId", sessionId);
  }
}
