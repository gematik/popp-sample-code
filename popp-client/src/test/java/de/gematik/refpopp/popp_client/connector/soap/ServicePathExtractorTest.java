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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_client.connector.ConnectorServicesFactory;
import de.gematik.ws.conn.servicedirectory.ConnectorServices;
import de.gematik.ws.conn.servicedirectory.EndpointType;
import de.gematik.ws.conn.servicedirectory.ServiceType;
import de.gematik.ws.conn.servicedirectory.VersionType;
import de.gematik.ws.conn.servicedirectory.VersionsType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ServicePathExtractorTest {

  private ServicePathExtractor sut;
  private ConnectorServicesFactory connectorServicesFactoryMock;

  @BeforeEach
  void setUp() {
    connectorServicesFactoryMock = mock(ConnectorServicesFactory.class);
    sut = new ServicePathExtractor("http://localhost:8080", false, connectorServicesFactoryMock);
  }

  @Test
  void getEventServicePath() {
    // given
    final var connectorServicesMock = mock(ConnectorServices.class, RETURNS_DEEP_STUBS);
    when(connectorServicesFactoryMock.createConnectorServices()).thenReturn(connectorServicesMock);
    final var serviceType = new ServiceType();
    serviceType.setName("EventService");
    final var versionsType = new VersionsType();
    final var versionType = new VersionType();
    versionType.setVersion("version");
    serviceType.setVersions(versionsType);
    final var endpointType = new EndpointType();
    endpointType.setLocation("location");
    versionType.setEndpoint(endpointType);
    versionsType.getVersion().add(versionType);
    when(connectorServicesMock.getServiceInformation().getService())
        .thenReturn(List.of(serviceType));

    // when
    final var result = sut.getEventServicePath();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo("location");
  }

  @Test
  void getCardServicePath() {
    // given
    ReflectionTestUtils.setField(sut, "connectorUrl", "http://localhost:8080/path");
    final var connectorServicesMock = mock(ConnectorServices.class, RETURNS_DEEP_STUBS);
    when(connectorServicesFactoryMock.createConnectorServices()).thenReturn(connectorServicesMock);
    final var serviceType = new ServiceType();
    serviceType.setName("CardService");
    final var versionsType = new VersionsType();
    final var versionType = new VersionType();
    versionType.setVersion("version");
    serviceType.setVersions(versionsType);
    final var endpointType = new EndpointType();
    endpointType.setLocation("location");
    versionType.setEndpoint(endpointType);
    versionsType.getVersion().add(versionType);
    when(connectorServicesMock.getServiceInformation().getService())
        .thenReturn(List.of(serviceType));

    // when
    final var result = sut.getCardServicePath();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo("location");
  }

  @Test
  void getCardServicePathWithTlsEndpoint() {
    // given
    ServicePathExtractor secureServicePathExtractor =
        new ServicePathExtractor("http://localhost:8080", true, connectorServicesFactoryMock);
    final var connectorServicesMock = mock(ConnectorServices.class, RETURNS_DEEP_STUBS);
    when(connectorServicesFactoryMock.createConnectorServices()).thenReturn(connectorServicesMock);
    final var serviceType = new ServiceType();
    serviceType.setName("CardService");
    final var versionsType = new VersionsType();
    final var versionType = new VersionType();
    versionType.setVersion("version");
    serviceType.setVersions(versionsType);
    final EndpointType nonSecureEndpointType = new EndpointType();
    nonSecureEndpointType.setLocation("nonsecurelocation");
    versionType.setEndpoint(nonSecureEndpointType);
    final EndpointType secureEndpointType = new EndpointType();
    secureEndpointType.setLocation("securelocation");
    versionType.setEndpointTLS(secureEndpointType);
    versionsType.getVersion().add(versionType);
    when(connectorServicesMock.getServiceInformation().getService())
        .thenReturn(List.of(serviceType));

    // when
    final var result = secureServicePathExtractor.getCardServicePath();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo("securelocation");
  }

  @Test
  void getCardServicePathWithNonTlsEndpoint() {
    // given
    final var connectorServicesMock = mock(ConnectorServices.class, RETURNS_DEEP_STUBS);
    when(connectorServicesFactoryMock.createConnectorServices()).thenReturn(connectorServicesMock);
    final var serviceType = new ServiceType();
    serviceType.setName("CardService");
    final var versionsType = new VersionsType();
    final var versionType = new VersionType();
    versionType.setVersion("version");
    serviceType.setVersions(versionsType);
    final EndpointType nonSecureEndpointType = new EndpointType();
    nonSecureEndpointType.setLocation("nonsecurelocation");
    versionType.setEndpoint(nonSecureEndpointType);
    final EndpointType secureEndpointType = new EndpointType();
    secureEndpointType.setLocation("securelocation");
    versionType.setEndpointTLS(secureEndpointType);
    versionsType.getVersion().add(versionType);
    when(connectorServicesMock.getServiceInformation().getService())
        .thenReturn(List.of(serviceType));

    // when
    final var result = sut.getCardServicePath();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo("nonsecurelocation");
  }

  @Test
  void getCardServicePathWithEmptyPath() {
    // given
    final var connectorServicesMock = mock(ConnectorServices.class, RETURNS_DEEP_STUBS);
    when(connectorServicesFactoryMock.createConnectorServices()).thenReturn(connectorServicesMock);
    final var serviceType = new ServiceType();
    serviceType.setName("CardService");
    final var versionsType = new VersionsType();
    final var versionType = new VersionType();
    versionType.setVersion("version");
    serviceType.setVersions(versionsType);
    final var endpointType = new EndpointType();
    endpointType.setLocation("");
    versionType.setEndpoint(endpointType);
    versionsType.getVersion().add(versionType);
    when(connectorServicesMock.getServiceInformation().getService())
        .thenReturn(List.of(serviceType));

    // when
    final var result = sut.getCardServicePath();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEmpty();
  }

  @Test
  void getCardServicePathReturnsNullWhenNoVersion() {
    // given
    final var connectorServicesMock = mock(ConnectorServices.class, RETURNS_DEEP_STUBS);
    when(connectorServicesFactoryMock.createConnectorServices()).thenReturn(connectorServicesMock);
    final var serviceType = new ServiceType();
    serviceType.setName("CardService");
    final var versionsType = new VersionsType();
    serviceType.setVersions(versionsType);
    when(connectorServicesMock.getServiceInformation().getService())
        .thenReturn(List.of(serviceType));

    // when
    final var result = sut.getCardServicePath();

    // then
    assertThat(result).isNull();
  }

  @Test
  void getCardServicePathReturnsNullWhenNoService() {
    // given
    final var connectorServicesMock = mock(ConnectorServices.class, RETURNS_DEEP_STUBS);
    when(connectorServicesFactoryMock.createConnectorServices()).thenReturn(connectorServicesMock);
    when(connectorServicesMock.getServiceInformation().getService()).thenReturn(List.of());

    // when
    final var result = sut.getCardServicePath();

    // then
    assertThat(result).isNull();
  }

  @Test
  void getCardServicePathWithLatestVersion() {
    // given
    final var connectorServicesMock = mock(ConnectorServices.class, RETURNS_DEEP_STUBS);
    when(connectorServicesFactoryMock.createConnectorServices()).thenReturn(connectorServicesMock);
    final var serviceType = new ServiceType();
    serviceType.setName("CardService");
    final var versionsType = new VersionsType();
    final var versionType1 = new VersionType();
    versionType1.setVersion("1.2");
    final var versionType2 = new VersionType();
    versionType2.setVersion("1.1");
    serviceType.setVersions(versionsType);
    final var endpointType = new EndpointType();
    endpointType.setLocation("location");
    versionType2.setEndpoint(endpointType);
    versionType1.setEndpoint(endpointType);
    versionsType.getVersion().add(versionType1);
    versionsType.getVersion().add(versionType2);
    when(connectorServicesMock.getServiceInformation().getService())
        .thenReturn(List.of(serviceType));

    // when
    final var result = sut.getCardServicePath();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo("location");
  }

  @Test
  void getCardServicePathWithEqualVersions() {
    // given
    final var connectorServicesMock = mock(ConnectorServices.class, RETURNS_DEEP_STUBS);
    when(connectorServicesFactoryMock.createConnectorServices()).thenReturn(connectorServicesMock);
    final var serviceType = new ServiceType();
    serviceType.setName("CardService");
    final var versionsType = new VersionsType();
    final var versionType1 = new VersionType();
    versionType1.setVersion("1.2");
    final var versionType2 = new VersionType();
    versionType2.setVersion("1.2");
    serviceType.setVersions(versionsType);
    final var endpointType = new EndpointType();
    endpointType.setLocation("location");
    versionType2.setEndpoint(endpointType);
    versionType1.setEndpoint(endpointType);
    versionsType.getVersion().add(versionType1);
    versionsType.getVersion().add(versionType2);
    when(connectorServicesMock.getServiceInformation().getService())
        .thenReturn(List.of(serviceType));

    // when
    final var result = sut.getCardServicePath();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo("location");
  }
}
