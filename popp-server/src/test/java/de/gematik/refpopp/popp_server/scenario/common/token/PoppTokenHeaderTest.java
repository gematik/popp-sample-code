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
import static org.mockito.Mockito.*;

import de.gematik.poppcommons.api.exceptions.ScenarioException;
import de.gematik.refpopp.popp_server.security.jwk.JwkKidGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.util.Map;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

class PoppTokenHeaderTest {

  private PoppTokenHeader sut;

  @Mock private ECPublicKey mockPublicKey;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ECPoint mockEcPoint;

  @Mock private JwkKidGenerator jwkKidGenerator;

  private AutoCloseable autoCloseable;

  @BeforeEach
  void setUp() {
    autoCloseable = MockitoAnnotations.openMocks(this);
    sut = new PoppTokenHeader(jwkKidGenerator);
    ReflectionTestUtils.setField(sut, "poppTokenType", "vnd.telematik.popp+jwt");

    when(mockPublicKey.getW()).thenReturn(mockEcPoint);
  }

  @AfterEach
  void tearDown() throws Exception {
    autoCloseable.close();
  }

  @Test
  void createHeaderForPoppToken() throws JoseException {
    // given
    final byte[] xBytes = "dummy-x".getBytes();
    final byte[] yBytes = "dummy-y".getBytes();
    when(mockEcPoint.getAffineX().toByteArray()).thenReturn(xBytes);
    when(mockEcPoint.getAffineY().toByteArray()).thenReturn(yBytes);
    when(jwkKidGenerator.generate(any())).thenReturn("my_kid");

    final var sessionId = "test-session";

    // when
    final Map<String, Object> header = sut.createPoppHeader(mockPublicKey, sessionId);

    // then
    assertThat(header)
        .isNotNull()
        .hasSize(2)
        .containsEntry("typ", "vnd.telematik.popp+jwt")
        .containsEntry("kid", "my_kid");
    verify(jwkKidGenerator).generate(mockPublicKey);
  }

  @Test
  void createHeaderForPoppTokenThrowsScenarioExceptionWhenKidGenerationFails()
      throws JoseException {
    // given
    final var sessionId = "test-session";
    when(jwkKidGenerator.generate(mockPublicKey)).thenThrow(new JoseException("kid failed"));

    // when & then
    final var exception =
        assertThrows(ScenarioException.class, () -> sut.createPoppHeader(mockPublicKey, sessionId));

    assertThat(exception.getMessage()).isEqualTo("Could not create kid");
    verify(jwkKidGenerator).generate(mockPublicKey);
  }
}
