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

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class BatchFlusherTest {

  @Test
  void shouldFlushWhenBatchSizeIsReached() {
    // given
    final int batchSize = 3;
    final BatchFlusher<Integer> batchFlusher = new BatchFlusher<>(batchSize, doNothing());

    // when
    batchFlusher.addAll(List.of(1, 2, 3));

    // then
    assertThat(batchFlusher.hasPending()).isFalse();
  }

  @Test
  void shouldNotFlushWhenBatchSizeIsNotReached() {
    // given
    final int batchSize = 3;
    final BatchFlusher<Integer> batchFlusher = new BatchFlusher<>(batchSize, doNothing());

    // when
    batchFlusher.addAll(List.of(1, 2));

    // then
    assertThat(batchFlusher.hasPending()).isTrue();
  }

  @Test
  void shouldFlushRemainingWhenItemsAreLeft() {
    // given
    final int batchSize = 3;
    final BatchFlusher<Integer> batchFlusher = new BatchFlusher<>(batchSize, doNothing());

    batchFlusher.addAll(List.of(1, 2));

    // when
    batchFlusher.flushRemaining();

    // then
    assertThat(batchFlusher.hasPending()).isFalse();
  }

  private Consumer<List<Integer>> doNothing() {
    return batch -> {
      // No operation
    };
  }
}
