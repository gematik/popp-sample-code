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

package de.gematik.refpopp.popp_server.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PoppTokenGenerationStatusConverter
    implements AttributeConverter<PoppTokenGenerationStatus, String> {

  @Override
  public String convertToDatabaseColumn(final PoppTokenGenerationStatus attribute) {
    return attribute != null ? attribute.getValue() : null;
  }

  @Override
  public PoppTokenGenerationStatus convertToEntityAttribute(final String dbData) {
    if (dbData == null) {
      return null;
    }
    for (final PoppTokenGenerationStatus status : PoppTokenGenerationStatus.values()) {
      if (status.getValue().equals(dbData)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown PoPP token generation status: " + dbData);
  }
}
