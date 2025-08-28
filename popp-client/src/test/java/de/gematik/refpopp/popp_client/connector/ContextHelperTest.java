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

package de.gematik.refpopp.popp_client.connector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContextHelperTest {

  @Test
  void convertToContextTypeReturnsContextTypeWithUserId() {
    // given
    final var context = new Context();
    context.setClientSystemId("clientSystemId");
    context.setMandantId("mandantId");
    context.setWorkplaceId("workplaceId");
    context.setUserId("userId");

    // when
    final var result = ContextHelper.convertToContextType(context);

    // then
    assertThat(result.getClientSystemId()).isEqualTo("clientSystemId");
    assertThat(result.getMandantId()).isEqualTo("mandantId");
    assertThat(result.getWorkplaceId()).isEqualTo("workplaceId");
    assertThat(result.getUserId()).isEqualTo("userId");
  }

  @Test
  void convertToContextTypeReturnsContextTypWithoutUserId() {
    // given
    final var context = new Context();
    context.setClientSystemId("clientSystemId");
    context.setMandantId("mandantId");
    context.setWorkplaceId("workplaceId");
    context.setUserId(null);

    // when
    final var result = ContextHelper.convertToContextType(context);

    // then
    assertThat(result.getClientSystemId()).isEqualTo("clientSystemId");
    assertThat(result.getMandantId()).isEqualTo("mandantId");
    assertThat(result.getWorkplaceId()).isEqualTo("workplaceId");
    assertThat(result.getUserId()).isNull();
  }
}
