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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class SingleThreadedForkChoiceExecutor implements ForkChoiceExecutor {
  private static final Logger LOG = LogManager.getLogger();
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final ExecutorService executor;

  private SingleThreadedForkChoiceExecutor(final ExecutorService executor) {
    this.executor = executor;
  }

  public static ForkChoiceExecutor create() {
    final ExecutorService executor =
        Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat("forkchoice-%d").build());
    return new SingleThreadedForkChoiceExecutor(executor);
  }

  @Override
  public <T> SafeFuture<T> performTask(final ForkChoiceTask<T> task) {
    final SafeFuture<T> result = new SafeFuture<>();
    try {
      executor.submit(() -> syncPerformTask(task).propagateTo(result));
    } catch (final RejectedExecutionException e) {
      if (stopped.get()) {
        LOG.debug("Ignoring fork choice task because shutdown is in progress");
      } else {
        result.completeExceptionally(e);
      }
    } catch (final Throwable t) {
      result.completeExceptionally(t);
    }
    return result;
  }

  /**
   * Perform the task, blocking the current thread until it completes. When executed on a single
   * threaded executor, this ensures that only one task can be completed at a time, without blocking
   * many threads while queuing to acquire a lock.
   */
  private <T> SafeFuture<T> syncPerformTask(final ForkChoiceTask<T> task) {
    return SafeFuture.of(() -> task.performTask().join());
  }

  @Override
  public void stop() {
    if (stopped.compareAndSet(false, true)) {
      executor.shutdownNow();
    }
  }
}
