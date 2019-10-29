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

package org.ethereum.beacon.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Schedules `runnable` in delay which is set by constructor. When runnable is renewed by putting it
 * in map again, old task is cancelled and removed. Task are equalled by the <Key>
 */
public class ExpirationScheduler<Key> {
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final long delay;
  private final TimeUnit timeUnit;
  private Map<Key, ScheduledFuture> expirationTasks = new ConcurrentHashMap<>();

  public ExpirationScheduler(long delay, TimeUnit timeUnit) {
    this.delay = delay;
    this.timeUnit = timeUnit;
  }

  /**
   * Puts scheduled task and renews (cancelling old) timeout for the task associated with the key
   *
   * @param key Task key
   * @param runnable Task
   */
  public void put(Key key, Runnable runnable) {
    cancel(key);
    ScheduledFuture future =
        scheduler.schedule(
            () -> {
              runnable.run();
              expirationTasks.remove(key);
            },
            delay,
            timeUnit);
    expirationTasks.put(key, future);
  }

  /** Cancels task for key and removes it from storage */
  public void cancel(Key key) {
    synchronized (this) {
      if (expirationTasks.containsKey(key)) {
        expirationTasks.remove(key).cancel(true);
      }
    }
  }
}
