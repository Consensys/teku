/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.statetransition.validation.signatures;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.service.serviceutils.ServiceCapacityExceededException;

class AggregatingSignatureVerificationService extends SignatureVerificationService {
  private static final Logger LOG = LogManager.getLogger();

  static final int DEFAULT_QUEUE_CAPACITY = 5_000;
  static final int DEFAULT_MAX_BATCH_SIZE = 250;
  static final int DEFAULT_MIN_BATCH_SIZE_TO_SPLIT = 25;
  static final int DEFAULT_THREAD_COUNT = 2;

  private final int numThreads;
  private final int maxBatchSize;
  private final int minBatchSizeToSplit;

  @VisibleForTesting final BlockingQueue<SignatureTask> batchSignatureTasks;
  private final AsyncRunner asyncRunner;

  @VisibleForTesting
  AggregatingSignatureVerificationService(
      final MetricsSystem metricsSystem,
      final AsyncRunnerFactory asyncRunnerFactory,
      final int numThreads,
      final int queueCapacity,
      final int maxBatchSize,
      final int minBatchSizeToSplit) {
    this.numThreads = Math.min(numThreads, Runtime.getRuntime().availableProcessors());
    this.asyncRunner = asyncRunnerFactory.create(this.getClass().getSimpleName(), this.numThreads);
    this.maxBatchSize = maxBatchSize;

    this.batchSignatureTasks = new ArrayBlockingQueue<>(queueCapacity);
    this.minBatchSizeToSplit = minBatchSizeToSplit;
    metricsSystem.createGauge(
        TekuMetricCategory.EXECUTOR,
        "signature_verifications_queue_size",
        "Tracks number of signatures waiting to be batch verified",
        this::getQueueSize);
  }

  AggregatingSignatureVerificationService(
      final MetricsSystem metricsSystem, final AsyncRunnerFactory asyncRunnerFactory) {
    this(
        metricsSystem,
        asyncRunnerFactory,
        DEFAULT_THREAD_COUNT,
        DEFAULT_QUEUE_CAPACITY,
        DEFAULT_MAX_BATCH_SIZE,
        DEFAULT_MIN_BATCH_SIZE_TO_SPLIT);
  }

  @Override
  protected SafeFuture<?> doStart() {
    for (int i = 0; i < numThreads; i++) {
      asyncRunner
          .runAsync(this::run)
          .finish(
              err ->
                  AggregatingSignatureVerificationService.LOG.error(
                      "Signature Verification Task failed", err));
    }

    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Boolean> verify(
      final List<BLSPublicKey> publicKeys, final Bytes message, final BLSSignature signature) {
    assertIsRunning("verify");
    final SignatureTask task = new SignatureTask(publicKeys, message, signature);
    if (!batchSignatureTasks.offer(task)) {
      // Queue is full
      final Throwable error =
          new ServiceCapacityExceededException("Failed to process signature, queue is full.");
      task.result.completeExceptionally(error);
    }
    return task.result;
  }

  private void run() {
    while (isRunning()) {
      final List<SignatureTask> tasks = waitForBatch();
      if (!tasks.isEmpty()) {
        batchVerifySignatures(tasks);
      }
    }
  }

  private List<SignatureTask> waitForBatch() {
    final List<SignatureTask> tasks = new ArrayList<>();
    try {
      int batchSize = maxBatchSize;
      final SignatureTask firstTask = batchSignatureTasks.poll(30, TimeUnit.SECONDS);
      if (firstTask != null) {
        tasks.add(firstTask);
        batchSize -= 1;
      }
      batchSignatureTasks.drainTo(tasks, batchSize);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return tasks;
  }

  @VisibleForTesting
  void batchVerifySignatures(final List<SignatureTask> tasks) {
    final List<List<BLSPublicKey>> allKeys = new ArrayList<>();
    final List<Bytes> allMessages = new ArrayList<>();
    final List<BLSSignature> allSignatures = new ArrayList<>();

    for (SignatureTask task : tasks) {
      allKeys.add(task.publicKeys);
      allMessages.add(task.message);
      allSignatures.add(task.signature);
    }

    final boolean batchIsValid = BLS.batchVerify(allKeys, allMessages, allSignatures);
    if (batchIsValid) {
      for (SignatureTask task : tasks) {
        task.result.complete(true);
      }
    } else if (tasks.size() == 1) {
      // We only had 1 signature, so it must be invalid
      tasks.get(0).result.complete(false);
    } else if (tasks.size() >= minBatchSizeToSplit) {
      // Split up tasks and try to verify in smaller batches
      final List<List<SignatureTask>> splitTasks = splitTasks(tasks);
      for (List<SignatureTask> splitTask : splitTasks) {
        batchVerifySignatures(splitTask);
      }
    } else {
      // Validate each signature individually
      for (SignatureTask task : tasks) {
        final boolean taskIsValid =
            BLSSignatureVerifier.SIMPLE.verify(task.publicKeys, task.message, task.signature);
        task.result.complete(taskIsValid);
      }
    }
  }

  @VisibleForTesting
  List<List<SignatureTask>> splitTasks(final List<SignatureTask> tasks) {
    final int splitListSize = Math.toIntExact((long) Math.ceil(tasks.size() / 2.0));
    return Lists.partition(tasks, splitListSize);
  }

  private double getQueueSize() {
    return batchSignatureTasks.size();
  }

  @Override
  public SafeFuture<Void> verify(
      final List<List<BLSPublicKey>> publicKeys,
      final List<Bytes> signingRoots,
      final List<BLSSignature> signatures) {
    final boolean verifyResult = BLSSignatureVerifier.verify(publicKeys, signingRoots, signatures);
    if (verifyResult) {
      return SafeFuture.COMPLETE;
    }
    return SafeFuture.failedFuture(
        new IllegalArgumentException("Block signatures are invalid"));
  }

  @VisibleForTesting
  static class SignatureTask {
    final SafeFuture<Boolean> result = new SafeFuture<>();
    final List<BLSPublicKey> publicKeys;
    final Bytes message;
    final BLSSignature signature;

    private SignatureTask(
        final List<BLSPublicKey> publicKeys, final Bytes message, final BLSSignature signature) {
      this.publicKeys = publicKeys;
      this.message = message;
      this.signature = signature;
    }
  }
}
