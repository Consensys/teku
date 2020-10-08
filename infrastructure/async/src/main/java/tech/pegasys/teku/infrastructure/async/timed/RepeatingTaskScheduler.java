package tech.pegasys.teku.infrastructure.async.timed;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class RepeatingTaskScheduler {
  private final Queue<TimedEvent> eventQueue =
      new PriorityBlockingQueue<>(11, Comparator.comparing(TimedEvent::getNextDueSeconds));

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
    eventQueue.add(event);
    scheduleNextEvent();
  }

  private synchronized void scheduleNextEvent() {
    // Remove and execute any tasks that are due or overdue
    TimedEvent nextEvent = eventQueue.poll();
    while (nextEvent != null
        && timeProvider.getTimeInSeconds().isGreaterThanOrEqualTo(nextEvent.getNextDueSeconds())) {
      executeEvent(nextEvent);
      nextEvent = eventQueue.poll();
    }
    if (nextEvent == null) {
      return;
    }
    // Otherwise schedule the next task for when it becomes due
    final UInt64 nowMs = timeProvider.getTimeInMillis();
    final UInt64 timeDueMs = nextEvent.getNextDueSeconds().times(TimeProvider.MILLIS_PER_SECOND);
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
  }

  private void onNextEventDue(final TimedEvent dueEvent) {
    executeEvent(dueEvent);
    scheduleNextEvent();
  }

  private void executeEvent(final TimedEvent nextEvent) {
    asyncRunner
        .runAsync(
            () -> {
              try {
                nextEvent.execute(timeProvider.getTimeInSeconds());
              } finally {
                // Schedule the next invocation
                scheduleEvent(nextEvent);
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
