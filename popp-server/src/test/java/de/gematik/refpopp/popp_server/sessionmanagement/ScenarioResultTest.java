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

package de.gematik.refpopp.popp_server.sessionmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.refpopp.popp_server.scenario.common.result.ScenarioResult;
import org.junit.jupiter.api.Test;

class ScenarioResultTest {

  @Test
  void testScenarioResultStepEqualsAndHashCode() {
    // given
    final byte[] data1 = {1, 2, 3};
    final byte[] data2 = {1, 2, 3};
    final var step1 = new ScenarioResult.ScenarioResultStep("Step1", "SUCCESS", data1);
    final var step2 = new ScenarioResult.ScenarioResultStep("Step1", "SUCCESS", data2);

    // when & then
    assertThat(step1).isEqualTo(step2).hasSameHashCodeAs(step2.hashCode());
  }

  @Test
  void testScenarioResultStepNotEquals() {
    // given
    final byte[] data1 = {1, 2, 3};
    final byte[] data2 = {4, 5, 6};
    final var step1 = new ScenarioResult.ScenarioResultStep("Step1", "SUCCESS", data1);
    final var step2 = new ScenarioResult.ScenarioResultStep("Step1", "SUCCESS", data2);

    // when & then
    assertThat(step1).isNotEqualTo(step2);
  }

  @Test
  void testScenarioResultStepToString() {
    // given
    final byte[] data = {1, 2, 3};
    final var step = new ScenarioResult.ScenarioResultStep("Step1", "SUCCESS", data);

    // when
    final var toString = step.toString();

    // then
    assertThat(toString)
        .isEqualTo(
            "ScenarioStep{" + "name='Step1'" + ", statusWord='SUCCESS'" + ", data=[1, 2, 3]" + '}');
  }
}
