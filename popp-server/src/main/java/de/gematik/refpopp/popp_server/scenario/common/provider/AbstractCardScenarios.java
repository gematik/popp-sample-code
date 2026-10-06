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

/**
 * Base implementation for providers that expose an ordered list of card scenarios.
 *
 * <p>The scenarios are returned in their configured order and can be traversed one at a time.
 * Subclasses provide the scenario list through the protected constructor.
 */
public abstract class AbstractCardScenarios implements CardScenarioProvider {

  private final List<Scenario> scenarios;

  /**
   * Creates a card scenario provider with the given ordered scenarios.
   *
   * @param scenarios the scenarios available to the provider
   */
  protected AbstractCardScenarios(final List<Scenario> scenarios) {
    this.scenarios = scenarios;
  }

  /**
   * Returns the configured scenarios in their execution order.
   *
   * @return an unmodifiable view of the configured scenarios
   */
  @Override
  public List<Scenario> getScenarios() {
    return Collections.unmodifiableList(scenarios);
  }

  /**
   * Returns the scenario following the specified scenario.
   *
   * @param currentScenario the currently processed scenario
   * @return the next scenario, or an empty optional if the current scenario is the last one
   */
  @Override
  public Optional<Scenario> getNextScenario(final Scenario currentScenario) {
    final var index = scenarios.indexOf(currentScenario);
    if (index < scenarios.size() - 1) {
      return Optional.of(scenarios.get(index + 1));
    }
    return Optional.empty();
  }

  /**
   * Describes a scenario and the steps that belong to it.
   *
   * @param scenarioId the identifier of the scenario
   * @param stepDefinitions the ordered definitions of the scenario steps
   */
  public record Scenario(ScenarioId scenarioId, List<StepDefinition> stepDefinitions) {

    /**
     * Creates a scenario from a scenario identifier and step identifiers.
     *
     * @param scenarioId the identifier of the scenario
     * @param stepIds the ordered step identifiers
     * @return a scenario containing the specified steps
     */
    public static Scenario of(final ScenarioId scenarioId, final StepId... stepIds) {
      return new Scenario(scenarioId, Arrays.stream(stepIds).map(StepDefinition::new).toList());
    }

    /**
     * Creates a copy of this scenario with additional steps appended.
     *
     * @param dynamicStepDefinitions the steps to append
     * @return a new scenario containing the existing and additional steps
     */
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

  /**
   * Describes a single scenario step and its optional command data.
   *
   * <p>Command data is accepted only for steps that require it, such as MSE and PSO APDU steps.
   *
   * @param stepId the identifier of the step
   * @param commandData the optional command data associated with the step
   */
  public record StepDefinition(StepId stepId, byte[] commandData) {

    public static final class InvalidStepDefinitionException extends IllegalArgumentException {

      public InvalidStepDefinitionException(final String message) {
        super(message);
      }
    }

    /**
     * Creates a step definition without command data.
     *
     * @param stepId the identifier of the step
     */
    public StepDefinition(final StepId stepId) {
      this(stepId, null);
    }

    /**
     * Creates a step definition and validates its command data.
     *
     * @param stepId the identifier of the step
     * @param commandData the optional command data associated with the step
     * @throws InvalidStepDefinitionException if the command data does not match the step type
     */
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
