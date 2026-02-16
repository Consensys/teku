/*
 * Copyright Consensys Software Inc., 2022
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
import static com.google.common.base.Preconditions.checkState;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Optional;
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
   * @param initialInvocationTimeMillis the time in epoch that the task should first be executed.
   * @param repeatingPeriodMillis the period after the previous execution was due that the next
   *     execution should occur
   * @param task the task to execute
   */
  private void scheduleRepeatingEventEventInMillis(
      final UInt64 initialInvocationTimeMillis,
      final UInt64 repeatingPeriodMillis,
      final RepeatingTask task,
      final Optional<UInt64> expirationMillis,
      final Optional<ExpirationTask> expirationAction) {
    scheduleEvent(
        new TimedEvent(
            initialInvocationTimeMillis,
            repeatingPeriodMillis,
            task,
            expirationMillis,
            expirationAction));
  }

  public void scheduleRepeatingEventInMillis(
      final UInt64 initialInvocationTime, final UInt64 repeatingPeriod, final RepeatingTask task) {
    scheduleRepeatingEventEventInMillis(
        initialInvocationTime, repeatingPeriod, task, Optional.empty(), Optional.empty());
  }

  public void scheduleRepeatingEventInMillis(
      final UInt64 initialInvocationTime,
      final UInt64 repeatingPeriod,
      final RepeatingTask task,
      final UInt64 expirationTime,
      final ExpirationTask expirationTask) {
    scheduleRepeatingEventEventInMillis(
        initialInvocationTime,
        repeatingPeriod,
        task,
        Optional.of(expirationTime),
        Optional.of(expirationTask));
  }

  private void scheduleEvent(final TimedEvent event) {
    UInt64 nowMs = timeProvider.getTimeInMillis();
    UInt64 dueMs = event.getNextDueMs();
    // First execute any already due executions
    while (nowMs.isGreaterThanOrEqualTo(dueMs)) {
      executeEvent(event);
      if (event.isExpired) {
        expireEvent(event);
        return;
      }
      // Update both now and due in case another repeat because due while we were executing
      nowMs = timeProvider.getTimeInMillis();
      dueMs = event.getNextDueMs();
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
      final UInt64 actualTime = getActualTime();
      event.execute(actualTime);
    } catch (final Throwable t) {
      Thread.currentThread()
          .getUncaughtExceptionHandler()
          .uncaughtException(Thread.currentThread(), t);
    }
  }

  private void expireEvent(final TimedEvent event) {
    try {
      final UInt64 actualTime = getActualTime();
      event.expire(actualTime);
    } catch (final Throwable t) {
      Thread.currentThread()
          .getUncaughtExceptionHandler()
          .uncaughtException(Thread.currentThread(), t);
    }
  }

  private UInt64 getActualTime() {
    return timeProvider.getTimeInMillis();
  }

  private static class TimedEvent {
    private UInt64 nextDue;
    private boolean isExpired;
    private final UInt64 repeatPeriod;
    private final RepeatingTask action;
    private final Optional<UInt64> expiration;
    private final Optional<ExpirationTask> expirationAction;

    private TimedEvent(
        final UInt64 nextDue,
        final UInt64 repeatPeriod,
        final RepeatingTask action,
        final Optional<UInt64> expiration,
        final Optional<ExpirationTask> expirationAction) {
      checkArgument(
          expiration.isPresent() == expirationAction.isPresent(),
          "expiration and expirationAction must be both specified");
      this.nextDue = nextDue;
      this.isExpired = false;
      this.repeatPeriod = repeatPeriod;
      this.action = action;
      this.expiration = expiration;
      this.expirationAction = expirationAction;
    }

    public UInt64 getNextDueMs() {
      return nextDue;
    }

    public void execute(final UInt64 actualTime) {
      checkArgument(
          actualTime.isGreaterThanOrEqualTo(nextDue),
          MessageFormat.format(
              "Executing task before it is due. Scheduled {0} currently {1}", nextDue, actualTime));
      checkState(
          !isExpired,
          MessageFormat.format(
              "Executing expired task. Expiration {0} currently {1}", expiration, actualTime));
      try {
        action.execute(nextDue, actualTime);
      } finally {
        moveToNextScheduledTime();
      }
    }

    public void expire(final UInt64 actualTime) {
      checkArgument(
          isExpired,
          MessageFormat.format(
              "Task is not expired. Expiration {0} currently {1}", expiration, actualTime));
      expirationAction.orElseThrow().execute(expiration.orElseThrow(), actualTime);
    }

    public void moveToNextScheduledTime() {
      nextDue = nextDue.plus(repeatPeriod);
      expiration.ifPresent(exp -> isExpired = exp.isLessThan(nextDue));
    }
  }

  public interface RepeatingTask {
    void execute(UInt64 scheduledTime, UInt64 actualTime);
  }

  public interface ExpirationTask {
    void execute(UInt64 expirationTime, UInt64 actualTime);
  }
}
