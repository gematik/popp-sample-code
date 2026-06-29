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

package de.gematik.poppcommons.api.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;

public enum BdeErrorCode {
  MISSING_OR_INVALID_HEADER(
      79030, "MISSING_OR_INVALID_HEADER", "The required header '%s' is missing or invalid.", null),

  UNSUPPORTED_MEDIATYPE(
      79031,
      "UNSUPPORTED_MEDIATYPE",
      "The clientsystem asked for an unsupported media type '%s'.",
      null),

  UNSUPPORTED_ENCODING(
      79032,
      "UNSUPPORTED_ENCODING",
      "The clientsystem asked for an unsupported encoding scheme '%s'.",
      null),

  INVALID_HTTP_OPERATION(79040, "INVALID_HTTP_OPERATION", "ERROR", null),

  INVALID_ENDPOINT(79041, "INVALID_ENDPOINT", "ERROR", null),

  SERVICE_INTERNAL_SERVER_ERROR(
      79100, "SERVICE_INTERNAL_SERVER_ERROR", "Unexpected internal server error.", null),

  OCSP_NOTREACHABLE(
      79112, "OCSP_NOTREACHABLE", "Certificate validation services can not be reached", null),

  OCSP_TIMEOUT(79113, "OCSP_TIMEOUT", "Certificate validation services timed out", null),

  INVALID_ACCESSTOKEN(
      79114,
      "INVALID_ACCESSTOKEN",
      "Signature verification of the presented access token failed (FdV)",
      null),

  EXPIRED_ACCESSTOKEN(79115, "EXPIRED_ACCESSTOKEN", "Access token has expired (FdV)", null),

  INVALID_END_ENTITY_CVC(
      79116,
      "InvalidEndEntityCvc",
      "invalid End-Entity-CVC",
      ErrorCategory.ERROR_EGK_HANDLING.value()),

  INVALID_X509(
      79117, "InvalidX509", "eGK C.CH.AUT was invalid", ErrorCategory.ERROR_EGK_BLOCKED.value()),

  INVALID_CERTIFICATE_PAIR_CONTACTLESS(
      79118,
      "InvalidCertificatePairContactless",
      "T=CL: eGK C.CH.AUT did not match the CVC",
      ErrorCategory.ERROR_EGK_HANDLING.value()),

  MISSING_HEADER_CLIENTDATA(
      79205, "MISSING_HEADER_CLIENTDATA", "Header ZTA-Client-Data fehlt.", null),

  MISSING_HEADER_USERINFO(79206, "MISSING_HEADER_USERINFO", "Header ZTA-User-Info fehlt.", null),

  ERROR_HEADER_CLIENTDATA(
      79400, "ERROR_HEADER_CLIENTDATA", "Client-Data Daten können nicht verarbeitet werden.", null),

  ERROR_HEADER_USERINFO(
      79401, "ERROR_HEADER_USERINFO", "User-Info Daten können nicht verarbeitet werden.", null),

  ZETA_DPOP_VALIDATION_ERROR(
      79403, "ZETA_DPOP_VALIDATION_ERROR", "Signature verification of the DPoP-JWT failed", null),

  ZETA_INVALID_ACCESSTOKEN(
      79404,
      "ZETA_INVALID_ACCESSTOKEN",
      "Signature verification of the presented access token failed",
      null),

  ZETA_EXPIRED_ACCESSTOKEN(79405, "ZETA_EXPIRED_ACCESSTOKEN", "Access token has expired", null),

  INVALID_AUTHENTICATION(
      79500,
      "InvalidAuthentication",
      "Authentication of G2.1 contactless failed\nAuthentication of Gx.y failed",
      ErrorCategory.ERROR_EGK_HANDLING.value()),

  INVALID_CERTIFICATE_PAIR_T1(
      79501,
      "InvalidCertificatePairT1",
      "eGK is blocked in Hash-DB",
      ErrorCategory.ERROR_EGK_BLOCKED.value()),

  INVALID_CERTIFICATE_PAIR_TCL(
      79501,
      "InvalidCertificatePairTcl",
      "eGK is blocked in Hash-DB",
      ErrorCategory.ERROR_EGK_BLOCKED.value()),

  INVALID_PI_OBJECT_SYSTEM(
      79502,
      "InvalidPiObjectSystem",
      "eGK product blocked",
      ErrorCategory.ERROR_EGK_BLOCKED.value()),

  INVALID_PTV_OBJECT_SYSTEM(
      79503,
      "InvalidPtvObjectSystem",
      "eGK product type version blocked",
      ErrorCategory.ERROR_EGK_BLOCKED.value()),

  UNEXPECTED_STATUS_WORD_SCE_AUTH_G2(
      79504,
      "UnexpectedStatusWordSceAuthG2",
      "Unexpected status word in scenario SceAuthG2",
      ErrorCategory.ERROR_EGK_HANDLING.value()),

  UNEXPECTED_STATUS_WORD_SCE_AUTH_GXY(
      79505,
      "UnexpectedStatusWordSceAuthGxy",
      "Unexpected status word in scenario SceAuthGx.y",
      ErrorCategory.ERROR_EGK_HANDLING.value()),

  UNEXPECTED_STATUS_WORD_SCE_OPEN_EGK(
      79506,
      "UnexpectedStatusWordSceOpenEgk",
      "Unexpected status word in scenario SceOpenEgk",
      ErrorCategory.ERROR_EGK_HANDLING.value()),

  UNEXPECTED_STATUS_WORD_SCE_READ_CVC(
      79507,
      "UnexpectedStatusWordSceReadCvc",
      "Unexpected status word in scenario SceReadCvc",
      ErrorCategory.ERROR_EGK_HANDLING.value()),

  UNEXPECTED_STATUS_WORD_SCE_READ_X509(
      79508,
      "UnexpectedStatusWordSceReadX509",
      "Unexpected status word in scenario SceReadX.509",
      ErrorCategory.ERROR_EGK_HANDLING.value()),

  UNEXPECTED_STATUS_WORD_SCE_TC1(
      79509,
      "UnexpectedStatusWordSceTC1",
      "Unexpected status word in scenario SceTC1",
      ErrorCategory.ERROR_EGK_HANDLING.value()),

  UNKNOWN_CERTIFICATES(
      79510,
      "UnknownCertificates",
      "Data for eGK G2.1 not in Hash-DB",
      "WarningUnknownCertificates"),

  INVALID_MESSAGE(
      79511,
      "INVALID_MESSAGE",
      "Message received via WebSocket is not in conformance to [I_PoPP_Token_Generation.yaml]",
      "InvalidMessage"),

  RESERVED_79512(79512, "RESERVED_79512", "(reserved for future use)", null),

  UNSUPPORTED_WORKFLOW(
      79513,
      "UNSUPPORTED_WORKFLOW",
      "PoPP-Service received an unexpected message",
      "UnsupportedWorkflow"),

  INVALID_CA_CVC(
      79514, "InvalidCaCvc", "invalid Sub-CA-CVC", ErrorCategory.ERROR_EGK_HANDLING.value()),

  BLOCK_ENTRIES(79515, "BlockEntries", "entries in the eGK-Hash-Datenbank have been blocked", null);

  private static final Map<String, BdeErrorCode> BY_REFERENCE =
      Arrays.stream(values())
          .collect(Collectors.toUnmodifiableMap(BdeErrorCode::getReference, Function.identity()));

  private static final Map<Integer, List<BdeErrorCode>> BY_BDE_CODE =
      Arrays.stream(values())
          .collect(
              Collectors.groupingBy(BdeErrorCode::getBdeCode, Collectors.toUnmodifiableList()));

  @Getter private final int bdeCode;
  @Getter private final String reference;
  @Getter private final String description;
  private final String errorCode;

  BdeErrorCode(int bdeCode, String reference, String description, String errorCode) {
    this.bdeCode = bdeCode;
    this.reference = reference;
    this.description = description;
    this.errorCode = errorCode;
  }

  public Optional<String> getErrorCode() {
    return Optional.ofNullable(errorCode);
  }

  public boolean hasErrorCode() {
    return errorCode != null && !errorCode.isBlank();
  }

  public boolean isReserved() {
    return this == RESERVED_79512;
  }

  public static Optional<BdeErrorCode> fromReference(String reference) {
    if (reference == null || reference.isBlank()) {
      return Optional.empty();
    }

    return Optional.ofNullable(BY_REFERENCE.get(reference));
  }

  public static List<BdeErrorCode> fromBdeCode(int bdeCode) {
    return BY_BDE_CODE.getOrDefault(bdeCode, List.of());
  }

  public String messageWith(Object... args) {
    return String.format(description, args);
  }
}
