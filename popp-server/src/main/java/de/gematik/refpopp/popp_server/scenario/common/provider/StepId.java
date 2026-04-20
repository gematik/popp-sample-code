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

package de.gematik.refpopp.popp_server.scenario.common.provider;

public enum StepId {
  SELECT_MASTER_FILE("select-master-file", ExpectedStatusWords.SUCCESS),
  READ_VERSION("read-version", ExpectedStatusWords.SUCCESS_OR_WARNING_6281),
  READ_SUB_CA_CV_CERTIFICATE(
      "read-sub-ca-cv-certificate", ExpectedStatusWords.SUCCESS_OR_WARNING_6281),
  READ_END_ENTITY_CV_CERTIFICATE(
      "read-end-entity-cv-certificate", ExpectedStatusWords.SUCCESS_OR_WARNING_6281),
  RETRIEVE_PUBLIC_KEY_IDENTIFIERS("retrieve-public-key-identifiers", ExpectedStatusWords.SUCCESS),
  SELECT_PRIVATE_KEY("select-private-key", ExpectedStatusWords.SUCCESS),
  MUTUAL_AUTHENTICATION_STEP_1("mutual-authentication-step-1", ExpectedStatusWords.SUCCESS),
  MUTUAL_AUTHENTICATION_STEP_2("mutual-authentication-step-2", ExpectedStatusWords.SUCCESS),
  SELECT_DF_ESIGN("select-df-esign", ExpectedStatusWords.SUCCESS),
  READ_EF_C_CH_AUT_E256("read-ef-c-ch-aut-e256", ExpectedStatusWords.SUCCESS_OR_WARNING_6281),
  READ_X509("read-x509", ExpectedStatusWords.SUCCESS_OR_WARNING_6281),
  INTERNAL_AUTHENTICATION("internal-authentication", ExpectedStatusWords.SUCCESS),
  MSE_APDU("mse-apdu", ExpectedStatusWords.SUCCESS),
  PSO_APDU("pso-apdu", ExpectedStatusWords.SUCCESS);

  private final String value;
  private final ExpectedStatusWords expectedStatusWords;

  StepId(final String value, final ExpectedStatusWords expectedStatusWords) {
    this.value = value;
    this.expectedStatusWords = expectedStatusWords;
  }

  public String value() {
    return value;
  }

  public ExpectedStatusWords expectedStatusWords() {
    return expectedStatusWords;
  }
}
