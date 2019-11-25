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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ExecutorScheduler implements Scheduler {

  private final ScheduledExecutorService executorService;
  private final Supplier<Long> timeSupplier;
  private reactor.core.scheduler.Scheduler cachedReactor;

  public ExecutorScheduler(ScheduledExecutorService executorService, Supplier<Long> timeSupplier) {
    this.executorService = executorService;
    this.timeSupplier = timeSupplier;
  }

  @Override
  public <T> CompletableFuture<T> execute(Callable<T> task) {
    CompletableFuture<T> future = new CompletableFuture<>();
    executorService.execute(
        () -> {
          try {
            future.complete(task.call());
          } catch (Throwable t) {
            future.completeExceptionally(t);
          }
        });
    return future;
  }

  @Override
  public <T> CompletableFuture<T> executeWithDelay(Duration delay, Callable<T> task) {
    CompletableFuture<T> future = new CompletableFuture<>();
    executorService.schedule(
        () -> {
          try {
            future.complete(task.call());
          } catch (Throwable t) {
            future.completeExceptionally(t);
          }
        },
        delay.toMillis(),
        TimeUnit.MILLISECONDS);
    return future;
  }

  @Override
  @SuppressWarnings({"rawtypes"})
  public CompletableFuture<Void> executeAtFixedRate(
      Duration initialDelay, Duration period, RunnableEx task) {

    ScheduledFuture<?>[] scheduledFuture = new ScheduledFuture[1];
    CompletableFuture<Void> ret =
        new CompletableFuture<Void>() {
          @Override
          public boolean cancel(boolean mayInterruptIfRunning) {
            return scheduledFuture[0].cancel(mayInterruptIfRunning);
          }
        };
    scheduledFuture[0] =
        executorService.scheduleAtFixedRate(
            () -> {
              try {
                task.run();
              } catch (Throwable e) {
                ret.completeExceptionally(e);
                throw new RuntimeException(e);
              }
            },
            initialDelay.toMillis(),
            period.toMillis(),
            TimeUnit.MILLISECONDS);

    return ret;
  }

  @Override
  public reactor.core.scheduler.Scheduler toReactor() {
    if (cachedReactor == null) {
      cachedReactor = convertToReactor(this);
    }
    return cachedReactor;
  }

  @Override
  public long getCurrentTime() {
    return timeSupplier.get();
  }

  public ScheduledExecutorService getExecutorService() {
    return executorService;
  }
}
