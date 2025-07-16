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

package de.gematik.refpopp.popp_client.connector.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceEndpointProviderTest {

  private ServiceEndpointProvider sut;
  private ServicePathExtractor servicePathExtractorMock;

  @BeforeEach
  void setUp() {
    servicePathExtractorMock = mock(ServicePathExtractor.class);
    sut = new ServiceEndpointProvider(servicePathExtractorMock);
  }

  @Test
  void getCardServiceEndpoint() {
    // given
    final var servicePath = new ServicePath();
    servicePath.setPath("/path");
    servicePath.setVersion("version");
    when(servicePathExtractorMock.getCardServicePath()).thenReturn(servicePath);

    // when
    final ServiceEndpoint endpoint = sut.getCardServiceEndpoint();

    // then
    assertThat(endpoint).isNotNull();
    assertThat(endpoint.getEndpoint()).isEqualTo("/path");
    assertThat(endpoint.getVersion()).isEqualTo("version");
    verify(servicePathExtractorMock).getCardServicePath();
  }

  @Test
  void getEventServiceEndpoint() {
    // given
    final var servicePath = new ServicePath();
    servicePath.setPath("/path");
    servicePath.setVersion("version");
    when(servicePathExtractorMock.getEventServicePath()).thenReturn(servicePath);

    // when
    final ServiceEndpoint endpoint = sut.getEventServiceEndpoint();

    // then
    assertThat(endpoint).isNotNull();
    assertThat(endpoint.getEndpoint()).isEqualTo("/path");
    assertThat(endpoint.getVersion()).isEqualTo("version");
    verify(servicePathExtractorMock).getEventServicePath();
  }

  @Test
  void callEventServiceFullEndpointAndCheckItIsLikeExpected() {
    // given
    final var servicePath = new ServicePath();
    servicePath.setPath("/path");
    servicePath.setVersion("version");
    when(servicePathExtractorMock.getEventServicePath()).thenReturn(servicePath);
    when(servicePathExtractorMock.getConnectorUrl()).thenReturn("https://127.0.0.1");

    // when
    final String endpoint = sut.getEventServiceFullEndpoint();

    // then
    assertThat(endpoint).isEqualTo("https://127.0.0.1/path");
  }

  @Test
  void callCardServiceFullEndpointAndCheckItIsLikeExpected() {
    // given
    final var servicePath = new ServicePath();
    servicePath.setPath("/path");
    servicePath.setVersion("version");
    when(servicePathExtractorMock.getCardServicePath()).thenReturn(servicePath);
    when(servicePathExtractorMock.getConnectorUrl()).thenReturn("https://127.0.0.1");

    // when
    final String endpoint = sut.getCardServiceFullEndpoint();

    // then
    assertThat(endpoint).isEqualTo("https://127.0.0.1/path");
  }
}
