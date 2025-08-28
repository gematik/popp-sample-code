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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.gematik.refpopp.popp_server.certificates.CertificateProviderService;
import de.gematik.refpopp.popp_server.certificates.KeyStoreLoader;
import de.gematik.smartcards.g2icc.cos.SecureMessagingConverterSoftware;
import java.security.KeyStore;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

@Configuration
public class ServiceConfiguration {

  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }

  @Bean
  SecureMessagingConverterSoftware secureMessagingConverterSoftware(
      final CertificateProviderService certificateProviderService) {
    return new SecureMessagingConverterSoftware(
        certificateProviderService.getCvcSubCertificate(),
        certificateProviderService.getCvEndEntityCertificate(),
        certificateProviderService.getCvcPoppServicePrk());
  }

  @Bean
  public LocalContainerEntityManagerFactoryBean entityManagerFactory(final DataSource dataSource) {
    final var emf = new LocalContainerEntityManagerFactoryBean();
    emf.setDataSource(dataSource);
    emf.setPackagesToScan("de/gematik/refpopp/popp_server/model");
    emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    emf.setEntityManagerFactoryInterface(jakarta.persistence.EntityManagerFactory.class);
    return emf;
  }

  @Bean("hashImportKeyStoreLoader")
  public KeyStoreLoader hashImportKeyStoreLoader(
      @Value("${certificates.hash-import-keystore}") final Resource location,
      @Value("${certificates.hash-import-keystore-password}") final String password) {
    return new KeyStoreLoader(location, password);
  }

  @Bean("poppKeyStoreLoader")
  public KeyStoreLoader trustKeyStoreLoader(
      @Value("${certificates.popp-keystore}") final Resource location,
      @Value("${certificates.popp-keystore-password}") final String password) {
    return new KeyStoreLoader(location, password);
  }

  @Bean("connectorKeyStoreLoader")
  public KeyStoreLoader connectorKeyStoreLoader(
      @Value("${certificates.connector-keystore}") final Resource location,
      @Value("${certificates.connector-keystore-password}") final String password) {
    return new KeyStoreLoader(location, password);
  }

  @Bean("hashKeyStore")
  public KeyStore hashKeyStore(@Qualifier("hashImportKeyStoreLoader") final KeyStoreLoader loader) {
    return loader.load();
  }

  @Bean("connectorKeyStore")
  public KeyStore connectorKeyStore(
      @Qualifier("connectorKeyStoreLoader") final KeyStoreLoader loader) {
    return loader.load();
  }

  @Bean("poppKeyStore")
  public KeyStore poppKeyStore(@Qualifier("poppKeyStoreLoader") final KeyStoreLoader loader) {
    return loader.load();
  }
}
