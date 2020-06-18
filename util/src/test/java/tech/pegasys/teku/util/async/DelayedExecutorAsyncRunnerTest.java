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

package tech.pegasys.teku.util.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.util.Waiter.waitFor;
import static tech.pegasys.teku.util.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class DelayedExecutorAsyncRunnerTest {

  private DelayedExecutorAsyncRunner asyncRunner;

  @BeforeEach
  void setUp() {
    asyncRunner = DelayedExecutorAsyncRunner.create();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void runAsync_shouldExecuteActionWithExecutorAndReturnResult() {
    final SafeFuture<String> actionResult = new SafeFuture<>();
    final Executor executor = mock(Executor.class);
    final Supplier<SafeFuture<String>> action = mock(Supplier.class);
    when(action.get()).thenReturn(actionResult);

    final SafeFuture<String> result = asyncRunner.runAsync(action, executor);

    final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).execute(captor.capture());
    final Runnable executedRunnable = captor.getValue();

    executedRunnable.run();
    verify(action).get();
    assertThat(result).isNotDone();

    actionResult.complete("Yay");
    assertThat(result).isCompletedWithValue("Yay");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void runAsync_shouldExecuteActionWithExecutorAndReturnExceptionalResult() {
    final SafeFuture<String> actionResult = new SafeFuture<>();
    final Executor executor = mock(Executor.class);
    final Supplier<SafeFuture<String>> action = mock(Supplier.class);
    when(action.get()).thenReturn(actionResult);

    final SafeFuture<String> result = asyncRunner.runAsync(action, executor);

    final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).execute(captor.capture());
    final Runnable executedRunnable = captor.getValue();

    executedRunnable.run();
    verify(action).get();
    assertThat(result).isNotDone();

    final RuntimeException exception = new RuntimeException("Nope");
    actionResult.completeExceptionally(exception);
    assertThatSafeFuture(result).isCompletedExceptionallyWith(exception);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void runAsync_shouldCompleteExceptionallyWhenExecutorFails() {
    final Executor executor = mock(Executor.class);
    final Supplier<SafeFuture<String>> action = mock(Supplier.class);
    final RuntimeException exception = new RuntimeException("Nope");
    doThrow(exception).when(executor).execute(any());

    final SafeFuture<String> result = asyncRunner.runAsync(action, executor);

    verify(action, never()).get();
    assertThatSafeFuture(result).isCompletedExceptionallyWith(exception);
  }

  @Test
  void runAsyc_shouldCompleteExceptionallyWhenSupplierThrowsException() {
    final RuntimeException exception = new RuntimeException("My bad...");
    final AtomicReference<Throwable> executorException = new AtomicReference<>();
    // Real executors use a different thread so exceptions don't propagate back out of execute
    final Executor executor =
        action -> {
          try {
            action.run();
          } catch (final Throwable t) {
            executorException.set(t);
          }
        };
    final Supplier<SafeFuture<String>> action =
        () -> {
          throw exception;
        };

    final SafeFuture<String> result = asyncRunner.runAsync(action, executor);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCause(exception);
    assertThat(executorException).hasValue(null);
  }

  @Test
  public void testRecurrentTaskCancel() throws Exception {
    AtomicInteger counter = new AtomicInteger();
    Cancellable task =
        asyncRunner.runWithFixedDelay(
            counter::incrementAndGet, 100, TimeUnit.MILLISECONDS, t -> {});
    waitFor(() -> assertThat(counter).hasValueGreaterThan(3));
    task.cancel();
    int cnt1 = counter.get();
    Thread.sleep(500);
    // 1 task may be completing during the cancel() call
    assertThat(counter).hasValueLessThanOrEqualTo(cnt1 + 1);
  }

  @Test
  public void testRecurrentTaskExceptionHandler() {
    AtomicInteger counter = new AtomicInteger();
    AtomicReference<Throwable> exception = new AtomicReference<>();
    Cancellable task =
        asyncRunner.runWithFixedDelay(
            () -> {
              if (counter.incrementAndGet() == 3) {
                throw new RuntimeException("Ups");
              }
            },
            100,
            TimeUnit.MILLISECONDS,
            exception::set);
    waitFor(() -> assertThat(counter).hasValueGreaterThan(3));
    assertThat(exception.get()).hasMessageContaining("Ups");
    task.cancel();
  }
}
