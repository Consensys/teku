/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.async.eventthread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;

class AsyncRunnerEventThreadTest {
  private final AsyncRunnerFactory asyncRunnerFactory =
      new AsyncRunnerFactory(new MetricTrackingExecutorFactory(new StubMetricsSystem()));

  private final EventThread eventThread =
      new AsyncRunnerEventThread(AsyncRunnerEventThreadTest.class.getName(), asyncRunnerFactory);

  @AfterEach
  void tearDown() {
    eventThread.stop();
    asyncRunnerFactory.getAsyncRunners().forEach(AsyncRunner::shutdown);
  }

  @Test
  void shouldNotBeOnEventThreadWhenNotStarted() {
    assertThatThrownBy(eventThread::checkOnEventThread).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldNotBeOnEventThreadWhenStartedButNotUsingExecute() {
    eventThread.start();
    assertThatThrownBy(eventThread::checkOnEventThread).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldBeOnEventThreadWhenUsingExecute() throws Exception {
    eventThread.start();
    waitForOnEventThread(eventThread::checkOnEventThread);
  }

  @Test
  void shouldExecuteImmediatelyIfAlreadyOnEventThread() throws Exception {
    eventThread.start();
    waitForOnEventThread(
        () -> {
          final AtomicBoolean actionRun = new AtomicBoolean(false);
          eventThread.execute(() -> actionRun.set(true));
          // Should have happened synchronously because we're already on the event thread
          //noinspection ConstantConditions
          assertThat(actionRun).isTrue();
        });
  }

  private void waitForOnEventThread(final Runnable action) throws Exception {
    final SafeFuture<Void> complete = new SafeFuture<>();
    eventThread.execute(
        () -> {
          try {
            action.run();
            complete.complete(null);
          } catch (final Throwable t) {
            complete.completeExceptionally(t);
          }
        });
    Waiter.waitFor(complete);
  }
}
