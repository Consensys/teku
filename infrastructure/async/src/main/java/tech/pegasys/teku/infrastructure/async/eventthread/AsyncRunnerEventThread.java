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

package tech.pegasys.teku.infrastructure.async.eventthread;

import static com.google.common.base.Preconditions.checkState;

import java.util.concurrent.atomic.AtomicBoolean;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class AsyncRunnerEventThread implements EventThread {
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final String name;
  private final AsyncRunnerFactory asyncRunnerFactory;
  private AsyncRunner thread;

  /** The ID of the event thread. */
  private volatile long eventThreadId = -1;

  public AsyncRunnerEventThread(final String name, final AsyncRunnerFactory asyncRunnerFactory) {
    this.name = name;
    this.asyncRunnerFactory = asyncRunnerFactory;
  }

  @Override
  public void checkOnEventThread() {
    checkState(isEventThread(), "Attempting to access " + name + " resource from non-event thread");
  }

  private boolean isEventThread() {
    return Thread.currentThread().getId() == eventThreadId;
  }

  @Override
  public synchronized void start() {
    if (started.get()) {
      return;
    }
    thread = asyncRunnerFactory.create(name, 1);
    started.set(true);
  }

  @Override
  public synchronized void stop() {
    if (!started.compareAndSet(true, false)) {
      return;
    }
    thread.shutdown();
  }

  @Override
  public void executeLater(final Runnable task) {
    thread.runAsync(() -> recordEventThreadIdAndExecute(asSupplier(task))).reportExceptions();
  }

  @Override
  public <T> SafeFuture<T> execute(final ExceptionThrowingSupplier<T> callable) {
    // Note: started is only set to true after thread has been initialized so if it is true, thread
    // must be initialized.
    if (!started.get()) {
      return SafeFuture.failedFuture(new IllegalStateException("EventThread not started"));
    }
    return doExecute(callable);
  }

  @Override
  public void execute(final Runnable task) {
    if (!started.get()) {
      return;
    }
    doExecute(asSupplier(task)).reportExceptions();
  }

  private <T> SafeFuture<T> doExecute(final ExceptionThrowingSupplier<T> callable) {
    // Execute immediately if we're already on the event thread.
    if (isEventThread()) {
      return SafeFuture.of(callable);
    } else {
      return thread.runAsync(() -> recordEventThreadIdAndExecute(callable));
    }
  }

  /**
   * Record the ID of the current thread as the event thread ID and execute the specified task.
   *
   * <p>While there is only one event thread, if there is no activity for some time the thread may
   * expire and shutdown, then a different thread created to handle the next event. This is still
   * thread-safe but we need to make sure we have the latest thread ID to make isEventThread work.
   *
   * @param task the task to execute.
   */
  private <T> T recordEventThreadIdAndExecute(final ExceptionThrowingSupplier<T> task)
      throws Throwable {
    eventThreadId = Thread.currentThread().getId();
    try {
      return task.get();
    } finally {
      // Reset again to avoid problems if the thread exits and its ID is reused.
      eventThreadId = -1;
    }
  }

  private ExceptionThrowingSupplier<Object> asSupplier(final Runnable task) {
    return () -> {
      task.run();
      return null;
    };
  }
}
