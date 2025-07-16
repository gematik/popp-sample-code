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

package de.gematik.refpopp.popp_server.scenario.common.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public abstract class AbstractCardScenarios implements CardScenarioProvider {

  private final List<Scenario> scenarios;

  protected AbstractCardScenarios(final List<Scenario> scenarios) {
    this.scenarios = scenarios;
  }

  @Override
  public List<Scenario> getScenarios() {
    return Collections.unmodifiableList(scenarios);
  }

  @Override
  public Optional<Scenario> getNextScenario(final Scenario currentScenario) {
    final var index = scenarios.indexOf(currentScenario);
    if (index < scenarios.size() - 1) {
      return Optional.of(scenarios.get(index + 1));
    }
    return Optional.empty();
  }

  public record Scenario(String name, List<StepDefinition> stepDefinitions) {

    public Scenario addAdditionalSteps(final List<StepDefinition> dynamicStepDefinitions) {
      final var updatedSteps = new ArrayList<>(this.stepDefinitions);
      updatedSteps.addAll(dynamicStepDefinitions);
      return new Scenario(this.name, updatedSteps);
    }

    @Override
    public boolean equals(final Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      final Scenario scenario = (Scenario) obj;
      return Objects.equals(name, scenario.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }
  }

  public record StepDefinition(
      String name, String description, String commandApdu, List<String> expectedStatusWord) {

    public StepDefinition(
        final String name,
        final String description,
        final String commandApdu,
        final List<String> expectedStatusWord) {
      this.name = name;
      this.description = description;
      this.commandApdu = commandApdu != null ? commandApdu.replaceAll("\\s+", "") : null;
      this.expectedStatusWord = expectedStatusWord;
    }
  }
}
