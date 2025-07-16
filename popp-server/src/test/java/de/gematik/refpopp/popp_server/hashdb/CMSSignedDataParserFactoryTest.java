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

package de.gematik.refpopp.popp_server.hashdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.exceptions.ImportDataException;
import java.io.ByteArrayInputStream;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;

class CMSSignedDataParserFactoryTest {

  private static final String SESSION_ID = "test-session";
  private CMSSignedDataParserFactory sut;

  @BeforeEach
  void setUp() {
    sut = new CMSSignedDataParserFactory();
  }

  @Test
  void createParserReturnsParserInstanceWhenConstructionSucceeds() {
    // given
    try (final MockedConstruction<CMSSignedDataParser> mocked =
        mockConstruction(CMSSignedDataParser.class)) {
      final var input = new ByteArrayInputStream(new byte[0]);

      // when
      final var parser = sut.createParser(input, SESSION_ID);

      // then
      assertThat(parser).isNotNull();
      assertThat(mocked.constructed()).hasSize(1);
    }
  }

  @Test
  void createParserThrowsImportDataExceptionWhenParserConstructorThrowsCMSException() {
    // given
    final var input = new ByteArrayInputStream(new byte[0]);

    // when / then
    assertThatThrownBy(() -> sut.createParser(input, SESSION_ID))
        .isInstanceOf(ImportDataException.class)
        .hasMessageContaining("Failed to create CMSSignedDataParser");
  }

  @Test
  void createParserThrowsImportDataExceptionWhenDigestProviderBuildFails() throws Exception {
    // given
    final var mockBuilder = mock(JcaDigestCalculatorProviderBuilder.class);
    when(mockBuilder.setProvider(anyString())).thenReturn(mockBuilder);
    when(mockBuilder.build()).thenThrow(new OperatorCreationException("op-fail"));

    ReflectionTestUtils.setField(sut, "digestProviderBuilder", mockBuilder);

    // when
    final var input = new ByteArrayInputStream(new byte[0]);

    // then
    assertThatThrownBy(() -> sut.createParser(input, SESSION_ID))
        .isInstanceOf(ImportDataException.class)
        .hasMessageContaining("Failed to create CMSSignedDataParser");
  }
}
