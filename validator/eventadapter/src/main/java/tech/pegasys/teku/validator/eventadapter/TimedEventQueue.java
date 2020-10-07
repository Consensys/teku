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

package tech.pegasys.teku.validator.eventadapter;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingRunnable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.time.TimeProvider;

public class TimedEventQueue {
  private final PriorityQueue<TimedEvent> eventQueue =
      new PriorityQueue<>(Comparator.comparing(TimedEvent::getTimeDue));

  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;

  private boolean executing = false;

  public TimedEventQueue(final AsyncRunner asyncRunner, final TimeProvider timeProvider) {
    this.asyncRunner = asyncRunner;
    this.timeProvider = timeProvider;
  }

  public synchronized void scheduleEvent(
      final UInt64 timeDueInSeconds, final ExceptionThrowingRunnable action) {
    eventQueue.add(new TimedEvent(timeDueInSeconds, action));
    scheduleNextEvent();
  }

  private void scheduleNextEvent() {
    if (executing) {
      // Already processing events, leave it to run.
      return;
    }
    executing = true;
    try {
      // Remove and execute any tasks that are due or overdue
      TimedEvent nextEvent = eventQueue.poll();
      while (nextEvent != null
          && timeProvider.getTimeInSeconds().isGreaterThanOrEqualTo(nextEvent.getTimeDue())) {
        executeEvent(nextEvent);
        nextEvent = eventQueue.poll();
      }
      if (nextEvent == null) {
        return;
      }
      // Otherwise schedule the next task for when it becomes due
      final UInt64 nowMs = timeProvider.getTimeInMillis();
      final UInt64 timeDueMs = nextEvent.getTimeDue().times(TimeProvider.MILLIS_PER_SECOND);
      if (nowMs.isGreaterThanOrEqualTo(timeDueMs)) {
        // Became due during the calculations, execute immediately
        onNextEventDue(nextEvent);
      } else {
        final TimedEvent event = nextEvent;
        asyncRunner
            .runAfterDelay(
                () -> onNextEventDue(event),
                timeDueMs.minus(nowMs).longValue(),
                TimeUnit.MILLISECONDS)
            .reportExceptions(); // TODO: Probably should keep retrying if rejected?
      }
    } finally {
      executing = false;
    }
  }

  private void onNextEventDue(final TimedEvent dueEvent) {
    executeEvent(dueEvent);
    scheduleNextEvent();
  }

  private void executeEvent(final TimedEvent nextEvent) {
    try {
      // TODO: Should we be executing this on a different thread?
      nextEvent.getAction().run();
    } catch (final Throwable e) {
      e.printStackTrace();
      Thread.currentThread()
          .getUncaughtExceptionHandler()
          .uncaughtException(Thread.currentThread(), e);
    }
  }

  private static final class TimedEvent {
    private final UInt64 timeDue;
    private final ExceptionThrowingRunnable action;

    private TimedEvent(final UInt64 timeDue, final ExceptionThrowingRunnable action) {
      this.timeDue = timeDue;
      this.action = action;
    }

    public UInt64 getTimeDue() {
      return timeDue;
    }

    public ExceptionThrowingRunnable getAction() {
      return action;
    }
  }
}
