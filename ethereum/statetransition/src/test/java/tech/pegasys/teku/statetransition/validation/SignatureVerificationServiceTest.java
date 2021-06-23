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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.MockScheduledExecutor;
import tech.pegasys.teku.service.serviceutils.ServiceCapacityExceededException;

public class SignatureVerificationServiceTest {
  private static List<BLSKeyPair> KEYS = BLSKeyGenerator.generateKeyPairs(10);

  private final int queueCapacity = 10;
  private final int batchSize = 5;
  private final int numThreads = 2;
  private final SignatureVerificationService service =
      new SignatureVerificationService(
          this::mockExecutorFactory, numThreads, queueCapacity, batchSize);
  private MockScheduledExecutor executor;

  @Test
  public void start_shouldSetupExecutor() {
    startService();
    assertThat(executor).isNotNull();
    assertThat(executor.isShutdown()).isFalse();
    assertThat(executor.getPendingFuturesCount()).isEqualTo(numThreads);
  }

  @Test
  public void stop_shouldShutdownExecutor() {
    startService();
    stopService();
    assertThat(executor.isShutdown()).isTrue();
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
  public void verify_singleValidSignature() {
    startService();
    final SafeFuture<Boolean> future = executeValidVerify(0, 0);

    assertThat(future).isNotDone();
    executor.runPendingFutures();

    assertThat(future).isCompletedWithValue(true);
  }

  @Test
  public void verify_singleInvalidSignature() {
    startService();
    final SafeFuture<Boolean> future = executeInvalidVerify(0, 0);

    assertThat(future).isNotDone();
    executor.runPendingFutures();

    assertThat(future).isCompletedWithValue(false);
  }

  @Test
  public void verify_multipleRoundsOfValidSignatures() {
    startService();

    for (int i = 0; i < 3; i++) {
      final List<SafeFuture<Boolean>> futures = new ArrayList<>();
      for (int j = 0; j < queueCapacity; j++) {
        futures.add(executeValidVerify(j, j));
      }

      for (SafeFuture<Boolean> future : futures) {
        assertThat(future).isNotDone();
      }
      executor.runPendingFutures();

      for (SafeFuture<Boolean> future : futures) {
        assertThat(future).isCompletedWithValue(true);
      }
    }
  }

  @Test
  public void verify_multipleRoundsOfMixedSignatures() {
    startService();

    for (int i = 0; i < 3; i++) {
      final List<SafeFuture<Boolean>> futures = new ArrayList<>();
      for (int j = 0; j < queueCapacity; j++) {
        if (j % 3 == 0) {
          futures.add(executeInvalidVerify(j, j));
        } else {
          futures.add(executeValidVerify(j, j));
        }
      }

      for (SafeFuture<Boolean> future : futures) {
        assertThat(future).isNotDone();
      }
      executor.runPendingFutures();

      for (int j = 0; j < queueCapacity; j++) {
        final SafeFuture<Boolean> future = futures.get(j);
        if (j % 3 == 0) {
          assertThat(future).isCompletedWithValue(false);
        } else {
          assertThat(future).isCompletedWithValue(true);
        }
      }
    }
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

  private final MockScheduledExecutor mockExecutorFactory(final int numThreads) {
    executor = new MockScheduledExecutor();
    return executor;
  }
}
