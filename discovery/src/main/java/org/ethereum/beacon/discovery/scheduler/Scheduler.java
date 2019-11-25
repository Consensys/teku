/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.discovery.scheduler;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Analog for standard <code>ScheduledExecutorService</code> */
public interface Scheduler {

  <T> CompletableFuture<T> execute(Callable<T> task);

  <T> CompletableFuture<T> executeWithDelay(Duration delay, Callable<T> task);

  CompletableFuture<Void> executeAtFixedRate(
      Duration initialDelay, Duration period, RunnableEx task);

  long getCurrentTime();

  default reactor.core.scheduler.Scheduler toReactor() {
    return convertToReactor(this);
  }

  default CompletableFuture<Void> executeR(Runnable task) {
    return execute(task::run);
  }

  default CompletableFuture<Void> execute(RunnableEx task) {
    return execute(
        () -> {
          task.run();
          return null;
        });
  }

  default CompletableFuture<Void> executeWithDelayR(Duration delay, Runnable task) {
    return executeWithDelay(delay, task::run);
  }

  default CompletableFuture<Void> executeWithDelay(Duration delay, RunnableEx task) {
    return executeWithDelay(
        delay,
        () -> {
          task.run();
          return null;
        });
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  default <C> CompletableFuture<C> orTimeout(
      CompletableFuture<C> future, Duration futureTimeout, Supplier<Exception> exceptionSupplier) {
    return (CompletableFuture<C>)
        CompletableFuture.anyOf(
            future,
            executeWithDelay(
                futureTimeout,
                () -> {
                  throw exceptionSupplier.get();
                }));
  }

  default reactor.core.scheduler.Scheduler convertToReactor(Scheduler scheduler) {
    if (scheduler instanceof ExecutorScheduler) {
      return new DelegatingReactorScheduler(
          reactor.core.scheduler.Schedulers.fromExecutorService(
              ((ExecutorScheduler) scheduler).getExecutorService()),
          this::getCurrentTime);
    } else {
      throw new UnsupportedOperationException(
          "Conversion from custom Scheduler to Reactor Scheduler not implemented yet.");
    }
  }
}
