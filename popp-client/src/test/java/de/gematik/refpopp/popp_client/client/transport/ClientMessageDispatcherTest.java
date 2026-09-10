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

package de.gematik.refpopp.popp_client.client.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientMessageDispatcherTest {

  private ClientMessageDispatcher sut;

  @BeforeEach
  void setUp() {
    // given
    sut = new ClientMessageDispatcher(4);
  }

  @AfterEach
  void tearDown() {
    sut.shutdown();
  }

  @Test
  void dispatchProcessesTasksInOrderForSameKey() throws InterruptedException {
    // given
    final List<Integer> order = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch done = new CountDownLatch(2);
    final CountDownLatch firstStarted = new CountDownLatch(1);
    final CountDownLatch firstContinue = new CountDownLatch(1);

    // when
    sut.dispatch(
        "key1",
        () -> {
          // signal that the first task has started and then wait until the test allows it to
          firstStarted.countDown();
          try {
            firstContinue.await();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          order.add(1);
          done.countDown();
        });

    sut.dispatch(
        "key1",
        () -> {
          order.add(2);
          done.countDown();
        });

    // ensure the first task actually started (is waiting) before releasing it
    final boolean started = firstStarted.await(2, TimeUnit.SECONDS);
    assertThat(started).isTrue();
    // allow first task to continue and finish
    firstContinue.countDown();

    // then
    final boolean finished = done.await(2, TimeUnit.SECONDS);
    assertThat(finished).isTrue();
    assertThat(order).containsExactly(1, 2);
  }

  @Test
  void dispatchRunsDifferentKeysInParallel() throws InterruptedException {
    // given
    final List<String> seen = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch start = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(2);

    // when
    sut.dispatch(
        "A",
        () -> {
          try {
            start.await();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          seen.add("A");
          done.countDown();
        });

    sut.dispatch(
        "B",
        () -> {
          try {
            start.await();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          seen.add("B");
          done.countDown();
        });

    // release both tasks at the same time to encourage parallel execution
    start.countDown();

    // then
    final boolean finished = done.await(2, TimeUnit.SECONDS);
    assertThat(finished).isTrue();
    assertThat(seen).containsExactlyInAnyOrder("A", "B");
  }

  @Test
  void nullAndBlankOrderingKeyUseSameDefaultChain() throws InterruptedException {
    // given
    final List<Integer> order = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch done = new CountDownLatch(2);
    final CountDownLatch firstStarted = new CountDownLatch(1);
    final CountDownLatch firstContinue = new CountDownLatch(1);

    // when
    sut.dispatch(
        null,
        () -> {
          firstStarted.countDown();
          try {
            firstContinue.await();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          order.add(1);
          done.countDown();
        });

    sut.dispatch(
        "",
        () -> {
          order.add(2);
          done.countDown();
        });

    // ensure the first task is waiting before allowing it to finish
    final boolean started = firstStarted.await(2, TimeUnit.SECONDS);
    assertThat(started).isTrue();
    firstContinue.countDown();

    // then
    final boolean finished = done.await(2, TimeUnit.SECONDS);
    assertThat(finished).isTrue();
    assertThat(order).containsExactly(1, 2);
  }

  @Test
  void exceptionInTaskIsIsolatedAndNextTaskRuns() throws InterruptedException {
    // given
    final List<String> seen = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch done = new CountDownLatch(1);

    // when
    sut.dispatch(
        "exKey",
        () -> {
          throw new RuntimeException("boom");
        });

    sut.dispatch(
        "exKey",
        () -> {
          seen.add("after");
          done.countDown();
        });

    // then
    final boolean finished = done.await(2, TimeUnit.SECONDS);
    assertThat(finished).isTrue();
    assertThat(seen).containsExactly("after");
  }
}
