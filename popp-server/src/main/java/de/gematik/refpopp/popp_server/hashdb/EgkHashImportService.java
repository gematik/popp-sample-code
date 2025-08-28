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

import de.gematik.poppcommons.api.exceptions.ImportDataException;
import de.gematik.refpopp.popp_server.model.EgkEntry;
import de.gematik.refpopp.popp_server.model.EgkEntryState;
import de.gematik.refpopp.popp_server.model.ImportReportEntry;
import de.gematik.refpopp.popp_server.repository.CertHashRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EgkHashImportService {

  private static final int NUM_CONSUMER_THREADS = Runtime.getRuntime().availableProcessors();
  private static final int BATCH_SIZE = 500;

  private final CmsSignatureVerifier cmsSignatureVerifier;
  private final CertHashRepository certHashRepository;
  private final EgkTransferEntryParser egkTransferEntryParser;
  private final EgkEntryProcessor egkEntryProcessor;
  private final BatchFlusherFactory batchFlusherFactory;
  private final ImportReportProcessor importReportProcessor;

  public EgkHashImportService(
      final CmsSignatureVerifier cmsSignatureVerifier,
      final CertHashRepository certHashRepository,
      final EgkTransferEntryParser egkTransferEntryParser,
      final EgkEntryProcessor egkEntryProcessor,
      final BatchFlusherFactory batchFlusherFactory,
      final ImportReportProcessor importReportProcessor) {
    this.cmsSignatureVerifier = cmsSignatureVerifier;
    this.certHashRepository = certHashRepository;
    this.egkTransferEntryParser = egkTransferEntryParser;
    this.egkEntryProcessor = egkEntryProcessor;
    this.batchFlusherFactory = batchFlusherFactory;
    this.importReportProcessor = importReportProcessor;
    log.info(
        "| EgkHashImportService initialized with {} consumer threads and {} batch size",
        NUM_CONSUMER_THREADS,
        BATCH_SIZE);
    log.info("| ImportReportProcessor initialized");
    log.info("| CmsSignatureVerifier initialized");
    log.info("| CertHashRepository initialized");
    log.info("| EgkTransferEntryParser initialized");
    log.info("| EgkEntryProcessor initialized");
  }

  /**
   * Imports data from the specified path into the EGK hash database.
   *
   * @param path The path to the data file.
   *     <p>The signed data has the following ASN.1 structure:
   *     <pre>
   *                  eContent ::= SEQUENCE {
   *                      version    INTEGER,
   *                      egkInfos   SEQUENCE OF egkInfo (SIZE(1..100000000))
   *                  }
   *
   *                  egkInfo ::= SET {
   *                      notAfter  UTCTime,     -- attribute "notAfter" from X.509
   *                      hashCvc   OCTET STRING,
   *                      hashAut   BIT STRING
   *                  }
   *                  </pre>
   *
   * @param sessionId The session ID for logging purposes.
   */
  public void importData(final Path path, final String sessionId) {
    ImportReportEntry report = importReportProcessor.createReport(sessionId);
    AtomicLong importedCount = new AtomicLong(0);
    AtomicLong blockedCount = new AtomicLong(0);
    AtomicLong skippedCount = new AtomicLong(0);
    AtomicLong totalProcessedCount = new AtomicLong(0);

    if (verifySignature(path, sessionId)) {
      final BlockingQueue<Optional<EgkTransferEntry>> queue =
          new ArrayBlockingQueue<>(NUM_CONSUMER_THREADS * 2);
      final ExecutorService exec = Executors.newFixedThreadPool(NUM_CONSUMER_THREADS);
      try {
        startConsumers(
            sessionId, exec, queue, importedCount, blockedCount, skippedCount, totalProcessedCount);
        enqueueParsedEntries(path, sessionId, queue);
      } catch (final InterruptedException e) {
        log.error("| sessionId {}: Interrupted while processing queue", sessionId, e);
        Thread.currentThread().interrupt();
      } finally {
        shutdownAndAwaitTermination(sessionId, queue, exec);
      }
    } else {
      log.warn("| sessionId {}: Signature invalid", sessionId);
    }

    importReportProcessor.finalizeReport(
        report,
        importedCount.get(),
        blockedCount.get(),
        skippedCount.get(),
        totalProcessedCount.get());
  }

  private boolean verifySignature(final Path path, final String sessionId) {
    try (final InputStream in = Files.newInputStream(path)) {
      log.info("Verifying signature for sessionId {}", sessionId);
      return cmsSignatureVerifier.isSignatureValid(in, sessionId);
    } catch (final IOException e) {
      throw new ImportDataException(
          sessionId, "Error reading file: " + e.getMessage(), "errorCode");
    }
  }

  private void shutdownAndAwaitTermination(
      final String sessionId,
      final BlockingQueue<Optional<EgkTransferEntry>> queue,
      final ExecutorService exec) {
    for (int i = 0; i < NUM_CONSUMER_THREADS; i++) {
      try {
        queue.put(Optional.empty());
      } catch (final InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }

    exec.shutdown();
    try {
      if (!exec.awaitTermination(1, TimeUnit.HOURS)) {
        exec.shutdownNow();
        throw new ImportDataException(sessionId, "Timeout waiting for import threads", "errorCode");
      }
    } catch (final InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new ImportDataException(
          sessionId, "Interrupted while waiting for threads", "errorCode");
    }
  }

  private void enqueueParsedEntries(
      final Path path,
      final String sessionId,
      final BlockingQueue<Optional<EgkTransferEntry>> queue)
      throws InterruptedException {
    try (final InputStream in = Files.newInputStream(path)) {
      final var parsedEntries = egkTransferEntryParser.parseAll(in, sessionId);
      for (final var entry : parsedEntries) {
        queue.put(Optional.of(entry));
      }
    } catch (final IOException e) {
      throw new ImportDataException(
          sessionId, "Error streaming CMS eContent parse: " + e.getMessage(), "errorCode");
    }
  }

  private void startConsumers(
      final String sessionId,
      final ExecutorService exec,
      final BlockingQueue<Optional<EgkTransferEntry>> queue,
      final AtomicLong importedCount,
      final AtomicLong blockedCount,
      final AtomicLong skippedCount,
      final AtomicLong totalProcessedCount) {
    for (int i = 0; i < NUM_CONSUMER_THREADS; i++) {
      exec.submit(
          () ->
              buildConsumerTask(
                  sessionId,
                  queue,
                  importedCount,
                  blockedCount,
                  skippedCount,
                  totalProcessedCount));
    }
  }

  private void buildConsumerTask(
      final String sessionId,
      final BlockingQueue<Optional<EgkTransferEntry>> queue,
      final AtomicLong importedCount,
      final AtomicLong blockedCount,
      final AtomicLong skippedCount,
      final AtomicLong totalProcessedCount) {
    log.info("| consumer thread started: {}", Thread.currentThread().getName());
    final var flusher =
        batchFlusherFactory.<EgkEntry>create(BATCH_SIZE, certHashRepository::saveAll);
    try {
      while (true) {
        final Optional<EgkTransferEntry> optionalEntry = queue.take();
        if (optionalEntry.isEmpty()) {
          break;
        }

        final EgkTransferEntry entry = optionalEntry.get();

        try {
          var processedEntries = egkEntryProcessor.process(entry, sessionId);
          totalProcessedCount.addAndGet(processedEntries.size());

          for (EgkEntry processedEntry : processedEntries) {
            if (processedEntry.getState() == EgkEntryState.IMPORTED) {
              importedCount.incrementAndGet();
            } else if (processedEntry.getState() == EgkEntryState.BLOCKED) {
              blockedCount.incrementAndGet();
            } else {
              skippedCount.incrementAndGet();
            }
          }

          flusher.addAll(processedEntries);
        } catch (Exception e) {
          blockedCount.incrementAndGet();
          log.debug("| sessionId {}: Error processing entry: {}", sessionId, e.getMessage());
        }
      }
      flusher.flushRemaining();
    } catch (final InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }
}
