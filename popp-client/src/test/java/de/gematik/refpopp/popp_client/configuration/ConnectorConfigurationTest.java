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

package de.gematik.refpopp.popp_client.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import de.gematik.ws.conn.servicedirectory.ConnectorServices;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnectorConfigurationTest {

  private ConnectorConfiguration sut;

  @BeforeEach
  void setUp() {
    sut = new ConnectorConfiguration();
  }

  @Test
  void testRestTemplate() {
    assertThat(sut.restTemplate(null)).isNotNull();
  }

  @Test
  void testJaxbContext() {
    assertThat(sut.jaxbContext()).isNotNull();
  }

  @Test
  void jaxbContextCreationFailsWhenJAXBException() {
    // given
    try (final var jaxbContextMock = mockStatic(JAXBContext.class)) {
      jaxbContextMock
          .when(() -> JAXBContext.newInstance(ConnectorServices.class))
          .thenThrow(new JAXBException("Test exception"));
      // when / then
      assertThatThrownBy(() -> sut.jaxbContext())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to create JAXBContext");
    }
  }
}
