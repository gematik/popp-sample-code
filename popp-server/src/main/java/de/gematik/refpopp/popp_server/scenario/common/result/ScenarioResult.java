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

import de.gematik.refpopp.popp_server.scenario.common.provider.ScenarioId;
import de.gematik.refpopp.popp_server.scenario.common.provider.StepId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record ScenarioResult(String name, List<ScenarioResultStep> scenarioResultSteps) {

  public ScenarioResult(
      final ScenarioId scenarioId, final List<ScenarioResultStep> scenarioResultSteps) {
    this(scenarioId.value(), scenarioResultSteps);
  }

  public record ScenarioResultStep(String name, String statusWord, byte[] data) {

    public ScenarioResultStep(final StepId stepId, final String statusWord, final byte[] data) {
      this(stepId.value(), statusWord, data);
    }

    public boolean is(final StepId stepId) {
      return name.equalsIgnoreCase(stepId.value());
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ScenarioResultStep that = (ScenarioResultStep) o;
      return Objects.deepEquals(data, that.data)
          && Objects.equals(statusWord, that.statusWord)
          && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, statusWord, Arrays.hashCode(data));
    }

    @Override
    public String toString() {
      return "ScenarioStep{"
          + "name='"
          + name
          + '\''
          + ", statusWord='"
          + statusWord
          + '\''
          + ", data="
          + Arrays.toString(data)
          + '}';
    }
  }
}
