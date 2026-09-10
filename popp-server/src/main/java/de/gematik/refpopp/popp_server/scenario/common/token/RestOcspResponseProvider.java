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

package de.gematik.refpopp.popp_server.scenario.common.token;

import de.gematik.pki.gemlibpki.commons.exception.GemPkiException;
import de.gematik.poppcommons.api.enums.BdeErrorCode;
import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.config.OcspProperties;
import de.gematik.refpopp.popp_server.sessionmanagement.SessionContainer;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Retrieves a binary DER-encoded OCSP response from the responder in the certificate AIA. */
@Component
@ConditionalOnProperty(
    name = "certificates.ocsp.provider",
    havingValue = "rest",
    matchIfMissing = true)
@Slf4j
final class RestOcspResponseProvider implements OcspResponseProvider {

  private static final String PRODUCT_TYPE = "popp-server";

  private final OcspResponderUrlExtractor responderUrlExtractor;
  private final OcspTransceiverClient ocspTransceiverClient;
  private final SessionContainer sessionContainer;
  private final int timeoutSeconds;
  private final Supplier<OcspResponseProvider> classpathFallbackProviderSupplier;

  @Autowired
  RestOcspResponseProvider(
      final OcspResponderUrlExtractor responderUrlExtractor,
      final OcspTransceiverClient ocspTransceiverClient,
      final SessionContainer sessionContainer,
      final OcspProperties properties) {
    this(
        responderUrlExtractor,
        ocspTransceiverClient,
        sessionContainer,
        properties.timeoutSeconds(),
        properties.getRest().getFallback().getResource() == null
            ? null
            : () ->
                new ClasspathOcspResponseProvider(
                    properties.getRest().getFallback().getResource()));
  }

  RestOcspResponseProvider(
      final OcspResponderUrlExtractor responderUrlExtractor,
      final OcspTransceiverClient ocspTransceiverClient,
      final SessionContainer sessionContainer,
      final int timeoutSeconds,
      final Supplier<OcspResponseProvider> classpathFallbackProviderSupplier) {
    this.responderUrlExtractor = responderUrlExtractor;
    this.ocspTransceiverClient = ocspTransceiverClient;
    this.sessionContainer = sessionContainer;
    this.timeoutSeconds = timeoutSeconds;
    this.classpathFallbackProviderSupplier = classpathFallbackProviderSupplier;
  }

  @Override
  public String getResponse(final OcspRequest request) {
    return sessionContainer.computeSessionDataIfAbsent(
        request.sessionId(),
        SessionContainer.SessionStorageKey.OCSP_RESPONSE,
        () -> retrieveResponse(request));
  }

  private String retrieveResponse(final OcspRequest request) {
    final var issuerCertificate =
        request
            .issuerCertificate()
            .orElseThrow(
                () ->
                    new ScenarioException(
                        request.sessionId(),
                        "Missing issuer certificate",
                        BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR));

    try {
      final var responderUrl =
          responderUrlExtractor
              .extract(request.endEntityCertificate())
              .orElseThrow(
                  () ->
                      new ScenarioException(
                          request.sessionId(),
                          "Missing OCSP responder URL in certificate",
                          BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR));

      log.info("| Retrieving response from OCSP responder for session {}", request.sessionId());
      final var response =
          getOcspResponseWithRetry(request, issuerCertificate, responderUrl)
              .orElseThrow(
                  () ->
                      new ScenarioException(
                          request.sessionId(),
                          "Received empty OCSP response",
                          BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR));
      final var encodedResponse = ocspTransceiverClient.getEncoded(response);
      return Base64.getEncoder().encodeToString(encodedResponse);
    } catch (IOException e) {
      throw new ScenarioException(
          request.sessionId(),
          "Could not encode OCSP response",
          BdeErrorCode.SERVICE_INTERNAL_SERVER_ERROR,
          e);
    } catch (GemPkiException e) {
      if (classpathFallbackProviderSupplier != null) {
        log.warn(
            "| OCSP response provider is not reachable for session {}. Using classpath fallback",
            request.sessionId());
        return classpathFallbackProviderSupplier.get().getResponse(request);
      }
      throw new ScenarioException(
          request.sessionId(),
          "OCSP response provider is not reachable",
          BdeErrorCode.OCSP_TIMEOUT,
          e);
    }
  }

  private Optional<OCSPResp> getOcspResponseWithRetry(
      final OcspRequest request, final X509Certificate issuerCertificate, final String responderUrl)
      throws GemPkiException {
    try {
      return getOcspResponse(request, issuerCertificate, responderUrl);
    } catch (GemPkiException ignored) {
      log.warn(
          "| OCSP response provider is not reachable for session {}. Retrying once",
          request.sessionId());
      return getOcspResponse(request, issuerCertificate, responderUrl);
    }
  }

  private Optional<OCSPResp> getOcspResponse(
      final OcspRequest request, final X509Certificate issuerCertificate, final String responderUrl)
      throws GemPkiException {
    return ocspTransceiverClient.getOcspResponse(
        request.endEntityCertificate(),
        issuerCertificate,
        responderUrl,
        PRODUCT_TYPE,
        timeoutSeconds);
  }
}
