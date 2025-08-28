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

package de.gematik.refpopp.popp_client.configuration.helper;

import java.security.cert.CertificateException;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * A trust manager that wraps another X509ExtendedTrustManager but does not perform hostname
 * validation. This is useful when you want to disable hostname verification while still using a
 * custom trust manager.
 */
public class NoHostNameValidationWrappedTrustManager extends X509ExtendedTrustManager {

  private final X509ExtendedTrustManager delegate;

  public NoHostNameValidationWrappedTrustManager(X509ExtendedTrustManager delegate) {
    this.delegate = delegate;
  }

  @Override
  public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a)
      throws CertificateException {
    delegate.checkClientTrusted(c, a);
  }

  @Override
  public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a)
      throws CertificateException {
    delegate.checkServerTrusted(c, a);
  }

  @Override
  public void checkClientTrusted(
      java.security.cert.X509Certificate[] c, String a, java.net.Socket s)
      throws CertificateException {
    delegate.checkClientTrusted(c, a);
  }

  @Override
  public void checkServerTrusted(
      java.security.cert.X509Certificate[] c, String a, java.net.Socket s)
      throws CertificateException {
    delegate.checkServerTrusted(c, a);
  }

  @Override
  public void checkClientTrusted(
      java.security.cert.X509Certificate[] c, String a, javax.net.ssl.SSLEngine e)
      throws CertificateException {
    delegate.checkClientTrusted(c, a);
  }

  @Override
  public void checkServerTrusted(
      java.security.cert.X509Certificate[] c, String a, javax.net.ssl.SSLEngine e)
      throws CertificateException {
    delegate.checkServerTrusted(c, a);
  }

  @Override
  public java.security.cert.X509Certificate[] getAcceptedIssuers() {
    return delegate.getAcceptedIssuers();
  }
}
