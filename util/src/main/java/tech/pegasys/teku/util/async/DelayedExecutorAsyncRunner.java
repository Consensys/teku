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

import static tech.pegasys.teku.util.async.SafeFuture.propagateResult;

import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class DelayedExecutorAsyncRunner implements AsyncRunner {
  private final ExecutorFactory executorFactory;

  private DelayedExecutorAsyncRunner(ExecutorFactory executorFactory) {
    this.executorFactory = executorFactory;
  }

  public static DelayedExecutorAsyncRunner create() {
    return new DelayedExecutorAsyncRunner(CompletableFuture::delayedExecutor);
  }

  public static DelayedExecutorAsyncRunner create(Executor executor) {
    return new DelayedExecutorAsyncRunner(
        (delay, unit) -> CompletableFuture.delayedExecutor(delay, unit, executor));
  }

  @Override
  public <U> SafeFuture<U> runAsync(final Supplier<SafeFuture<U>> action) {
    final Executor executor = getAsyncExecutor();
    return runAsync(action, executor);
  }

  @Override
  public <U> SafeFuture<U> runAfterDelay(
      Supplier<SafeFuture<U>> action, long delayAmount, TimeUnit delayUnit) {
    final Executor executor = getDelayedExecutor(delayAmount, delayUnit);
    return runAsync(action, executor);
  }

  @VisibleForTesting
  <U> SafeFuture<U> runAsync(final Supplier<SafeFuture<U>> action, final Executor executor) {
    final SafeFuture<U> result = new SafeFuture<>();
    try {
      executor.execute(() -> propagateResult(action.get(), result));
    } catch (final Throwable t) {
      result.completeExceptionally(t);
    }
    return result;
  }

  private Executor getAsyncExecutor() {
    return getDelayedExecutor(-1, TimeUnit.SECONDS);
  }

  protected Executor getDelayedExecutor(long delayAmount, TimeUnit delayUnit) {
    return executorFactory.create(delayAmount, delayUnit);
  }

  private interface ExecutorFactory {
    Executor create(long delayAmount, TimeUnit delayUnit);
  }
}
