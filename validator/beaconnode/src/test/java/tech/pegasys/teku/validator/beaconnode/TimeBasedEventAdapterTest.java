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

package tech.pegasys.teku.validator.beaconnode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class TimeBasedEventAdapterTest {

  private final GenesisTimeProvider genesisTimeProvider = mock(GenesisTimeProvider.class);
  private final ValidatorTimingChannel validatorTimingChannel = mock(ValidatorTimingChannel.class);

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final RepeatingTaskScheduler repeatingTaskScheduler =
      new RepeatingTaskScheduler(asyncRunner, timeProvider);

  private final TimeBasedEventAdapter eventAdapter =
      new TimeBasedEventAdapter(
          genesisTimeProvider, repeatingTaskScheduler, timeProvider, validatorTimingChannel);

  @Test
  void shouldScheduleEventsOnceGenesisIsKnown() {
    final SafeFuture<UInt64> genesisTimeFuture = new SafeFuture<>();
    when(genesisTimeProvider.getGenesisTime()).thenReturn(genesisTimeFuture);

    final SafeFuture<Void> startResult = eventAdapter.start();
    assertThat(startResult).isNotDone();
    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    genesisTimeFuture.complete(UInt64.ONE);
    assertThat(startResult).isCompleted();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
  }

  @Test
  void shouldScheduleSlotStartEventsStartingFromNextSlot() {
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisTimeProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));
    final int nextSlot = 26;
    final UInt64 firstSlotToFire = UInt64.valueOf(nextSlot);
    final int timeUntilNextSlot = Constants.SECONDS_PER_SLOT / 2;
    timeProvider.advanceTimeBySeconds(Constants.SECONDS_PER_SLOT * nextSlot - timeUntilNextSlot);

    assertThat(eventAdapter.start()).isCompleted();

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Fire slot start events when the next slot is due to start
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(firstSlotToFire);
    verify(validatorTimingChannel).onBlockProductionDue(firstSlotToFire);
    verifyNoMoreInteractions(validatorTimingChannel);
  }

  @Test
  void shouldScheduleAggregateEventsStartingFromNextSlot() {
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisTimeProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));
    final int nextSlot = 25;
    // Starting time is before the aggregation for the current slot should happen, but should still
    // wait until the next slot to start
    final int timeUntilNextSlot = Constants.SECONDS_PER_SLOT - 1;
    timeProvider.advanceTimeBySeconds(Constants.SECONDS_PER_SLOT * nextSlot - timeUntilNextSlot);

    assertThat(eventAdapter.start()).isCompleted();

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Aggregation should not fire at the start of the slot
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onAttestationAggregationDue(UInt64.valueOf(nextSlot));

    // But does fire 2/3rds through the slot
    timeProvider.advanceTimeBySeconds(Constants.SECONDS_PER_SLOT / 3 * 2);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(UInt64.valueOf(nextSlot));
  }
}
