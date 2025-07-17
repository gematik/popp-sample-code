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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

class BatchFlusher<T> {
  private final int batchSize;
  private final Consumer<List<T>> flushAction;
  private final List<T> buffer = new ArrayList<>();

  BatchFlusher(final int batchSize, final Consumer<List<T>> flushAction) {
    this.batchSize = batchSize;
    this.flushAction = flushAction;
  }

  void addAll(final Collection<T> items) {
    buffer.addAll(items);
    if (buffer.size() >= batchSize) {
      flush();
    }
  }

  void flushRemaining() {
    if (!buffer.isEmpty()) {
      flush();
    }
  }

  boolean hasPending() {
    return !buffer.isEmpty();
  }

  private void flush() {
    flushAction.accept(buffer);
    buffer.clear();
  }
}
