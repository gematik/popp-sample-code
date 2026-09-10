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

import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dispatches incoming server messages onto a worker pool so that the single WebSocket receive
 * thread is not blocked by (potentially slow) card I/O. This frees the receive loop to read the
 * next frame immediately, enabling parallel processing of independent token requests over one
 * connection.
 *
 * <p>Messages are processed <b>in parallel across</b> different {@code clientSessionId}s, but <b>in
 * order within</b> a single {@code clientSessionId}. Per-key ordering is achieved by chaining the
 * tasks of a key on a {@link CompletableFuture} so that the next task only starts once the previous
 * one for the same key has finished. Different keys use independent chains and therefore run
 * concurrently on the shared pool.
 */
@Component
@Slf4j
public class ClientMessageDispatcher {

  private static final String DEFAULT_KEY = "__default__";

  private final ExecutorService executor;
  private final ConcurrentMap<String, CompletableFuture<Void>> serialChains =
      new ConcurrentHashMap<>();

  public ClientMessageDispatcher(
      @Value("${popp-client.message-processing-pool-size:8}") final int poolSize) {
    this.executor = Executors.newFixedThreadPool(Math.max(1, poolSize), namedThreadFactory());
  }

  /**
   * Schedules {@code task} for asynchronous execution. Tasks sharing the same {@code orderingKey}
   * are executed strictly one after another (FIFO); tasks with different keys may run concurrently.
   *
   * @param orderingKey the correlation key (typically the {@code clientSessionId}); {@code null} or
   *     blank falls back to a shared default chain
   * @param task the work to run
   */
  public void dispatch(final String orderingKey, final Runnable task) {
    final String key = (orderingKey == null || orderingKey.isBlank()) ? DEFAULT_KEY : orderingKey;
    serialChains.compute(
        key,
        (chainKey, previous) -> {
          final CompletableFuture<Void> base =
              previous == null ? CompletableFuture.completedFuture(null) : previous;
          final CompletableFuture<Void> next =
              base.handleAsync((ignored, throwable) -> runSafely(chainKey, task), executor);
          // Remove the entry once this task finishes, but only if it is still the tail of the chain
          // (a newer task may already have been appended, replacing the mapped value).
          next.whenComplete((result, throwable) -> serialChains.remove(chainKey, next));
          return next;
        });
  }

  private Void runSafely(final String key, final Runnable task) {
    try {
      task.run();
    } catch (final RuntimeException e) {
      log.error("| Error while processing message for key {}: {}", key, e.getMessage(), e);
    }
    return null;
  }

  @PreDestroy
  public void shutdown() {
    log.info("| Shutting down ClientMessageDispatcher");
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      executor.shutdownNow();
    }
  }

  private static ThreadFactory namedThreadFactory() {
    final AtomicInteger counter = new AtomicInteger();
    return runnable -> {
      final Thread thread = new Thread(runnable);
      thread.setName("client-msg-dispatcher-" + counter.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
  }
}
