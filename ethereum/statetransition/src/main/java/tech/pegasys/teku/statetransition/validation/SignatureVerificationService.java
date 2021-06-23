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

package tech.pegasys.teku.statetransition.validation;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceCapacityExceededException;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;

public class SignatureVerificationService extends Service implements AsyncBLSSignatureVerifier {
  private static final int QUEUE_CAPACITY = 1000;
  private static final int MAX_BATCH_SIZE = 50;
  private static final Duration INACTIVITY_PAUSE = Duration.ofMillis(200);

  private final ExecutorFactory executorFactory;
  private final int numThreads;
  private final int maxBatchSize;

  private final BlockingQueue<SignatureTask> batchSignatureTasks;
  private ScheduledExecutorService executor;

  @VisibleForTesting
  SignatureVerificationService(
      final ExecutorFactory executorFactory,
      final int numThreads,
      final int queueCapacity,
      final int maxBatchSize) {
    this.executorFactory = executorFactory;
    this.numThreads = Math.min(numThreads, Runtime.getRuntime().availableProcessors());
    this.maxBatchSize = maxBatchSize;

    this.batchSignatureTasks = new ArrayBlockingQueue<SignatureTask>(queueCapacity);
  }

  public static SignatureVerificationService create() {
    return new SignatureVerificationService(
        SignatureVerificationService::defaultExecutorFactory, 2, QUEUE_CAPACITY, MAX_BATCH_SIZE);
  }

  private static ScheduledExecutorService defaultExecutorFactory(final int numThreads) {
    return new ScheduledThreadPoolExecutor(
        numThreads,
        new ThreadFactoryBuilder()
            .setDaemon(false)
            .setNameFormat(SignatureVerificationService.class.getSimpleName() + "-%d")
            .build());
  }

  @Override
  protected synchronized SafeFuture<?> doStart() {
    executor = executorFactory.createExecutor(numThreads);
    for (int i = 0; i < numThreads; i++) {
      executor.submit(this::run);
    }

    return SafeFuture.COMPLETE;
  }

  @Override
  protected synchronized SafeFuture<?> doStop() {
    executor.shutdownNow();
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
      final List<SignatureTask> tasks = new ArrayList<>();
      batchSignatureTasks.drainTo(tasks, maxBatchSize);
      if (tasks.isEmpty()) {
        // Exit loop and resume after a short pause
        executor.schedule(this::run, INACTIVITY_PAUSE.toMillis(), TimeUnit.MILLISECONDS);
        return;
      } else {
        batchVerifySignatures(tasks);
      }
    }
  }

  private void batchVerifySignatures(final List<SignatureTask> tasks) {
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
    } else {
      // Validate each signature individually
      for (SignatureTask task : tasks) {
        final boolean taskIsValid =
            BLSSignatureVerifier.SIMPLE.verify(task.publicKeys, task.message, task.signature);
        task.result.complete(taskIsValid);
      }
    }
  }

  private static class SignatureTask {
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

  interface ExecutorFactory {
    ScheduledExecutorService createExecutor(final int numThreads);
  }
}
