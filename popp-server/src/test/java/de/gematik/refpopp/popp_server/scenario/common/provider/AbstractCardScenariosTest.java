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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition;
import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios.StepDefinition.InvalidStepDefinitionException;
import org.junit.jupiter.api.Test;

class AbstractCardScenariosTest {

  @Test
  void createsStepDefinitionWithStepDefaults() {
    final var sut = new StepDefinition(StepId.SELECT_MASTER_FILE);

    assertThat(sut.expectedStatusWords())
        .isEqualTo(StepId.SELECT_MASTER_FILE.expectedStatusWords());
    assertThat(sut.commandData()).isNull();
  }

  @Test
  void createsStepDefinitionWithCommandData() {
    final var commandData = "car".getBytes();

    final var sut = new StepDefinition(StepId.MSE_APDU, commandData);

    assertThat(sut.commandData()).isEqualTo(commandData);
  }

  @Test
  void creatingDynamicStepDefinitionWithoutCommandDataThrows() {
    assertThrows(InvalidStepDefinitionException.class, () -> new StepDefinition(StepId.MSE_APDU));
  }

  @Test
  void creatingStaticStepDefinitionWithCommandDataThrows() {
    final var commandData = "car".getBytes();

    assertThrows(
        InvalidStepDefinitionException.class,
        () -> new StepDefinition(StepId.SELECT_MASTER_FILE, commandData));
  }

  @Test
  void stepDefinitionToStringUsesArrayContent() {
    final var sut = new StepDefinition(StepId.MSE_APDU, "car".getBytes());

    assertThat(sut).hasToString("StepDefinition[stepId=MSE_APDU, commandData=[99, 97, 114]]");
  }
}
