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

import de.gematik.ws.conn.servicedirectory.ConnectorServices;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Optional;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Configuration
@DependsOn("bouncyCastleConfiguration")
public class ConnectorConfiguration {

  @Bean
  public Optional<SSLContext> sslContext(
      @Value("${connector.secure.enable}") final boolean sslIsEnabled,
      @Value("${connector.secure.keystore}") final String keystorePath,
      @Value("${connector.secure.keystore-password}") final String keystorePassword,
      @Value("${connector.secure.truststore}") final String truststorePath,
      @Value("${connector.secure.truststore-password}") final String truststorePassword)
      throws NoSuchAlgorithmException,
          KeyStoreException,
          IOException,
          CertificateException,
          KeyManagementException,
          NoSuchProviderException,
          UnrecoverableKeyException {
    if (!sslIsEnabled) {
      return Optional.empty();
    }
    System.setProperty("jsse.ec.curves", "brainpoolP256r1,brainpoolP384r1,brainpoolP512r1");
    KeyStore keyStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
    try (InputStream keyStoreStream =
        getClass().getClassLoader().getResourceAsStream(keystorePath)) {
      keyStore.load(keyStoreStream, keystorePassword.toCharArray());
    }

    KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
    kmf.init(keyStore, keystorePassword.toCharArray());

    KeyStore trustStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
    try (InputStream trustStoreStream =
        getClass().getClassLoader().getResourceAsStream(truststorePath)) {
      trustStore.load(trustStoreStream, truststorePassword.toCharArray());
    }

    TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
    tmf.init(trustStore);

    SSLContext sslContext =
        SSLContext.getInstance("TLSv1.2", BouncyCastleJsseProvider.PROVIDER_NAME);
    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

    return Optional.of(sslContext);
  }

  @Bean
  public RestTemplate restTemplate(
      Optional<SSLContext> sslContext,
      @Value("${connector.secure.hostname-validation}") final boolean hostnameValidationIsEnabled) {
    if (sslContext.isEmpty()) {
      return new RestTemplate();
    }
    TlsSocketStrategy tlsStrategy =
        ClientTlsStrategyBuilder.create()
            .setSslContext(sslContext.get())
            .setHostnameVerifier(
                hostnameValidationIsEnabled
                    ? new DefaultHostnameVerifier()
                    : NoopHostnameVerifier.INSTANCE)
            .buildClassic();

    PoolingHttpClientConnectionManager connectionManager =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setTlsSocketStrategy(tlsStrategy)
            .build();

    HttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();

    HttpComponentsClientHttpRequestFactory requestFactory =
        new HttpComponentsClientHttpRequestFactory(httpClient);
    return new RestTemplate(requestFactory);
  }

  @Bean
  public JAXBContext jaxbContext() {
    try {
      return JAXBContext.newInstance(ConnectorServices.class);
    } catch (final JAXBException e) {
      throw new IllegalStateException("Failed to create JAXBContext", e);
    }
  }

  @Bean
  public Jaxb2Marshaller eventServiceMarshaller() {
    final Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
    marshaller.setContextPath("de.gematik.ws.conn.eventservice.v7");
    return marshaller;
  }
}
