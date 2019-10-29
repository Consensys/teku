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

import java.util.function.Consumer;

/**
 * Processes events submitted via {@link #newEvent(T)} with the specified <code>eventProcessor
 * </code> on the specified <code>scheduler</code>.
 *
 * <p>Guarantees that the latest event would be processed, though other intermediate events could be
 * skipped.
 *
 * <p>Skips subsequent events if any previous is still processing. Avoids creating scheduling a task
 * for each event thus allowing frequent events submitting.
 */
public class LatestExecutor<T> {
  private final Scheduler scheduler;
  private final Consumer<T> eventProcessor;
  private T latestEvent = null;
  private boolean processingEvent;

  public LatestExecutor(Scheduler scheduler, Consumer<T> eventProcessor) {
    this.scheduler = scheduler;
    this.eventProcessor = eventProcessor;
  }

  /**
   * Submits a new event for processing. This particular event may not be processed if a subsequent
   * event submitted shortly
   */
  public synchronized void newEvent(T event) {
    latestEvent = event;
    startEvent();
  }

  private synchronized void startEvent() {
    if (!processingEvent) {
      if (latestEvent != null) {
        T event = latestEvent;
        latestEvent = null;

        processingEvent = true;
        scheduler.execute(() -> runEvent(event));
      }
    }
  }

  private void runEvent(T event) {
    eventProcessor.accept(event);
    synchronized (this) {
      processingEvent = false;
    }
    startEvent();
  }
}
