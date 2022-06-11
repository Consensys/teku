/*
 * Copyright ConsenSys Software Inc., 2022
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
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

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
   * Schedules a repeating event in seconds, but can be changed to use millis. If the initial
   * invocation time has already been reached, the task will be executed immediately. The task will
   * then try to repeat repeatingPeriod after that initial invocation time.
   *
   * <p>It is guaranteed that only one instance of the task will execute at a time. If a task is
   * still executing when the next repeat is due, the next repeat will be executed immediately after
   * the first task completes. Tasks are not skipped because of these delays but the scheduled and
   * actual time is provided when the task is executed. Tasks should use that information to skip
   * unnecessary work when events are delayed to allow the system to catch up.
   *
   * @param initialInvocationTime the time in epoch that the task should first be executed.
   * @param repeatingPeriod the period after the previous execution was due that the next execution
   *     should occur
   * @param useMillis use millis instead of seconds
   * @param task the task to execute. If useMillis is true, then {@link RepeatingTask#execute} will
   *     use epoch millis instead of epoch seconds.
   */
  private void scheduleRepeatingEvent(
      final UInt64 initialInvocationTime,
      final UInt64 repeatingPeriod,
      final boolean useMillis,
      final RepeatingTask task) {
    scheduleEvent(new TimedEvent(initialInvocationTime, repeatingPeriod, useMillis, task));
  }

  public void scheduleRepeatingEvent(
      final UInt64 initialInvocationTime, final UInt64 repeatingPeriod, final RepeatingTask task) {
    scheduleRepeatingEvent(initialInvocationTime, repeatingPeriod, false, task);
  }

  public void scheduleRepeatingEventInMillis(
      final UInt64 initialInvocationTime, final UInt64 repeatingPeriod, final RepeatingTask task) {
    scheduleRepeatingEvent(initialInvocationTime, repeatingPeriod, true, task);
  }

  private void scheduleEvent(final TimedEvent event) {
    UInt64 nowMs = timeProvider.getTimeInMillis();
    UInt64 dueMs = getDueMs(event);
    // First execute any already due executions
    while (nowMs.isGreaterThanOrEqualTo(dueMs)) {
      executeEvent(event);
      // Update both now and due in case another repeat because due while we were executing
      nowMs = timeProvider.getTimeInMillis();
      dueMs = getDueMs(event);
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

  private UInt64 getDueMs(final TimedEvent event) {
    return event.useMillis ? event.getNextDue() : secondsToMillis(event.getNextDue());
  }

  private void executeEvent(final TimedEvent event) {
    try {
      UInt64 actualTime = getActualTime(event);
      event.execute(actualTime);
    } catch (final Throwable t) {
      Thread.currentThread()
          .getUncaughtExceptionHandler()
          .uncaughtException(Thread.currentThread(), t);
    }
  }

  private UInt64 getActualTime(final TimedEvent event) {
    return event.useMillis ? timeProvider.getTimeInMillis() : timeProvider.getTimeInSeconds();
  }

  private static class TimedEvent {
    private UInt64 nextDue;
    private final UInt64 repeatPeriod;
    private final boolean useMillis;
    private final RepeatingTask action;

    private TimedEvent(
        final UInt64 nextDue,
        final UInt64 repeatPeriod,
        final boolean useMillis,
        final RepeatingTask action) {
      this.nextDue = nextDue;
      this.repeatPeriod = repeatPeriod;
      this.useMillis = useMillis;
      this.action = action;
    }

    public UInt64 getNextDue() {
      return nextDue;
    }

    public void execute(final UInt64 actualTime) {
      checkArgument(
          actualTime.isGreaterThanOrEqualTo(nextDue),
          "Executing task before it is due. Scheduled " + nextDue + " currently " + actualTime);
      try {
        action.execute(nextDue, actualTime);
      } finally {
        moveToNextScheduledTime();
      }
    }

    public void moveToNextScheduledTime() {
      nextDue = nextDue.plus(repeatPeriod);
    }
  }

  public interface RepeatingTask {
    void execute(UInt64 scheduledTime, UInt64 actualTime);
  }
}
