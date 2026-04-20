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
import java.util.Arrays;
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

  public record Scenario(ScenarioId scenarioId, List<StepDefinition> stepDefinitions) {

    public static Scenario of(final ScenarioId scenarioId, final StepId... stepIds) {
      return new Scenario(scenarioId, Arrays.stream(stepIds).map(StepDefinition::new).toList());
    }

    public Scenario addAdditionalSteps(final List<StepDefinition> dynamicStepDefinitions) {
      final var updatedSteps = new ArrayList<>(this.stepDefinitions);
      updatedSteps.addAll(dynamicStepDefinitions);
      return new Scenario(this.scenarioId, updatedSteps);
    }

    public String name() {
      return scenarioId.value();
    }

    public boolean is(final ScenarioId candidate) {
      return scenarioId == candidate;
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
      return scenarioId == scenario.scenarioId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(scenarioId);
    }
  }

  public record StepDefinition(StepId stepId, byte[] commandData) {

    public static final class InvalidStepDefinitionException extends IllegalArgumentException {

      public InvalidStepDefinitionException(final String message) {
        super(message);
      }
    }

    public StepDefinition(final StepId stepId) {
      this(stepId, null);
    }

    public StepDefinition(final StepId stepId, final byte[] commandData) {
      this.stepId = Objects.requireNonNull(stepId, "stepId must not be null");
      this.commandData = commandData == null ? null : commandData.clone();
      validateCommandData(stepId, commandData);
    }

    private static void validateCommandData(final StepId stepId, final byte[] commandData) {
      if (requiresCommandData(stepId) && (commandData == null || commandData.length == 0)) {
        throw new InvalidStepDefinitionException(
            "Step " + stepId.value() + " requires command data");
      }
      if (!requiresCommandData(stepId) && commandData != null) {
        throw new InvalidStepDefinitionException(
            "Step " + stepId.value() + " does not accept command data");
      }
    }

    private static boolean requiresCommandData(final StepId stepId) {
      return stepId == StepId.MSE_APDU || stepId == StepId.PSO_APDU;
    }

    public String name() {
      return stepId.value();
    }

    public ExpectedStatusWords expectedStatusWords() {
      return stepId.expectedStatusWords();
    }

    @Override
    public byte[] commandData() {
      return commandData == null ? null : commandData.clone();
    }

    @Override
    public boolean equals(final Object obj) {
      return obj instanceof StepDefinition(final StepId otherStepId, final byte[] otherCommandData)
          && stepId == otherStepId
          && Arrays.equals(commandData, otherCommandData);
    }

    @Override
    public int hashCode() {
      return 31 * stepId.hashCode() + Arrays.hashCode(commandData);
    }

    @Override
    public String toString() {
      return "StepDefinition[stepId="
          + stepId
          + ", commandData="
          + Arrays.toString(commandData)
          + "]";
    }

    public boolean is(final StepId candidate) {
      return stepId == candidate;
    }
  }
}
