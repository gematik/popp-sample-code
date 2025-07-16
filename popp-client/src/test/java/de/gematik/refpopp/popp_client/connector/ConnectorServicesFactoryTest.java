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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.ws.conn.servicedirectory.ConnectorServices;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.io.StringReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

class ConnectorServicesFactoryTest {

  private ConnectorServicesFactory sut;
  private RestTemplate restTemplateMock;
  private JAXBContext jaxbContextMock;

  @BeforeEach
  void setUp() {
    restTemplateMock = mock(RestTemplate.class);
    jaxbContextMock = mock(JAXBContext.class);
    sut = new ConnectorServicesFactory(restTemplateMock, jaxbContextMock);
  }

  @Test
  void createConnectorServicesShouldReturnConnectorServices() throws JAXBException {
    // given
    ReflectionTestUtils.setField(sut, "connectorAddress", "http://localhost:8080");
    final var url = "http://localhost:8080/connector.sds";
    when(restTemplateMock.getForEntity(url, String.class))
        .thenReturn(ResponseEntity.ok("<ConnectorServices></ConnectorServices>"));
    final var unmarshallerMock = mock(jakarta.xml.bind.Unmarshaller.class);
    when(jaxbContextMock.createUnmarshaller()).thenReturn(unmarshallerMock);
    when(unmarshallerMock.unmarshal(any(StringReader.class)))
        .thenReturn(mock(ConnectorServices.class));
    // when
    final var connectorServices = sut.createConnectorServices();

    // then
    assertThat(connectorServices).isNotNull();
    verify(restTemplateMock).getForEntity(url, String.class);
    verify(jaxbContextMock).createUnmarshaller();
  }

  @Test
  void createConnectorServicesShouldThrowExceptionWhenResponseIsNotSuccessful() {
    // given
    ReflectionTestUtils.setField(sut, "connectorAddress", "http://localhost:8080");
    final var url = "http://localhost:8080/connector.sds";
    when(restTemplateMock.getForEntity(url, String.class))
        .thenReturn(ResponseEntity.badRequest().body(null));

    // when & then
    assertThrows(
        IllegalStateException.class,
        () -> sut.createConnectorServices(),
        "Failed to load connector.sds from " + url);
  }

  @Test
  void createConnectorServicesShouldThrowExceptionWhenResponseBodyIsNull() {
    // given
    ReflectionTestUtils.setField(sut, "connectorAddress", "http://localhost:8080");
    final var url = "http://localhost:8080/connector.sds";
    when(restTemplateMock.getForEntity(url, String.class))
        .thenReturn(ResponseEntity.ok().body(null));

    // when & then
    assertThrows(
        IllegalStateException.class,
        () -> sut.createConnectorServices(),
        "Failed to load connector.sds from " + url);
  }
}
