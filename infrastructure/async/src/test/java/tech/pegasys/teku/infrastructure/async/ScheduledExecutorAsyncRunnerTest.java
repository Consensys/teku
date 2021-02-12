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

package tech.pegasys.teku.infrastructure.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScheduledExecutorAsyncRunnerTest {

  private final AtomicReference<Throwable> executorException = new AtomicReference<>();
  private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
  private final ExecutorService workerPool = mock(ExecutorService.class);
  private AsyncRunner asyncRunner;

  @BeforeEach
  void setUp() {
    // Real executors use a different thread so exceptions don't propagate back out of execute
    doAnswer(
            invocation -> {
              try {
                ((Runnable) invocation.getArgument(0)).run();
              } catch (final Throwable t) {
                executorException.set(t);
              }
              return null;
            })
        .when(workerPool)
        .execute(any());
    asyncRunner = new ScheduledExecutorAsyncRunner(scheduler, workerPool);
  }

  @AfterEach
  void tearDown() {
    // Exceptions should always be passed back through the returned future not left unhandled
    assertThat(executorException).hasValue(null);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void runAsync_shouldExecuteActionWithExecutorAndReturnResult() throws Throwable {
    final SafeFuture<String> actionResult = new SafeFuture<>();
    final ExceptionThrowingFutureSupplier<String> action =
        mock(ExceptionThrowingFutureSupplier.class);
    when(action.get()).thenReturn(actionResult);

    final SafeFuture<String> result = asyncRunner.runAsync(action);

    verify(action).get();
    assertThat(result).isNotDone();

    actionResult.complete("Yay");
    assertThat(result).isCompletedWithValue("Yay");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void runAsync_shouldExecuteActionWithExecutorAndReturnExceptionalResult()
      throws Throwable {
    final SafeFuture<String> actionResult = new SafeFuture<>();
    final ExceptionThrowingFutureSupplier<String> action =
        mock(ExceptionThrowingFutureSupplier.class);
    when(action.get()).thenReturn(actionResult);

    final SafeFuture<String> result = asyncRunner.runAsync(action);

    verify(action).get();
    assertThat(result).isNotDone();

    final RuntimeException exception = new RuntimeException("Nope");
    actionResult.completeExceptionally(exception);
    assertThatSafeFuture(result).isCompletedExceptionallyWith(exception);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void runAsync_shouldCompleteExceptionallyWhenExecutorFails() throws Throwable {
    final ExceptionThrowingFutureSupplier<String> action =
        mock(ExceptionThrowingFutureSupplier.class);
    final RuntimeException exception = new RuntimeException("Nope");
    doThrow(exception).when(workerPool).execute(any());

    final SafeFuture<String> result = asyncRunner.runAsync(action);

    verify(action, never()).get();
    assertThatSafeFuture(result).isCompletedExceptionallyWith(exception);
  }

  @Test
  void runAsyc_shouldCompleteExceptionallyWhenSupplierThrowsException() {
    final RuntimeException exception = new RuntimeException("My bad...");
    final ExceptionThrowingFutureSupplier<String> action =
        () -> {
          throw exception;
        };

    final SafeFuture<String> result = asyncRunner.runAsync(action);

    assertThatSafeFuture(result).isCompletedExceptionallyWith(exception);
  }

  @Test
  public void testRecurrentTaskCancel() {
    final List<Runnable> scheduledActions = new ArrayList<>();
    when(scheduler.schedule(any(Runnable.class), eq(100L), eq(TimeUnit.MILLISECONDS)))
        .thenAnswer(
            invocation -> {
              scheduledActions.add(invocation.getArgument(0));
              return mock(ScheduledFuture.class);
            });
    AtomicInteger counter = new AtomicInteger();
    Cancellable task =
        asyncRunner.runWithFixedDelay(counter::incrementAndGet, Duration.ofMillis(100), t -> {});
    assertThat(scheduledActions).hasSize(1);
    scheduledActions.get(0).run();
    assertThat(counter).hasValue(1);

    // After it executes, it should schedule the next invocation.
    assertThat(scheduledActions).hasSize(2);
    task.cancel();

    // Should not execute after the task is cancelled
    scheduledActions.get(1).run();
    assertThat(counter).hasValue(1);
  }

  @Test
  public void testRecurrentTaskExceptionHandler() {
    final List<Runnable> scheduledActions = new ArrayList<>();
    when(scheduler.schedule(any(Runnable.class), eq(100L), eq(TimeUnit.MILLISECONDS)))
        .thenAnswer(
            invocation -> {
              scheduledActions.add(invocation.getArgument(0));
              return mock(ScheduledFuture.class);
            });
    AtomicInteger counter = new AtomicInteger();
    AtomicReference<Throwable> exception = new AtomicReference<>();

    asyncRunner.runWithFixedDelay(
        () -> {
          if (counter.incrementAndGet() == 2) {
            throw new RuntimeException("Ups");
          }
        },
        Duration.ofMillis(100),
        exception::set);
    assertThat(scheduledActions).hasSize(1);
    scheduledActions.get(0).run();
    assertThat(scheduledActions).hasSize(2);
    scheduledActions.get(1).run();
    assertThat(scheduledActions).hasSize(3);
    scheduledActions.get(2).run();
    assertThat(exception.get()).hasMessageContaining("Ups");
  }

  @Test
  void shouldReportRejectionFromWorkerPoolForDelayedActions() {
    final RejectedExecutionException exception = new RejectedExecutionException("Too lazy");
    doThrow(exception).when(workerPool).execute(any());

    final SafeFuture<Void> result = asyncRunner.runAfterDelay(() -> {}, Duration.ofMillis(100));

    final ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).schedule(taskCaptor.capture(), eq(100L), eq(TimeUnit.MILLISECONDS));
    taskCaptor.getValue().run();

    SafeFutureAssert.assertThatSafeFuture(result).isCompletedExceptionallyWith(exception);
  }
}
