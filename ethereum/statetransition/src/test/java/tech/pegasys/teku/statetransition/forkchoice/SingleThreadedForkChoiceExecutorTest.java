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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceExecutor.ForkChoiceTask;

class SingleThreadedForkChoiceExecutorTest {

  private ForkChoiceExecutor executor;

  @BeforeEach
  void setUp() {
    executor = SingleThreadedForkChoiceExecutor.create();
  }

  @AfterEach
  void tearDown() {
    executor.stop();
  }

  @Test
  void shouldPerformTasks() throws Exception {
    final StubTask task = new StubTask();
    final SafeFuture<String> result = executor.performTask(task);
    task.assertStarted();
    assertThat(result).isNotDone();

    task.result.complete("Done");
    Waiter.waitFor(result);
    assertThat(result).isCompletedWithValue("Done");
  }

  @Test
  void shouldNotExecuteNextTaskUntilFirstIsComplete() throws Exception {
    final StubTask task1 = new StubTask();
    final StubTask task2 = new StubTask();
    final SafeFuture<String> result1 = executor.performTask(task1);
    final SafeFuture<String> result2 = executor.performTask(task2);

    task1.assertStarted();
    assertThat(task2.started).isFalse();

    task1.result.complete("Foo");
    Waiter.waitFor(result1);
    task2.assertStarted();
    task2.result.complete("Bar");
    Waiter.waitFor(result2);
    assertThat(result1).isCompletedWithValue("Foo");
    assertThat(result2).isCompletedWithValue("Bar");
  }

  @Test
  void shouldReportExceptionWhenTaskFails() {
    final StubTask task = new StubTask();
    final SafeFuture<String> result = executor.performTask(task);
    final RuntimeException error = new RuntimeException("Failed");
    task.result.completeExceptionally(error);
    Waiter.waitFor(result::isCompletedExceptionally);
    assertThatSafeFuture(result).isCompletedExceptionallyWith(error);
  }

  private static class StubTask implements ForkChoiceTask<String> {
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final SafeFuture<String> result = new SafeFuture<>();

    @Override
    public SafeFuture<String> performTask() {
      started.set(true);
      return result;
    }

    void assertStarted() {
      Waiter.waitFor(started::get);
    }
  }
}
