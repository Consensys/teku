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

package tech.pegasys.teku.infrastructure.async.timed;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.time.TimeProvider.MILLIS_PER_SECOND;

import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class RepeatingTaskScheduler {
  private static final Logger LOG = LogManager.getLogger();
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;

  public RepeatingTaskScheduler(final AsyncRunner asyncRunner, final TimeProvider timeProvider) {
    this.asyncRunner = asyncRunner;
    this.timeProvider = timeProvider;
  }

  /**
   * Schedules a repeating event. If the initial invocation time has already been reached, the task
   * will be executed immediately. The task will then try to repeat repeatingPeriodSeconds after
   * that initial invocation time.
   *
   * <p>It is guaranteed that only one instance of the task will execute at a time. If a task is
   * still executing when the next repeat is due, the next repeat will be executed immediately after
   * the first task completes. Tasks are not skipped because of these delays but the scheduled and
   * actual time is provided when the task is executed. Tasks should use that information to skip
   * unnecessary work when events are delayed to allow the system to catch up.
   *
   * @param initialInvocationTimeInSeconds the time in epoch seconds that the task should first be
   *     executed.
   * @param repeatingPeriodSeconds the number of seconds after the previous execution was due that
   *     the next execution should occur
   * @param task the task to execute
   */
  public void scheduleRepeatingEvent(
      final UInt64 initialInvocationTimeInSeconds,
      final UInt64 repeatingPeriodSeconds,
      final RepeatingTask task) {
    scheduleEvent(new TimedEvent(initialInvocationTimeInSeconds, repeatingPeriodSeconds, task));
  }

  private void scheduleEvent(final TimedEvent event) {
    UInt64 nowMs = timeProvider.getTimeInMillis();
    UInt64 dueMs = event.getNextDueSeconds().times(MILLIS_PER_SECOND);
    // First execute any already due executions
    while (nowMs.isGreaterThanOrEqualTo(dueMs)) {
      executeEvent(event);
      // Update both now and due in case another repeat because due while we were executing
      nowMs = timeProvider.getTimeInMillis();
      dueMs = event.getNextDueSeconds().times(MILLIS_PER_SECOND);
    }
    asyncRunner
        .runAfterDelay(
            () -> scheduleEvent(event), Duration.ofMillis(dueMs.minus(nowMs).longValue()))
        .finish(
            error -> {
              LOG.error("Failed to schedule next repeat of event. Skipping", error);
              // We may be hopelessly blocked but try to recover by moving on to the next event
              // If the thread pool and its queue are full, we should still be able to schedule a
              // task in the future.
              event.moveToNextScheduledTime();
              scheduleEvent(event);
            });
  }

  private void executeEvent(final TimedEvent event) {
    try {
      event.execute(timeProvider.getTimeInSeconds());
    } catch (final Throwable t) {
      Thread.currentThread()
          .getUncaughtExceptionHandler()
          .uncaughtException(Thread.currentThread(), t);
    }
  }

  private static class TimedEvent {
    private UInt64 nextDueSeconds;
    private final UInt64 repeatPeriodSeconds;
    private final RepeatingTask action;

    private TimedEvent(
        final UInt64 nextDueSeconds, final UInt64 repeatPeriodSeconds, final RepeatingTask action) {
      this.nextDueSeconds = nextDueSeconds;
      this.repeatPeriodSeconds = repeatPeriodSeconds;
      this.action = action;
    }

    public UInt64 getNextDueSeconds() {
      return nextDueSeconds;
    }

    public void execute(final UInt64 actualTime) {
      checkArgument(
          actualTime.isGreaterThanOrEqualTo(nextDueSeconds),
          "Executing task before it is due. Scheduled "
              + nextDueSeconds
              + " currently "
              + actualTime);
      try {
        action.execute(nextDueSeconds, actualTime);
      } finally {
        moveToNextScheduledTime();
      }
    }

    public void moveToNextScheduledTime() {
      nextDueSeconds = nextDueSeconds.plus(repeatPeriodSeconds);
    }
  }

  public interface RepeatingTask {
    void execute(UInt64 scheduledTime, UInt64 actualTime);
  }
}
