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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

public record ExpectedStatusWords(List<String> values) {

  public static final ExpectedStatusWords SUCCESS = of("9000");
  public static final ExpectedStatusWords SUCCESS_OR_WARNING_6281 = of("9000", "6281");

  public ExpectedStatusWords {
    values = List.copyOf(values);
    if (values.isEmpty()) {
      throw new IllegalArgumentException("Expected status words must not be empty");
    }
  }

  public static ExpectedStatusWords of(final String... values) {
    return new ExpectedStatusWords(Arrays.asList(values));
  }

  public static ExpectedStatusWords of(final List<String> values) {
    Objects.requireNonNull(values, "values");
    return new ExpectedStatusWords(values);
  }

  public boolean contains(final String statusWord) {
    return values.contains(statusWord);
  }

  @Override
  public @NonNull String toString() {
    return values.toString();
  }
}
