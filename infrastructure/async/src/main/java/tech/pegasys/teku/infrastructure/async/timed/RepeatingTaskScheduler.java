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

import java.util.concurrent.TimeUnit;
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

  public void scheduleRepeatingEvent(
      final UInt64 initialInvocationTimeInSeconds,
      final UInt64 repeatingPeriodSeconds,
      final RepeatingTask action) {
    scheduleEvent(new TimedEvent(initialInvocationTimeInSeconds, repeatingPeriodSeconds, action));
  }

  private void scheduleEvent(final TimedEvent event) {
    final UInt64 nowMs = timeProvider.getTimeInMillis();
    final UInt64 dueMs = event.getNextDueSeconds().times(MILLIS_PER_SECOND);
    if (nowMs.isGreaterThanOrEqualTo(dueMs)) {
      executeEvent(event);
    } else {
      asyncRunner
          .runAfterDelay(
              () -> executeEvent(event), dueMs.minus(nowMs).longValue(), TimeUnit.MILLISECONDS)
          .finish(error -> LOG.fatal("Failed to schedule next scheduled event", error));
    }
  }

  private void executeEvent(final TimedEvent event) {
    asyncRunner
        .runAsync(
            () -> {
              try {
                event.execute(timeProvider.getTimeInSeconds());
              } finally {
                // Schedule the next invocation
                scheduleEvent(event);
              }
            })
        .reportExceptions();
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
        nextDueSeconds = nextDueSeconds.plus(repeatPeriodSeconds);
      }
    }
  }

  public interface RepeatingTask {
    void execute(UInt64 scheduledTime, UInt64 actualTime);
  }
}
