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

package tech.pegasys.artemis.util.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.async.SafeFutureTest.assertExceptionallyCompletedWith;

import java.util.concurrent.Executor;
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
    assertExceptionallyCompletedWith(result, exception);
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
    assertExceptionallyCompletedWith(result, exception);
  }
}
