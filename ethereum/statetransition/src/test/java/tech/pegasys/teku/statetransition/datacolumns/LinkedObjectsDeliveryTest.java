/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class LinkedObjectsDeliveryTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalEip7594());
  private final ExecutorService executorService = Executors.newFixedThreadPool(4);
  private final AsyncRunnerFactory asyncRunnerFactory =
      AsyncRunnerFactory.createDefault(new MetricTrackingExecutorFactory(new StubMetricsSystem()));
  private final AsyncRunner asyncRunner = asyncRunnerFactory.create("test", 2);

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  public void testDeadLock() throws Exception {
    final Retriever retriever = new Retriever();
    final Sync sync = new Sync(retriever);
    final CountDownLatch latch = new CountDownLatch(100);
    for (int i = 0; i < 100; ++i) {
      executorService.submit(
          () -> {
            sync.next(latch);
          });
    }
    assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
  }

  class Retriever {
    public synchronized SafeFuture<DataColumnSidecar> retrieve() {
      final SafeFuture<DataColumnSidecar> result = new SafeFuture<>();
      asyncRunner.runAsync(() -> reqRespCompleted(result)).ifExceptionGetsHereRaiseABug();
      return result;
    }

    private synchronized void reqRespCompleted(final SafeFuture<DataColumnSidecar> result) {
      asyncRunner
          .runAsync(() -> result.complete(dataStructureUtil.randomDataColumnSidecar()))
          .ifExceptionGetsHereRaiseABug();
      // Same without asyncrunner will cause deadlock
      // result.complete(dataStructureUtil.randomDataColumnSidecar());
    }
  }

  static class Sync {
    final Retriever retriever;

    public Sync(Retriever retriever) {
      this.retriever = retriever;
    }

    public synchronized void next(final CountDownLatch latch) {
      final SafeFuture<DataColumnSidecar> promise = retriever.retrieve();
      promise.finish(response -> onRequestComplete(latch), __ -> {});
    }

    private synchronized void onRequestComplete(final CountDownLatch latch) {
      try {
        Thread.sleep(1);
        latch.countDown();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
