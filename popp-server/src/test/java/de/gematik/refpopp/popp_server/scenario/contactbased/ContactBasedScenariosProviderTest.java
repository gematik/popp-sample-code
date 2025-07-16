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

package de.gematik.refpopp.popp_server.scenario.contactbased;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContactBasedScenariosProviderTest {

  @Test
  void getNextScenarioReturnsNextScenario() {
    // given
    final ContactBasedScenariosProvider.Scenario scenario1 =
        new ContactBasedScenariosProvider.Scenario("Scenario1", List.of());
    final ContactBasedScenariosProvider.Scenario scenario2 =
        new ContactBasedScenariosProvider.Scenario("Scenario2", List.of());
    final ContactBasedScenariosProvider contactBasedScenariosProvider =
        new ContactBasedScenariosProvider(List.of(scenario1, scenario2));

    // when
    final Optional<ContactBasedScenariosProvider.Scenario> nextScenario =
        contactBasedScenariosProvider.getNextScenario(scenario1);

    // then
    assertThat(nextScenario).isPresent();
    assertThat(contactBasedScenariosProvider.getScenarios()).contains(scenario2);
  }

  @Test
  void getNextScenarioReturnsEmptyWhenCurrentIsLast() {
    // given
    final ContactBasedScenariosProvider.Scenario scenario1 =
        new ContactBasedScenariosProvider.Scenario("Scenario1", List.of());
    final ContactBasedScenariosProvider contactBasedScenariosProvider =
        new ContactBasedScenariosProvider(List.of(scenario1));

    // when
    final Optional<ContactBasedScenariosProvider.Scenario> nextScenario =
        contactBasedScenariosProvider.getNextScenario(scenario1);

    // then
    assertThat(nextScenario).isEmpty();
  }

  @Test
  void addAdditionalStatesReturnsScenarioWithNewStates() {
    // given
    final var expectedStatusWord = List.of("9000", "6281");
    final StepDefinition stepDefinition1 =
        new StepDefinition("name1", "State1", "Command1", expectedStatusWord);
    final StepDefinition stepDefinition2 =
        new StepDefinition("name2", "State2", "Command2", expectedStatusWord);
    final StepDefinition dynamicStepDefinition1 =
        new StepDefinition("dynName1", "DynamicState1", "DynamicCommand", expectedStatusWord);
    final ContactBasedScenariosProvider.Scenario scenario =
        new ContactBasedScenariosProvider.Scenario(
            "Scenario", List.of(stepDefinition1, stepDefinition2));

    // when
    final var scenarioWithNewStates = scenario.addAdditionalSteps(List.of(dynamicStepDefinition1));

    // then
    assertThat(scenarioWithNewStates.stepDefinitions())
        .containsExactly(stepDefinition1, stepDefinition2, dynamicStepDefinition1);
  }

  @Test
  void equalsReturnsTrueForStepWhenSameObject() {
    // given
    final var expectedStatusWord = List.of("9000", "6281");
    final StepDefinition stepDefinition1 =
        new StepDefinition("name1", "description", "Command1", expectedStatusWord);
    final StepDefinition stepDefinition2 =
        new StepDefinition("name1", "description", "Command1", expectedStatusWord);

    // when
    final boolean equals = stepDefinition1.equals(stepDefinition2);

    // then
    assertThat(equals).isTrue();
  }

  @Test
  void equalsReturnsFalseForStateWhenDifferentObject() {
    // given
    final var expectedStatusWord = List.of("9000", "6281");
    final StepDefinition stepDefinition1 =
        new StepDefinition("name1", "State1", "Command1", expectedStatusWord);
    final StepDefinition stepDefinition2 =
        new StepDefinition("name2", "State2", "Command2", expectedStatusWord);

    // when
    final boolean equals = stepDefinition1.equals(stepDefinition2);

    // then
    assertThat(equals).isFalse();
  }

  @Test
  void equalsReturnsFalseForStateWhenObjectIsNull() {
    // given
    final var expectedStatusWord = List.of("9000", "6281");
    final StepDefinition stepDefinition1 =
        new StepDefinition("name1", "State1", "Command1", expectedStatusWord);
    final StepDefinition stepDefinition2 = null;

    // when
    final boolean equals = stepDefinition1.equals(stepDefinition2);

    // then
    assertThat(equals).isFalse();
  }

  @Test
  void equalsReturnsFalseForStateWhenAnotherObject() {
    // given
    final var expectedStatusWord = List.of("9000", "6281");
    final StepDefinition stepDefinition1 =
        new StepDefinition("name1", "State1", "Command1", expectedStatusWord);
    final ContactBasedScenariosProvider.Scenario scenario =
        new ContactBasedScenariosProvider.Scenario("Scenario", List.of());

    // when
    final boolean equals = stepDefinition1.equals(scenario);

    // then
    assertThat(equals).isFalse();
  }

  @Test
  void equalsReturnsTrueForScenarioWhenSameObject() {
    // given
    final ContactBasedScenariosProvider.Scenario scenario1 =
        new ContactBasedScenariosProvider.Scenario("Scenario1", List.of());
    final ContactBasedScenariosProvider.Scenario scenario2 =
        new ContactBasedScenariosProvider.Scenario("Scenario1", List.of());

    // when
    final boolean equals = scenario1.equals(scenario2);

    // then
    assertThat(equals).isTrue();
  }

  @Test
  void equalsReturnsFalseForScenarioWhenDifferentObject() {
    // given
    final ContactBasedScenariosProvider.Scenario scenario1 =
        new ContactBasedScenariosProvider.Scenario("Scenario1", List.of());
    final ContactBasedScenariosProvider.Scenario scenario2 =
        new ContactBasedScenariosProvider.Scenario("Scenario2", List.of());

    // when
    final boolean equals = scenario1.equals(scenario2);

    // then
    assertThat(equals).isFalse();
  }

  @Test
  void equalsReturnsFalseForScenarioWhenObjectIsNull() {
    // given
    final ContactBasedScenariosProvider.Scenario scenario1 =
        new ContactBasedScenariosProvider.Scenario("Scenario1", List.of());
    final ContactBasedScenariosProvider.Scenario scenario2 = null;

    // when
    final boolean equals = scenario1.equals(scenario2);

    // then
    assertThat(equals).isFalse();
  }

  @Test
  void equalsReturnsFalseForScenarioWhenAnotherObject() {
    // given
    final var expectedStatusWord = List.of("9000", "6281");
    final ContactBasedScenariosProvider.Scenario scenario1 =
        new ContactBasedScenariosProvider.Scenario("Scenario1", List.of());
    final StepDefinition stepDefinition =
        new StepDefinition("name1", "State1", "Command1", expectedStatusWord);

    // when
    final boolean equals = scenario1.equals(stepDefinition);

    // then
    assertThat(equals).isFalse();
  }

  @Test
  void hashCodeReturnsSameValueForEqualStep() {
    // given
    final var expectedStatusWord = List.of("9000", "6281");
    final StepDefinition stepDefinition1 =
        new StepDefinition("name1", "description", "Command1", expectedStatusWord);
    final StepDefinition stepDefinition2 =
        new StepDefinition("name1", "description", "Command1", expectedStatusWord);

    // when
    final int hashCode1 = stepDefinition1.hashCode();
    final int hashCode2 = stepDefinition2.hashCode();

    // then
    assertThat(hashCode1).isEqualTo(hashCode2);
  }

  @Test
  void hashCodeReturnsSameValueForEqualScenario() {
    // given
    final ContactBasedScenariosProvider.Scenario scenario1 =
        new ContactBasedScenariosProvider.Scenario("Scenario1", List.of());
    final ContactBasedScenariosProvider.Scenario scenario2 =
        new ContactBasedScenariosProvider.Scenario("Scenario1", List.of());

    // when
    final int hashCode1 = scenario1.hashCode();
    final int hashCode2 = scenario2.hashCode();

    // then
    assertThat(hashCode1).isEqualTo(hashCode2);
  }

  @Test
  void hashCodeReturnsDifferentValueForDifferentState() {
    // given
    final var expectedStatusWord = List.of("9000", "6281");
    final StepDefinition stepDefinition1 =
        new StepDefinition("name1", "State1", "Command1", expectedStatusWord);
    final StepDefinition stepDefinition2 =
        new StepDefinition("name2", "State2", "Command2", expectedStatusWord);

    // when
    final int hashCode1 = stepDefinition1.hashCode();
    final int hashCode2 = stepDefinition2.hashCode();

    // then
    assertThat(hashCode1).isNotEqualTo(hashCode2);
  }

  @Test
  void hashCodeReturnsDifferentValueForDifferentScenario() {
    // given
    final ContactBasedScenariosProvider.Scenario scenario1 =
        new ContactBasedScenariosProvider.Scenario("Scenario1", List.of());
    final ContactBasedScenariosProvider.Scenario scenario2 =
        new ContactBasedScenariosProvider.Scenario("Scenario2", List.of());

    // when
    final int hashCode1 = scenario1.hashCode();
    final int hashCode2 = scenario2.hashCode();

    // then
    assertThat(hashCode1).isNotEqualTo(hashCode2);
  }
}
