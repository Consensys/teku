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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
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

  @Test
  void shouldExecuteEventImmediatelyWhenAlreadyDue() {
    eventQueue.scheduleRepeatingEvent(timeProvider.getTimeInSeconds(), UInt64.valueOf(12), action);
    verify(action).execute(timeProvider.getTimeInSeconds(), timeProvider.getTimeInSeconds());
  }

  @Test
  void shouldExecuteEventImmediatelyMultipleTimesWhenMultipleRepeatsAreAlreadyDue() {
    eventQueue.scheduleRepeatingEvent(
        timeProvider.getTimeInSeconds().minus(24), UInt64.valueOf(12), action);
    verify(action)
        .execute(timeProvider.getTimeInSeconds().minus(24), timeProvider.getTimeInSeconds());
    verify(action)
        .execute(timeProvider.getTimeInSeconds().minus(12), timeProvider.getTimeInSeconds());
    verify(action).execute(timeProvider.getTimeInSeconds(), timeProvider.getTimeInSeconds());
  }

  @Test
  void shouldDelayExecutionUntilEventIsDue() {
    eventQueue.scheduleRepeatingEvent(
        timeProvider.getTimeInSeconds().plus(100), UInt64.valueOf(12), action);

    asyncRunner.executeDueActionsRepeatedly();
    verifyNoInteractions(action);

    // Task executes when time becomes due
    timeProvider.advanceTimeBySeconds(100);
    // Delayed action fires
    asyncRunner.executeDueActionsRepeatedly();
    // Action is executed async
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(timeProvider.getTimeInSeconds(), timeProvider.getTimeInSeconds());
    verifyNoMoreInteractions(action);
  }

  @Test
  void shouldExecuteEventAgainAfterRepeatPeriod() {
    final UInt64 repeatPeriod = UInt64.valueOf(12);
    // Action executes immediately
    eventQueue.scheduleRepeatingEvent(timeProvider.getTimeInSeconds(), repeatPeriod, action);
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(timeProvider.getTimeInSeconds(), timeProvider.getTimeInSeconds());

    timeProvider.advanceTimeBySeconds(repeatPeriod.longValue());
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(timeProvider.getTimeInSeconds(), timeProvider.getTimeInSeconds());
  }

  @Test
  void shouldInterleaveMultipleRepeatingEvents() {
    final RepeatingTask secondAction = mock(RepeatingTask.class);
    eventQueue.scheduleRepeatingEvent(timeProvider.getTimeInSeconds(), UInt64.valueOf(6), action);
    eventQueue.scheduleRepeatingEvent(
        timeProvider.getTimeInSeconds(), UInt64.valueOf(5), secondAction);

    // Both execute initially
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(timeProvider.getTimeInSeconds(), timeProvider.getTimeInSeconds());
    verify(secondAction).execute(timeProvider.getTimeInSeconds(), timeProvider.getTimeInSeconds());

    // Then second action is scheduled 5 seconds later
    timeProvider.advanceTimeBySeconds(5);
    asyncRunner.executeDueActionsRepeatedly();
    verify(secondAction).execute(timeProvider.getTimeInSeconds(), timeProvider.getTimeInSeconds());
    verifyNoMoreInteractions(action);

    // And action one second after that
    timeProvider.advanceTimeBySeconds(1);
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(timeProvider.getTimeInSeconds(), timeProvider.getTimeInSeconds());
    verifyNoMoreInteractions(secondAction);
  }

  @Test
  void shouldReportScheduledAndActualExecutionTimeWhenTaskIsDelayed() {
    final UInt64 scheduledTime = timeProvider.getTimeInSeconds().minus(5);
    eventQueue.scheduleRepeatingEvent(scheduledTime, UInt64.valueOf(10), action);

    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(scheduledTime, timeProvider.getTimeInSeconds());
  }
}
