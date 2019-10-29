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

package org.ethereum.beacon.schedulers;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The collection of standard Schedulers, Scheduler factory and system time supplier Any scheduler
 * withing a system should be obtained or created via this interface
 */
public interface Schedulers {

  /** Creates default Schedulers implementation for production functioning */
  static Schedulers createDefault() {
    return new DefaultSchedulers();
  }

  /**
   * Creates the ControlledSchedulers implementation (normally for testing or simulation) with the
   * specified delegate Executor factory.
   *
   * @param delegateExecutor all the tasks are finally executed on executors created by this
   *     factory. Normally a single executor should be sufficient and could be supplied as <code>
   *     () -> mySingleExecutor</code>
   */
  static ControlledSchedulers createControlled(Supplier<Executor> delegateExecutor) {
    return new ControlledSchedulersImpl() {
      @Override
      protected Executor createDelegateExecutor() {
        return delegateExecutor.get();
      }
    };
  }

  /**
   * Creates the ControlledSchedulers implementation (normally for testing or simulation) which
   * executes all the tasks immediately on the same thread or if a task scheduled for later
   * execution then this task would be executed within appropriate {@link
   * ControlledSchedulers#setCurrentTime(long)} call
   */
  static ControlledSchedulers createControlled() {
    return createControlled(() -> Runnable::run);
  }

  /**
   * Returns the current system time This method should be used by all components to obtain the
   * current system time <code>System.currentTimeMillis()</code> (or other standard Java means) is
   * prohibited.
   */
  long getCurrentTime();

  /**
   * Scheduler to execute CPU heavy tasks This is normally based on a thread pool with the number of
   * threads equal to number of CPU cores
   */
  Scheduler cpuHeavy();

  /**
   * The scheduler to execute disk read/write tasks (like DB access, file read/write etc) and other
   * tasks with potentially short blocking time. Tasks with potentially longer blocking time (like
   * waiting for network response) is highly recommended to execute in a non-blocking (reactive)
   * manner or at least on a dedicated Scheduler
   *
   * <p>This Scheduler is normally based on a dynamic pool with sufficient number of threads
   */
  Scheduler blocking();

  /** Dedicated Scheduler for internal system asynchronous events */
  Scheduler events();

  /** Creates new single thread Scheduler with the specified thread name */
  default Scheduler newSingleThreadDaemon(String threadName) {
    return newParallelDaemon(threadName, 1);
  }

  /**
   * Creates new multi-thread Scheduler with the specified thread namePattern and number of pool
   * threads
   */
  Scheduler newParallelDaemon(String threadNamePattern, int threadPoolCount);
}
