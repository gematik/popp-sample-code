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

package de.gematik.refpopp.popp_client.client;

import de.gematik.zeta.sdk.WsClientExtension;
import de.gematik.zeta.sdk.ZetaSdkClient;
import de.gematik.zeta.sdk.network.http.client.ZetaHttpClientBuilder;
import java.util.Map;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.springframework.stereotype.Component;

@Component
public class WsClientWrapper {

  void ws(
      ZetaSdkClient zetaSdk,
      String targetUrl,
      Function1<ZetaHttpClientBuilder, Unit> builder,
      Map<String, String> customHeaders,
      WsClientExtension.WsSession.WsHandler handler) {
    WsClientExtension.ws(zetaSdk, targetUrl, builder, customHeaders, handler);
  }
}
