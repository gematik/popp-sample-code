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

import de.gematik.refpopp.popp_client.connector.RealConnectorCommunicationService;
import de.gematik.zeta.sdk.authentication.smcb.CustomConnectorApi;
import kotlin.coroutines.Continuation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Adapts the application's connector communication service to Zeta SDK SMC-B authentication.
 *
 * <p>The adapter obtains the connected SMC-B card handle to read its certificate and to perform
 * external authentication after PIN verification.
 */
@Slf4j
@Component
@AllArgsConstructor
public class CustomConnectorApiZeta implements CustomConnectorApi {
  @Lazy private final RealConnectorCommunicationService realConnectorCommunicationService;

  @Override
  public byte @Nullable [] readCertificate(@NonNull Continuation<? super byte[]> continuation) {
    String cardHandle = realConnectorCommunicationService.getConnectedSmcbCard();
    return realConnectorCommunicationService.readCardCertificate(cardHandle);
  }

  @Override
  public byte @Nullable [] externalAuthenticate(
      @NonNull String base64Challenge, @NonNull Continuation<? super byte[]> continuation) {
    String cardHandle = realConnectorCommunicationService.getConnectedSmcbCard();
    realConnectorCommunicationService.verifyPin(cardHandle);
    return realConnectorCommunicationService.externalAuthenticate(base64Challenge, cardHandle);
  }
}
