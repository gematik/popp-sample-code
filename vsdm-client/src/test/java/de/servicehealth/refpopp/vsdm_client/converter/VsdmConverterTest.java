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

package de.servicehealth.refpopp.vsdm_client.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class VsdmConverterTest {

  private VsdmConverter converter;
  private VsdmSoapBuilder soapBuilder;

  private static final String VALID_FHIR_XML_SIMPLE =
      """
      <Bundle xmlns="http://hl7.org/fhir">
        <entry>
          <resource>
            <Patient>
              <identifier>
                <value value="X123456789"/>
              </identifier>
              <name>
                <family value="Mustermann"/>
                <given value="Max"/>
              </name>
              <birthDate value="1980-01-01"/>
              <gender value="male"/>
              <address>
                <city value="Berlin"/>
                <postalCode value="10117"/>
              </address>
            </Patient>
          </resource>
        </entry>
        <entry>
          <resource>
            <Coverage>
              <status value="active"/>
            </Coverage>
          </resource>
        </entry>
      </Bundle>
      """;

  @BeforeEach
  void setUp() {
    soapBuilder = new VsdmSoapBuilder();
    converter = new VsdmConverter(new VsdmFhirParser(), soapBuilder);
  }

  @Test
  @DisplayName("Should create valid response from a simple, hardcoded FHIR XML string")
  void createReadVSDResponse_shouldReturnValidResponse_whenFhirXmlIsValid() {
    ReadVSDResponse response = converter.createReadVSDResponse(VALID_FHIR_XML_SIMPLE, "test-token");

    assertThat(response).isNotNull();
    assertThat(response.getVSDStatus()).isNotNull();
    assertThat(response.getVSDStatus().getStatus()).isEqualTo("0");
    assertThat(response.getPersoenlicheVersichertendaten()).isNotEmpty();
    assertThat(response.getAllgemeineVersicherungsdaten()).isNotEmpty();
    assertThat(response.getGeschuetzteVersichertendaten()).isNotEmpty();
    assertThat(response.getPruefungsnachweis()).isNotEmpty();
  }

  @ParameterizedTest(name = "File: {0}")
  @MethodSource("fhirBundleFileProvider")
  @DisplayName("Should create valid response for all files in fhir_bundle directory")
  void createReadVSDResponse_shouldReturnValidResponse_whenReadingFromResourceFile(
      String resourceName) throws IOException {

    System.out.println("Testing with resource file: " + resourceName);
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
      assertThat(is).as("Resource %s not found", resourceName).isNotNull();
      String fhirXml = new String(is.readAllBytes(), StandardCharsets.UTF_8);

      ReadVSDResponse response = converter.createReadVSDResponse(fhirXml, "test-token");

      assertThat(response).isNotNull();
      assertThat(response.getVSDStatus()).isNotNull();
      assertThat(response.getVSDStatus().getStatus()).isEqualTo("0");
      assertThat(response.getPersoenlicheVersichertendaten()).isNotEmpty();
    }
  }

  @Test
  @DisplayName("Should throw VsdmProcessingException for invalid XML content")
  void createReadVSDResponse_shouldThrowException_forInvalidXml() {
    String invalidXml = "<Bundle><Patient>...<Patient></Bundle>"; // Malformed XML

    VsdmProcessingException exception =
        assertThrows(
            VsdmProcessingException.class,
            () -> {
              converter.createReadVSDResponse(invalidXml, "test-token");
            });

    assertThat(exception.getMessage()).contains("Error parsing FHIR XML bundle");
  }

  @Test
  @DisplayName("Should generate and print full SOAP response")
  void createReadVSDResponse_shouldPrintSoapResponse() {
    ReadVSDResponse response = converter.createReadVSDResponse(VALID_FHIR_XML_SIMPLE, "test-token");
    String soapXml = soapBuilder.marshallToSoapString(response);

    System.out.println("Generated SOAP Response:");
    System.out.println(soapXml);

    assertThat(soapXml).isNotBlank();
    assertThat(soapXml).contains("http://schemas.xmlsoap.org/soap/envelope/");
    assertThat(soapXml).contains("ReadVSDResponse");
  }

  /** Provides a stream of all .xml files in the fhir_bundle test resource directory. */
  private static Stream<String> fhirBundleFileProvider() {
    String resourceDir = "fhir_bundle";
    try (InputStream in =
            VsdmConverterTest.class.getClassLoader().getResourceAsStream(resourceDir);
        BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
      List<String> resourceFiles =
          br.lines()
              .filter(line -> line.endsWith(".xml"))
              .map(line -> resourceDir + "/" + line)
              .collect(Collectors.toList());
      assertThat(resourceFiles).as("No XML files found in fhir_bundle directory").isNotEmpty();
      return resourceFiles.stream();
    } catch (Exception e) {
      throw new RuntimeException("Could not read resource directory: " + resourceDir, e);
    }
  }
}
