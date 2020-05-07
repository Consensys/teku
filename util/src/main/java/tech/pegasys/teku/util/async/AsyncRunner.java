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

import com.google.common.base.Preconditions;
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

  default SafeFuture<Void> runAfterDelay(
      final ExceptionThrowingRunnable action, long delayAmount, TimeUnit delayUnit) {
    return runAfterDelay(() -> SafeFuture.fromRunnable(action), delayAmount, delayUnit);
  }

  default SafeFuture<Void> getDelayedFuture(long delayAmount, TimeUnit delayUnit) {
    return runAfterDelay(() -> SafeFuture.COMPLETE, delayAmount, delayUnit);
  }

  /**
   * Schedules the recurrent task which will be repeatedly executed with the specified delay.
   *
   * <p>The returned {@code Future} can be used to cancel the task. Note that {@link
   * SafeFuture#cancel(boolean)} doesn't interrupt already running task. When cancelled the returned
   * Future will immediately be exceptionally completed with {@link
   * java.util.concurrent.CancellationException}
   *
   * If the {@code runnable} throws exception at any iteration then the returned Future would
   * be completed exceptionally and the task will not be executed any more
   */
  default SafeFuture<Void> runWithFixedDelay(
      ExceptionThrowingRunnable runnable, long delayAmount, TimeUnit delayUnit) {

    SafeFuture<Void> task = new SafeFuture<>();
    FutureUtil.runWithFixedDelay(this, runnable, task, delayAmount, delayUnit, null);
    return task;
  }

  /**
   * Schedules the recurrent task which will be repeatedly executed with the specified delay.
   *
   * <p>The returned {@code Future} can be used to cancel the task. Note that {@link
   * SafeFuture#cancel(boolean)} doesn't interrupt already running task. When cancelled the returned
   * Future will immediately be exceptionally completed with {@link
   * java.util.concurrent.CancellationException}
   *
   * Whenever the {@code runnable} throws exception it is notified to the {@code exceptionHandler}
   * and the task recurring executions are not interrupted
   */
  default SafeFuture<Void> runWithFixedDelay(
      ExceptionThrowingRunnable runnable,
      long delayAmount,
      TimeUnit delayUnit,
      Consumer<Throwable> exceptionHandler) {

    Preconditions.checkNotNull(exceptionHandler);

    SafeFuture<Void> task = new SafeFuture<>();
    FutureUtil.runWithFixedDelay(this, runnable, task, delayAmount, delayUnit, exceptionHandler);
    return task;
  }
}
