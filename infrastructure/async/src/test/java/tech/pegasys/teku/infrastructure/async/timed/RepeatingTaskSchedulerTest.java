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

  private final RepeatingTaskScheduler eventQueue =
      new RepeatingTaskScheduler(asyncRunner, timeProvider);

  private final StubTimeProvider timeProviderMs = StubTimeProvider.withTimeInMillis(500);
  private final StubAsyncRunner asyncRunnerMs = new StubAsyncRunner(timeProviderMs);

  private final RepeatingTaskScheduler eventQueueMs =
      new RepeatingTaskScheduler(asyncRunnerMs, timeProviderMs);

  private final RepeatingTask action = mock(RepeatingTask.class);

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

  @Test
  void shouldExecuteEventImmediatelyWhenAlreadyDueMillis() {
    eventQueueMs.scheduleRepeatingEventInMillis(
        timeProviderMs.getTimeInMillis(), UInt64.valueOf(500), action);
    verify(action).execute(timeProviderMs.getTimeInMillis(), timeProviderMs.getTimeInMillis());
  }

  @Test
  void shouldExecuteEventImmediatelyMultipleTimesWhenMultipleRepeatsAreAlreadyDueMillis() {
    eventQueueMs.scheduleRepeatingEventInMillis(
        timeProviderMs.getTimeInMillis().minus(300), UInt64.valueOf(100), action);
    verify(action)
        .execute(timeProviderMs.getTimeInMillis().minus(200), timeProviderMs.getTimeInMillis());
    verify(action)
        .execute(timeProviderMs.getTimeInMillis().minus(100), timeProviderMs.getTimeInMillis());
    verify(action).execute(timeProviderMs.getTimeInMillis(), timeProviderMs.getTimeInMillis());
  }

  @Test
  void shouldDelayExecutionUntilEventIsDueMillis() {
    eventQueueMs.scheduleRepeatingEventInMillis(
        timeProviderMs.getTimeInMillis().plus(1000), UInt64.valueOf(100), action);

    asyncRunnerMs.executeDueActionsRepeatedly();
    verifyNoInteractions(action);

    // Task executes when time becomes due
    timeProviderMs.advanceTimeByMillis(1000);
    // Delayed action fires
    asyncRunnerMs.executeDueActionsRepeatedly();
    // Action is executed async
    asyncRunnerMs.executeDueActionsRepeatedly();
    verify(action).execute(timeProviderMs.getTimeInMillis(), timeProviderMs.getTimeInMillis());
    verifyNoMoreInteractions(action);
  }

  @Test
  void shouldExecuteEventAgainAfterRepeatPeriodMillis() {
    final UInt64 repeatPeriod = UInt64.valueOf(500);
    // Action executes immediately
    eventQueueMs.scheduleRepeatingEventInMillis(
        timeProviderMs.getTimeInMillis(), repeatPeriod, action);
    asyncRunnerMs.executeDueActionsRepeatedly();
    verify(action).execute(timeProviderMs.getTimeInMillis(), timeProviderMs.getTimeInMillis());

    timeProviderMs.advanceTimeByMillis(repeatPeriod.longValue());
    asyncRunnerMs.executeDueActionsRepeatedly();
    verify(action).execute(timeProviderMs.getTimeInMillis(), timeProviderMs.getTimeInMillis());
  }

  @Test
  void shouldInterleaveMultipleRepeatingEventsMillis() {
    final RepeatingTask secondAction = mock(RepeatingTask.class);
    eventQueueMs.scheduleRepeatingEventInMillis(
        timeProviderMs.getTimeInMillis(), UInt64.valueOf(1000), action);
    eventQueueMs.scheduleRepeatingEventInMillis(
        timeProviderMs.getTimeInMillis(), UInt64.valueOf(800), secondAction);

    // Both execute initially
    asyncRunnerMs.executeDueActionsRepeatedly();
    verify(action).execute(timeProviderMs.getTimeInMillis(), timeProviderMs.getTimeInMillis());
    verify(secondAction)
        .execute(timeProviderMs.getTimeInMillis(), timeProviderMs.getTimeInMillis());

    // Then second action is scheduled 800 millis later
    timeProviderMs.advanceTimeByMillis(800);
    asyncRunnerMs.executeDueActionsRepeatedly();
    verify(secondAction)
        .execute(timeProviderMs.getTimeInMillis(), timeProviderMs.getTimeInMillis());
    verifyNoMoreInteractions(action);

    // And action 200 millis after that
    timeProviderMs.advanceTimeByMillis(200);
    asyncRunnerMs.executeDueActionsRepeatedly();
    verify(action).execute(timeProviderMs.getTimeInMillis(), timeProviderMs.getTimeInMillis());
    verifyNoMoreInteractions(secondAction);
  }

  @Test
  void shouldReportScheduledAndActualExecutionTimeWhenTaskIsDelayedMillis() {
    final UInt64 scheduledTime = timeProviderMs.getTimeInMillis().minus(250);
    eventQueueMs.scheduleRepeatingEventInMillis(scheduledTime, UInt64.valueOf(1000), action);

    asyncRunnerMs.executeDueActionsRepeatedly();
    verify(action).execute(scheduledTime, timeProviderMs.getTimeInMillis());
  }
}
