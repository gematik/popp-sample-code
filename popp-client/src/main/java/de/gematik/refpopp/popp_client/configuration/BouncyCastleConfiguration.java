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

import de.gematik.refpopp.popp_client.configuration.helper.NoHostNameValidationWrappedTrustManager;
import de.gematik.refpopp.popp_client.configuration.helper.TrustAllTrustManager;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BouncyCastleConfiguration {

  @PostConstruct
  public void registerBouncyCastle() {
    Security.insertProviderAt(new BouncyCastleProvider(), 1);
    Security.insertProviderAt(new BouncyCastleJsseProvider(), 2);
    log.info("Bouncy Castle registered as primary provider.");

    if (ECNamedCurveTable.getParameterSpec("brainpoolP256r1") != null) {
      log.info("BrainpoolP256r1 curve is available.");
    } else {
      log.info("BrainpoolP256r1 NOT found!");
    }
  }

  @Bean("httpClientWithBC")
  @ConditionalOnProperty(prefix = "connector.secure", name = "enable", havingValue = "true")
  public HttpClient sslContext(
      @Value("${connector.secure.enable}") final boolean sslIsEnabled,
      @Value("${connector.secure.hostname-validation}") final boolean hostnameValidationIsEnabled,
      @Value("${connector.secure.keystore}") final String keystorePath,
      @Value("${connector.secure.keystore-password}") final String keystorePassword,
      @Value("${connector.secure.trust-all}") final boolean trustAll,
      @Value("${connector.secure.truststore}") final String truststorePath,
      @Value("${connector.secure.truststore-password}") final String truststorePassword)
      throws NoSuchAlgorithmException,
          KeyStoreException,
          IOException,
          CertificateException,
          KeyManagementException,
          NoSuchProviderException,
          UnrecoverableKeyException {
    System.setProperty(
        "jdk.tls.namedGroups",
        "x25519, secp256r1, secp384r1, secp521r1, x448, ffdhe2048, ffdhe3072,ffdhe4096,ffdhe6144,"
            + " ffdhe8192, brainpoolP256r1, brainpoolP384r1, brainpoolP512r1");

    KeyStore keyStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
    try (InputStream keyStoreStream =
        getClass().getClassLoader().getResourceAsStream(keystorePath)) {
      keyStore.load(keyStoreStream, keystorePassword.toCharArray());
    }

    KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
    kmf.init(keyStore, keystorePassword.toCharArray());

    TrustManager[] trustManagers = new TrustManager[] {new TrustAllTrustManager()};
    if (!trustAll) {
      KeyStore trustStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
      try (InputStream trustStoreStream =
          getClass().getClassLoader().getResourceAsStream(truststorePath)) {
        trustStore.load(trustStoreStream, truststorePassword.toCharArray());
      }

      TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
      tmf.init(trustStore);
      trustManagers = tmf.getTrustManagers();
      if (!hostnameValidationIsEnabled) {
        // Wrap to disable endpoint ID checks but keep chain validation
        X509ExtendedTrustManager delegate =
            (X509ExtendedTrustManager)
                java.util.Arrays.stream(tmf.getTrustManagers())
                    .filter(tm -> tm instanceof javax.net.ssl.X509ExtendedTrustManager)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No X509ExtendedTrustManager"));

        trustManagers = new TrustManager[] {new NoHostNameValidationWrappedTrustManager(delegate)};
      }
    }
    SSLContext sslContext =
        SSLContext.getInstance("TLSv1.2", BouncyCastleJsseProvider.PROVIDER_NAME);
    sslContext.init(kmf.getKeyManagers(), trustManagers, null);

    TlsSocketStrategy tlsSocketStrategy =
        ClientTlsStrategyBuilder.create()
            .setSslContext(sslContext)
            .setHostnameVerifier(
                hostnameValidationIsEnabled
                    ? new DefaultHostnameVerifier()
                    : NoopHostnameVerifier.INSTANCE)
            .buildClassic();

    PoolingHttpClientConnectionManager connectionManager =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setTlsSocketStrategy(tlsSocketStrategy)
            .build();

    return HttpClients.custom()
        .setConnectionManager(connectionManager)
        // In order to prevent an I/O error: Content-Length header already present
        .addRequestInterceptorFirst(
            (httpRequest, entity, context) -> httpRequest.removeHeaders("Content-Length"))
        .build();
  }
}
