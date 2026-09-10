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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ClassPathResource;

class ClasspathOcspResponseProviderTest {

  @Test
  void getResponseReadsAndEncodesClasspathResource() {
    // given
    final var sut = new ClasspathOcspResponseProvider(new ClassPathResource("ocsp-response.txt"));

    // when
    final var response = sut.getResponse(createRequest());

    // then
    assertThat(response).isEqualTo("dmFsaWQ=");
  }

  @Test
  void getResponseThrowsScenarioExceptionWhenResourceCannotBeRead() {
    // given
    final var sut =
        new ClasspathOcspResponseProvider(new ClassPathResource("missing-ocsp-response.txt"));
    final var request = createRequest();

    // when & then
    final var exception = assertThrows(ScenarioException.class, () -> sut.getResponse(request));
    assertThat(exception.getMessage())
        .isEqualTo(
            "Could not read OCSP response from class path resource [missing-ocsp-response.txt]");
  }

  private OcspRequest createRequest() {
    return OcspRequest.withoutIssuer("test-session", Mockito.mock(X509Certificate.class));
  }
}
