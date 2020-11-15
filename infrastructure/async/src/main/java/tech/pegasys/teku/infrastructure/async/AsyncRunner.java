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

import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface AsyncRunner {

  default SafeFuture<Void> runAsync(final ExceptionThrowingRunnable action) {
    return runAsync(() -> SafeFuture.fromRunnable(action));
  }

  <U> SafeFuture<U> runAsync(final Supplier<SafeFuture<U>> action);

  <U> SafeFuture<U> runAfterDelay(
      Supplier<SafeFuture<U>> action, long delayAmount, TimeUnit delayUnit);

  void shutdown();

  default <U> SafeFuture<U> runAsync(final ExceptionThrowingSupplier<U> action) {
    return runAsync(() -> SafeFuture.of(action));
  }

  default SafeFuture<Void> runAfterDelay(
      final ExceptionThrowingRunnable action, long delayAmount, TimeUnit delayUnit) {
    return runAfterDelay(() -> SafeFuture.fromRunnable(action), delayAmount, delayUnit);
  }

  default SafeFuture<Void> getDelayedFuture(final Duration delay) {
    return runAfterDelay(() -> SafeFuture.COMPLETE, delay.toMillis(), TimeUnit.MILLISECONDS);
  }

  default SafeFuture<Void> getDelayedFuture(long delayAmount, TimeUnit delayUnit) {
    return runAfterDelay(() -> SafeFuture.COMPLETE, delayAmount, delayUnit);
  }

  /**
   * Schedules the recurrent task which will be repeatedly executed with the specified delay.
   *
   * <p>The returned instance can be used to cancel the task. Note that {@link Cancellable#cancel()}
   * doesn't interrupt already running task.
   *
   * <p>Whenever the {@code runnable} throws exception it is notified to the {@code
   * exceptionHandler} and the task recurring executions are not interrupted
   */
  default Cancellable runWithFixedDelay(
      ExceptionThrowingRunnable runnable,
      long delayAmount,
      TimeUnit delayUnit,
      Consumer<Throwable> exceptionHandler) {

    Preconditions.checkNotNull(exceptionHandler);

    Cancellable cancellable = FutureUtil.createCancellable();
    FutureUtil.runWithFixedDelay(
        this, runnable, cancellable, delayAmount, delayUnit, exceptionHandler);
    return cancellable;
  }
}
