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
import static org.mockito.Mockito.mock;

import de.gematik.refpopp.popp_client.cardreader.card.CardEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceConfigurationTest {

  private ServiceConfiguration sut;

  @BeforeEach
  void setUp() {
    final var cardEventPublisherMock = mock(CardEventPublisher.class);
    sut = new ServiceConfiguration(cardEventPublisherMock);
  }

  @Test
  void cardReaderBeanCreatedSuccessfully() {
    // given

    // when
    final var cardReader = sut.cardReader(null);

    // then
    assertThat(cardReader).isNotNull();
  }

  @Test
  void readerAndCardMonitoringBeanCreatedSuccessfully() {
    // given

    // when
    final var monitoring = sut.readerAndCardMonitoring();

    // then
    assertThat(monitoring).isNotNull();
  }

  @Test
  void objectMapperBeanCreatedSuccessfully() {
    // given

    // when
    final var objectMapper = sut.objectMapper();

    // then
    assertThat(objectMapper).isNotNull();
  }
}
