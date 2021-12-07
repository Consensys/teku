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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.service.serviceutils.ServiceCapacityExceededException;
import tech.pegasys.teku.statetransition.validation.signatures.AggregatingSignatureVerificationService.SignatureTask;

public class AggregatingSignatureVerificationServiceTest {
  private static List<BLSKeyPair> KEYS = BLSKeyGenerator.generateKeyPairs(50);

  private final int queueCapacity = 50;
  private final int batchSize = 25;
  private final int minBatchSizeToSplit = 5;
  private final int numThreads = 2;
  private final boolean strictThreadLimitEnabled = true;
  private final StubAsyncRunner completionRunner = new StubAsyncRunner();

  private final StubAsyncRunnerFactory asyncRunnerFactory = new StubAsyncRunnerFactory();
  private AggregatingSignatureVerificationService service =
      new AggregatingSignatureVerificationService(
          new StubMetricsSystem(),
          asyncRunnerFactory,
          completionRunner,
          numThreads,
          queueCapacity,
          batchSize,
          minBatchSizeToSplit,
          strictThreadLimitEnabled);

  @Test
  public void start_shouldQueueTasks() {
    startService();
    assertThat(getRunner().countDelayedActions()).isEqualTo(numThreads);
  }

  @Test
  public void verify_beforeStarted() {
    assertThatThrownBy(() -> executeValidVerify(0, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Service must be running to execute action 'verify'");
  }

  @Test
  public void verify_afterStopping() {
    startService();
    stopService();
    assertThatThrownBy(() -> executeValidVerify(0, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Service must be running to execute action 'verify'");
  }

  @Test
  public void verify_withFullQueue() {
    startService();

    fillQueue();
    final SafeFuture<Boolean> future = executeInvalidVerify(0, 0);

    assertThat(future).isDone();
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasCauseInstanceOf(ServiceCapacityExceededException.class);
  }

  @Test
  public void verify_singleValidSignature() {
    startService();
    final SafeFuture<Boolean> future = executeValidVerify(0, 0);

    assertThat(future).isNotDone();
    runPendingTasks();

    assertThat(future).isCompletedWithValue(true);
  }

  @Test
  public void verify_singleInvalidSignature() {
    startService();
    final SafeFuture<Boolean> future = executeInvalidVerify(0, 0);

    assertThat(future).isNotDone();
    runPendingTasks();

    assertThat(future).isCompletedWithValue(false);
  }

  @Test
  public void verify_validSignatures_fullBatch() {
    verifyValidSignatures(queueCapacity);
  }

  @Test
  public void verify_validSignatures_smallBatch() {
    verifyValidSignatures(minBatchSizeToSplit - 1);
  }

  @Test
  public void verify_validSignatures_listVerify() {
    startService();

    final List<SafeFuture<Boolean>> futures = new ArrayList<>();
    for (int i = 0; i < batchSize; i += 5) {
      final List<Integer> indices = new ArrayList<>();
      final List<Boolean> useValidSignatures = new ArrayList<>();
      for (int j = 0; j < 5; j++) {
        useValidSignatures.add(true);
        indices.add(i);
      }
      futures.add(executeListVerify(indices, indices, useValidSignatures));
    }
    for (SafeFuture<Boolean> future : futures) {
      assertThat(future).isNotDone();
    }
    runPendingTasks();

    for (SafeFuture<Boolean> future : futures) {
      assertThat(future).isCompletedWithValue(true);
    }
  }

  @Test
  public void verify_InvalidSignature_listVerify() {
    startService();

    final List<SafeFuture<Boolean>> futures = new ArrayList<>();
    for (int i = 0; i < batchSize; i += 5) {
      final List<Integer> indices = new ArrayList<>();
      final List<Boolean> useValidSignatures = new ArrayList<>();
      // batch 1 contains i: 5-9, and the second signature will be invalid
      for (int j = 0; j < 5; j++) {
        useValidSignatures.add(i != 5 || j != 1);
        indices.add(i);
      }
      futures.add(executeListVerify(indices, indices, useValidSignatures));
    }
    for (SafeFuture<Boolean> future : futures) {
      assertThat(future).isNotDone();
    }
    runPendingTasks();

    // the second future will fail due to an invalid signature in the group, but all other futures
    // pass
    for (int i = 0; i < 5; i++) {
      final SafeFuture<Boolean> future = futures.get(i);
      assertThat(future).isCompletedWithValue(i != 1);
    }
  }

  public void verifyValidSignatures(final int batchSize) {
    startService();

    final List<SafeFuture<Boolean>> futures = new ArrayList<>();
    for (int j = 0; j < batchSize; j++) {
      futures.add(executeValidVerify(j, j));
    }

    for (SafeFuture<Boolean> future : futures) {
      assertThat(future).isNotDone();
    }
    runPendingTasks();

    for (SafeFuture<Boolean> future : futures) {
      assertThat(future).isCompletedWithValue(true);
    }
  }

  @Test
  public void verify_mixedSignatures_fullBatch() {
    verifyMixedSignatures(queueCapacity);
  }

  @Test
  public void verify_mixedSignatures_smallBatch() {
    verifyMixedSignatures(minBatchSizeToSplit - 1);
  }

  private void verifyMixedSignatures(final int batchSize) {
    startService();

    final List<SafeFuture<Boolean>> futures = new ArrayList<>();
    for (int j = 0; j < batchSize; j++) {
      if (j % 3 == 0) {
        futures.add(executeInvalidVerify(j, j));
      } else {
        futures.add(executeValidVerify(j, j));
      }
    }

    for (SafeFuture<Boolean> future : futures) {
      assertThat(future).isNotDone();
    }
    runPendingTasks();

    for (int j = 0; j < batchSize; j++) {
      final SafeFuture<Boolean> future = futures.get(j);
      if (j % 3 == 0) {
        assertThat(future).isCompletedWithValue(false);
      } else {
        assertThat(future).isCompletedWithValue(true);
      }
    }
  }

  @Test
  public void testRealServiceWithThreads() throws Exception {
    final MetricsSystem metrics = new StubMetricsSystem();
    final AsyncRunnerFactory realRunnerFactory =
        AsyncRunnerFactory.createDefault(new MetricTrackingExecutorFactory(metrics));
    service =
        new AggregatingSignatureVerificationService(
            metrics,
            realRunnerFactory,
            realRunnerFactory.create("completion", 1),
            1,
            queueCapacity,
            batchSize,
            minBatchSizeToSplit,
            strictThreadLimitEnabled);
    startService();

    final Random random = new Random(1);
    for (int i = 0; i < 3; i++) {
      final List<SafeFuture<Boolean>> validFutures = new ArrayList<>();
      final List<SafeFuture<Boolean>> invalidFutures = new ArrayList<>();
      for (int j = 0; j < queueCapacity - i; j++) {
        if (random.nextFloat() < .5) {
          validFutures.add(executeValidVerify(j, j));
        } else {
          invalidFutures.add(executeInvalidVerify(j, j));
        }
      }

      final List<SafeFuture<Boolean>> allFutures = new ArrayList<>();
      allFutures.addAll(validFutures);
      allFutures.addAll(invalidFutures);
      Waiter.waitFor(
          SafeFuture.allOf(allFutures.toArray(SafeFuture<?>[]::new)), Duration.ofSeconds(5));

      validFutures.forEach(f -> assertThat(f).isCompletedWithValue(true));
      invalidFutures.forEach(f -> assertThat(f).isCompletedWithValue(false));
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  public void splitTasks_evenNumber() {
    startService();

    final int taskCount = 8;
    for (int i = 0; i < taskCount; i++) {
      executeValidVerify(0, i);
    }

    final List<SignatureTask> tasks = getPendingTasks();
    assertThat(tasks.size()).isEqualTo(taskCount);
    final List<List<SignatureTask>> split = service.splitTasks(tasks);

    assertThat(split.size()).isEqualTo(2);
    assertThat(split.get(0).size()).isEqualTo(4);
    assertThat(split.get(1).size()).isEqualTo(4);
    assertThat(split.get(0)).doesNotContainAnyElementsOf(split.get(1));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  public void splitTasks_oddNumber() {
    startService();

    final int taskCount = 7;
    for (int i = 0; i < taskCount; i++) {
      executeValidVerify(0, i);
    }

    final List<SignatureTask> tasks = getPendingTasks();
    assertThat(tasks.size()).isEqualTo(taskCount);
    final List<List<SignatureTask>> split = service.splitTasks(tasks);

    assertThat(split.size()).isEqualTo(2);
    assertThat(split.get(0).size()).isEqualTo(4);
    assertThat(split.get(1).size()).isEqualTo(3);
    assertThat(split.get(0)).doesNotContainAnyElementsOf(split.get(1));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  public void splitTasks_singleTask() {
    startService();

    executeValidVerify(0, 0);

    final List<SignatureTask> tasks = getPendingTasks();
    assertThat(tasks.size()).isEqualTo(1);
    final List<List<SignatureTask>> split = service.splitTasks(tasks);

    assertThat(split.size()).isEqualTo(1);
    assertThat(split.get(0).size()).isEqualTo(1);
  }

  private void startService() {
    try {
      service.start().get(500, TimeUnit.MILLISECONDS);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  private void stopService() {
    try {
      service.stop().get(500, TimeUnit.MILLISECONDS);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void fillQueue() {
    for (int i = 0; i < queueCapacity; i++) {
      executeValidVerify(0, i);
    }
  }

  private SafeFuture<Boolean> executeValidVerify(final int keypairIndex, final int data) {
    return executeVerify(keypairIndex, data, true);
  }

  private SafeFuture<Boolean> executeInvalidVerify(final int keypairIndex, final int data) {
    return executeVerify(keypairIndex, data, false);
  }

  private SafeFuture<Boolean> executeVerify(
      final int keypairIndex, final int data, final boolean useValidSignature) {
    final BLSKeyPair keypair = KEYS.get(keypairIndex);
    final Bytes message = Bytes.of(data);
    final BLSSignature signature =
        useValidSignature ? BLS.sign(keypair.getSecretKey(), message) : BLSSignature.empty();
    return service.verify(keypair.getPublicKey(), message, signature);
  }

  private SafeFuture<Boolean> executeListVerify(
      final List<Integer> keyIndices,
      final List<Integer> data,
      final List<Boolean> useValidSignatures) {
    final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
    final List<Bytes> messages = new ArrayList<>();
    final List<BLSSignature> signatures = new ArrayList<>();
    for (int i = 0; i < keyIndices.size(); i++) {
      publicKeys.add(List.of(KEYS.get(i).getPublicKey()));
      messages.add(Bytes.of(data.get(i)));
      signatures.add(
          useValidSignatures.get(i)
              ? BLS.sign(KEYS.get(i).getSecretKey(), messages.get(i))
              : BLSSignature.empty());
    }
    return service.verify(publicKeys, messages, signatures);
  }

  private void runPendingTasks() {
    // Get pending tasks
    final List<SignatureTask> tasks = getPendingTasks();
    service.batchVerifySignatures(tasks);
    completionRunner.executeQueuedActions();
  }

  private List<SignatureTask> getPendingTasks() {
    final List<SignatureTask> pendingTasks = new ArrayList<>();
    service.batchSignatureTasks.drainTo(pendingTasks);
    return pendingTasks;
  }

  private StubAsyncRunner getRunner() {
    final List<StubAsyncRunner> runners = asyncRunnerFactory.getStubAsyncRunners();
    assertThat(runners.size()).isEqualTo(1);
    return runners.get(0);
  }
}
