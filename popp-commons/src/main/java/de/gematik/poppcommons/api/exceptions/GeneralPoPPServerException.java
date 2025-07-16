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

package de.gematik.poppcommons.api.exceptions;

import java.io.Serial;
import lombok.Getter;

@Getter
public abstract class GeneralPoPPServerException extends RuntimeException {

  @Serial private static final long serialVersionUID = -3455896777160551403L;

  protected String exceptionTypeName;
  private final String errorCode;

  protected GeneralPoPPServerException(final String message, final String errorCode) {
    super(message);
    this.errorCode = errorCode;
  }
}
