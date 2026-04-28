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

package de.gematik.refpopp.popp_server.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.gematik.refpopp.popp_server.certificates.CertificateProviderService;
import de.gematik.refpopp.popp_server.certificates.KeyStoreLoader;
import de.gematik.smartcards.crypto.EcPrivateKeyImpl;
import de.gematik.smartcards.g2icc.cos.SecureMessagingConverterSoftware;
import de.gematik.smartcards.g2icc.cvc.Cvc;
import java.security.KeyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;

class ServiceConfigurationTest {

  @Mock private CertificateProviderService certificateProviderServiceMock;

  private ServiceConfiguration sut;

  @BeforeEach
  void setUp() {
    certificateProviderServiceMock = mock(CertificateProviderService.class);
    sut = new ServiceConfiguration();
  }

  @Test
  void shouldGetObjectMapper() {
    // when
    final var objectMapper = sut.objectMapper();

    // then
    assertThat(objectMapper).isNotNull();
  }

  @Test
  void shouldGetSecureMessagingConverterSoftwareBean() {
    try (final MockedConstruction<SecureMessagingConverterSoftware> mocked =
        Mockito.mockConstruction(SecureMessagingConverterSoftware.class)) {
      // given
      final var cvcMock = mock(Cvc.class);
      final var ecPrivateKeyImplMock = mock(EcPrivateKeyImpl.class);
      when(certificateProviderServiceMock.getCvcSubCertificate()).thenReturn(cvcMock);
      when(certificateProviderServiceMock.getCvEndEntityCertificate()).thenReturn(cvcMock);
      when(certificateProviderServiceMock.getCvcPoppServicePrk()).thenReturn(ecPrivateKeyImplMock);

      // when
      final var secureMessagingConverterSoftware =
          sut.secureMessagingConverterSoftware(certificateProviderServiceMock);

      // then
      assertThat(secureMessagingConverterSoftware).isNotNull();
      assertThat(mocked).isNotNull();
      assertThat(mocked.constructed()).hasSize(1);
      verify(certificateProviderServiceMock).getCvcSubCertificate();
      verify(certificateProviderServiceMock).getCvEndEntityCertificate();
      verify(certificateProviderServiceMock).getCvcPoppServicePrk();
    }
  }

  @Test
  void shouldGetEntityManagerFactoryBean() {
    // given
    final var dataSource = mock(javax.sql.DataSource.class);

    // when
    final var entityManagerFactory = sut.entityManagerFactory(dataSource);

    // then
    assertThat(entityManagerFactory).isNotNull();
    assertThat(entityManagerFactory.getDataSource()).isEqualTo(dataSource);
    assertThat(entityManagerFactory.getJpaVendorAdapter())
        .isInstanceOf(org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter.class);
  }

  @Test
  void shouldGetConnectorKeyStoreLoader() {
    // given
    final var location = mock(Resource.class);
    final var password = "connectorPassword";

    // when
    final var keyStoreLoader = sut.connectorKeyStoreLoader(location, password);

    // then
    assertThat(keyStoreLoader)
        .isNotNull()
        .hasFieldOrPropertyWithValue("truststoreLocation", location)
        .hasFieldOrPropertyWithValue("truststorePassword", password);
  }

  @Test
  void shouldGetFederationKeyStore() {
    // given
    final var keyStoreLoader = mock(KeyStoreLoader.class);
    final var keyStore = mock(KeyStore.class);
    when(keyStoreLoader.load()).thenReturn(keyStore);

    // when
    final var result = sut.federationKeyStore(keyStoreLoader);

    // then
    assertThat(result).isSameAs(keyStore);
    verify(keyStoreLoader).load();
  }

  @Test
  void shouldGetFederationKeyStoreLoader() {
    // given
    final var location = mock(Resource.class);
    final var password = "password";

    // when
    var keyStoreLoader = sut.federationKeyStoreLoader(location, password);

    // then
    assertThat(keyStoreLoader)
        .isNotNull()
        .hasFieldOrPropertyWithValue("truststoreLocation", location)
        .hasFieldOrPropertyWithValue("truststorePassword", password);
  }
}
