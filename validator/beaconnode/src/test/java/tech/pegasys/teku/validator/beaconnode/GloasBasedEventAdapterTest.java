/*
 * Copyright Consensys Software Inc., 2026
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class GloasBasedEventAdapterTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();

  private final ValidatorTimingChannel validatorTimingChannel = mock(ValidatorTimingChannel.class);

  final int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();
  final int secondsPerSlot = millisPerSlot / 1000;

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final RepeatingTaskScheduler repeatingTaskScheduler =
      new RepeatingTaskScheduler(asyncRunner, timeProvider);

  private final GloasTimeBasedEventAdapter eventAdapter =
      new GloasTimeBasedEventAdapter(
          repeatingTaskScheduler, timeProvider, validatorTimingChannel, () -> {}, spec);

  @Test
  void shouldScheduleAttestationEventsStartingFromNextSlot() {
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    final long nextSlot = 25;
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    eventAdapter.start(genesisTime);

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Attestation should not fire at the start of the slot
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onAttestationCreationDue(UInt64.valueOf(nextSlot));

    // But does fire 1/4 through the slot
    timeProvider.advanceTimeByMillis(millisPerSlot / 4);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationCreationDue(UInt64.valueOf(nextSlot));
  }

  @Test
  void shouldScheduleAggregateEventsStartingFromNextSlot() {
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    final long nextSlot = 25;
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    eventAdapter.start(genesisTime);

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Aggregation should not fire at the start of the slot
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onAttestationAggregationDue(UInt64.valueOf(nextSlot));

    // But does fire 2/4 through the slot
    timeProvider.advanceTimeByMillis(millisPerSlot * 2L / 4);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(UInt64.valueOf(nextSlot));
  }

  @Test
  void shouldSchedulePayloadAttestationEventsStartingFromNextSlot() {
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    final long nextSlot = 25;
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    eventAdapter.start(genesisTime);

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Payload attestation should not fire at the start of the slot
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never())
        .onPayloadAttestationCreationDue(UInt64.valueOf(nextSlot));

    // But does fire 3/4 through the slot
    timeProvider.advanceTimeByMillis(millisPerSlot * 3L / 4);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1))
        .onPayloadAttestationCreationDue(UInt64.valueOf(nextSlot));
  }

  @Test
  void shouldCallOnLastSlotExactlyOnceWhenEventsExpire() {
    final Runnable onLastSlotCallback = mock(Runnable.class);
    final GloasTimeBasedEventAdapter adapter =
        new GloasTimeBasedEventAdapter(
            repeatingTaskScheduler, timeProvider, validatorTimingChannel, onLastSlotCallback, spec);

    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    adapter.setGenesisTime(genesisTime);

    final long nextSlot = 25;
    final int timeUntilNextSlot = secondsPerSlot / 2;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    final long genesisTimeMillis = genesisTime.longValue() * 1000;
    final UInt64 nextSlotStartMillis = UInt64.valueOf(genesisTimeMillis + nextSlot * millisPerSlot);
    final UInt64 expiryMillis = UInt64.valueOf(genesisTimeMillis + (nextSlot + 1) * millisPerSlot);

    adapter.scheduleDuties(nextSlotStartMillis, Optional.of(expiryMillis));

    // Advance to slot start — slot event fires and then expires, triggering onLastSlot
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(UInt64.valueOf(nextSlot));
    verify(onLastSlotCallback, times(1)).run();

    // Advance through rest of slot — duties fire and expire, but onLastSlot is NOT called again
    timeProvider.advanceTimeBySeconds(secondsPerSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(onLastSlotCallback, times(1)).run();
  }

  @Test
  void shouldScheduleSyncCommitteeEventsStartingFromNextSlot() {
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    final long nextSlot = 25;
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    eventAdapter.start(genesisTime);

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Sync committee should not fire at the start of the slot
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onSyncCommitteeCreationDue(UInt64.valueOf(nextSlot));

    // But does fire 1/4 through the slot (same as attestation in GLOAS)
    timeProvider.advanceTimeByMillis(millisPerSlot / 4);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onSyncCommitteeCreationDue(UInt64.valueOf(nextSlot));
  }

  @Test
  void shouldScheduleContributionEventsStartingFromNextSlot() {
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    final long nextSlot = 25;
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    eventAdapter.start(genesisTime);

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Contribution should not fire at the start of the slot
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onContributionCreationDue(UInt64.valueOf(nextSlot));

    // But does fire 2/4 through the slot (same as aggregation in GLOAS)
    timeProvider.advanceTimeByMillis(millisPerSlot * 2L / 4);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onContributionCreationDue(UInt64.valueOf(nextSlot));
  }
}
