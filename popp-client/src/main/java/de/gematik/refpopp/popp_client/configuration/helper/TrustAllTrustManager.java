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

import java.net.Socket;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLEngine;
import org.bouncycastle.jsse.BCX509ExtendedTrustManager;

public class TrustAllTrustManager extends BCX509ExtendedTrustManager {
  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType) {}

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType) {}

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {}

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {}

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return new X509Certificate[0];
  }
}
