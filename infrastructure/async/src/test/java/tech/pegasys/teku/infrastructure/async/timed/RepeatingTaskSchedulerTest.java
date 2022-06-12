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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler.RepeatingTask;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class RepeatingTaskSchedulerTest {
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(500);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final RepeatingTask action = mock(RepeatingTask.class);

  private final RepeatingTaskScheduler eventQueue =
      new RepeatingTaskScheduler(asyncRunner, timeProvider);

  private enum SchedulerType {
    SECONDS,
    MILLIS
  }

  @ParameterizedTest
  @EnumSource(SchedulerType.class)
  void shouldExecuteEventImmediatelyWhenAlreadyDue(SchedulerType type) {
    scheduleRepeatingEvent(type, getTime(type), UInt64.valueOf(12), action);
    verify(action).execute(getTime(type), getTime(type));
  }

  @ParameterizedTest
  @EnumSource(SchedulerType.class)
  void shouldExecuteEventImmediatelyMultipleTimesWhenMultipleRepeatsAreAlreadyDue(
      SchedulerType type) {
    scheduleRepeatingEvent(type, getTime(type).minus(24), UInt64.valueOf(12), action);
    verify(action).execute(getTime(type).minus(24), getTime(type));
    verify(action).execute(getTime(type).minus(12), getTime(type));
    verify(action).execute(getTime(type), getTime(type));
  }

  @ParameterizedTest
  @EnumSource(SchedulerType.class)
  void shouldDelayExecutionUntilEventIsDue(SchedulerType type) {
    scheduleRepeatingEvent(type, getTime(type).plus(100), UInt64.valueOf(12), action);

    asyncRunner.executeDueActionsRepeatedly();
    verifyNoInteractions(action);

    // Task executes when time becomes due
    advanceTimeBy(type, 100);
    // Delayed action fires
    asyncRunner.executeDueActionsRepeatedly();
    // Action is executed async
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(getTime(type), getTime(type));
    verifyNoMoreInteractions(action);
  }

  @ParameterizedTest
  @EnumSource(SchedulerType.class)
  void shouldExecuteEventAgainAfterRepeatPeriod(SchedulerType type) {
    final UInt64 repeatPeriod = UInt64.valueOf(12);
    // Action executes immediately
    scheduleRepeatingEvent(type, getTime(type), repeatPeriod, action);
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(getTime(type), getTime(type));

    advanceTimeBy(type, repeatPeriod.longValue());
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(getTime(type), getTime(type));
  }

  @ParameterizedTest
  @EnumSource(SchedulerType.class)
  void shouldInterleaveMultipleRepeatingEvents(SchedulerType type) {
    final RepeatingTask secondAction = mock(RepeatingTask.class);
    scheduleRepeatingEvent(type, getTime(type), UInt64.valueOf(6), action);
    scheduleRepeatingEvent(type, getTime(type), UInt64.valueOf(5), secondAction);

    // Both execute initially
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(getTime(type), getTime(type));
    verify(secondAction).execute(getTime(type), getTime(type));

    // Then second action is scheduled 5 seconds later
    advanceTimeBy(type, 5);
    asyncRunner.executeDueActionsRepeatedly();
    verify(secondAction).execute(getTime(type), getTime(type));
    verifyNoMoreInteractions(action);

    // And action one second after that
    advanceTimeBy(type, 1);
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(getTime(type), getTime(type));
    verifyNoMoreInteractions(secondAction);
  }

  @ParameterizedTest
  @EnumSource(SchedulerType.class)
  void shouldReportScheduledAndActualExecutionTimeWhenTaskIsDelayed(SchedulerType type) {
    final UInt64 scheduledTime = getTime(type).minus(5);
    scheduleRepeatingEvent(type, scheduledTime, UInt64.valueOf(10), action);

    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(scheduledTime, getTime(type));
  }

  private void scheduleRepeatingEvent(
      SchedulerType schedulerType,
      UInt64 initialInvocationTime,
      UInt64 repeatingPeriod,
      RepeatingTask task) {
    if (schedulerType == SchedulerType.SECONDS) {
      eventQueue.scheduleRepeatingEvent(initialInvocationTime, repeatingPeriod, task);
    } else {
      eventQueue.scheduleRepeatingEventInMillis(initialInvocationTime, repeatingPeriod, task);
    }
  }

  private UInt64 getTime(SchedulerType schedulerType) {
    return schedulerType == SchedulerType.SECONDS
        ? timeProvider.getTimeInSeconds()
        : timeProvider.getTimeInMillis();
  }

  private void advanceTimeBy(SchedulerType schedulerType, long time) {
    if (schedulerType == SchedulerType.SECONDS) {
      timeProvider.advanceTimeBySeconds(time);
    } else {
      timeProvider.advanceTimeByMillis(time);
    }
  }
}
