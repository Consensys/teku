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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class GloasBasedEventAdapterTest {

  private final Spec spec = TestSpecFactory.createMinimalWithEip7732ForkEpoch(UInt64.ONE);

  private final GenesisDataProvider genesisDataProvider = mock(GenesisDataProvider.class);
  private final ValidatorTimingChannel validatorTimingChannel = mock(ValidatorTimingChannel.class);

  final int secondsPerSlot = spec.getSecondsPerSlot(SpecConfig.GENESIS_SLOT);
  final UInt64 millisPerSlot = spec.getMillisPerSlot(SpecConfig.GENESIS_SLOT);

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final RepeatingTaskScheduler repeatingTaskScheduler =
      new RepeatingTaskScheduler(asyncRunner, timeProvider);

  private final GloasTimeBasedEventAdapter eventAdapter =
      new GloasTimeBasedEventAdapter(
          genesisDataProvider, repeatingTaskScheduler, timeProvider, validatorTimingChannel, spec);

  @Test
  void shouldScheduleEventsOnceGenesisIsKnown() {
    final SafeFuture<UInt64> genesisTimeFuture = new SafeFuture<>();
    when(genesisDataProvider.getGenesisTime()).thenReturn(genesisTimeFuture);

    final SafeFuture<Void> startResult = eventAdapter.start();
    // Returned future completes immediately so startup can complete pre-genesis
    assertThat(startResult).isCompleted();
    // But no slot timings have been scheduled yet
    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    // Once we get the genesis time, we can schedule the slot events
    genesisTimeFuture.complete(UInt64.ONE);
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
  }

  @Test
  void shouldScheduleSlotStartEventsStartingFromNextSlot() {
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));
    final long nextSlot = 26;
    final UInt64 firstSlotToFire = UInt64.valueOf(nextSlot);
    final int timeUntilNextSlot = secondsPerSlot / 2;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

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
  void shouldScheduleAttestationEventsStartingFromNextSlot() {
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));
    final long nextSlot = 25;
    // Starting time is before the aggregation for the current slot should happen, but should still
    // wait until the next slot to start
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    assertThat(eventAdapter.start()).isCompleted();

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Attestation should not fire at the start of the slot
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onAttestationCreationDue(UInt64.valueOf(nextSlot));

    // But does fire 1/4rds through the slot
    timeProvider.advanceTimeByMillis(millisPerSlot.dividedBy(4));
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationCreationDue(UInt64.valueOf(nextSlot));
  }

  @Test
  void shouldScheduleAggregateEventsStartingFromNextSlot() {
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));
    final long nextSlot = 25;
    // Starting time is before the aggregation for the current slot should happen, but should still
    // wait until the next slot to start
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    assertThat(eventAdapter.start()).isCompleted();

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Aggregation should not fire at the start of the slot
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onAttestationAggregationDue(UInt64.valueOf(nextSlot));

    // But does fire 2/4rds through the slot
    timeProvider.advanceTimeByMillis(millisPerSlot.times(2).dividedBy(4));
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(UInt64.valueOf(nextSlot));
  }

  @Test
  void shouldSchedulePayloadAttestationEventsStartingFromNextSlot() {
    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider,
            repeatingTaskScheduler,
            timeProvider,
            validatorTimingChannel,
            spec);
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));
    final long nextSlot = 25;
    // Starting time is before the aggregation for the current slot should happen, but should still
    // wait until the next slot to start
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    assertThat(eventAdapter.start()).isCompleted();

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Payload attestation should not fire at the start of the slot
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onPayloadAttestationDue(UInt64.valueOf(nextSlot));

    // But does fire 3/4 through the slot
    final UInt64 millisPerSlot = spec.getMillisPerSlot(SpecConfig.GENESIS_SLOT);
    timeProvider.advanceTimeByMillis(millisPerSlot.times(3).dividedBy(4));
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onPayloadAttestationDue(UInt64.valueOf(nextSlot));
  }

  @Test
  void shouldNotSchedulePayloadAttestationEventsPreGloas() {
    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider,
            repeatingTaskScheduler,
            timeProvider,
            validatorTimingChannel,
            spec);
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));
    final long nextSlot = 2;
    // Starting time is before the aggregation for the current slot should happen, but should still
    // wait until the next slot to start
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    assertThat(eventAdapter.start()).isCompleted();

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Payload attestation should not fire before Gloas
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onPayloadAttestationDue(UInt64.valueOf(nextSlot));

    // Even on time (which is 3/4 slot) it shouldn't fire
    final UInt64 millisPerSlot = spec.getMillisPerSlot(SpecConfig.GENESIS_SLOT);
    timeProvider.advanceTimeByMillis(millisPerSlot.times(3).dividedBy(4));
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onPayloadAttestationDue(UInt64.valueOf(nextSlot));
  }

  @Test
  void shouldTransitionFromPhase0ToEip7732DutiesAcrossForkBoundary() {
    // Spec: minimal config, EIP7732 fork at epoch 1 (slot 8)
    // SLOTS_PER_EPOCH=8, SECONDS_PER_SLOT=6, millisPerSlot=6000
    // genesisTime=100 sec, eip7732 starts at slot 8 = 148 sec
    final UInt64 genesisTime = timeProvider.getTimeInSeconds(); // 100 sec
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));

    // Start at mid-slot 5 (133 sec), so next slot is 6 (pre-EIP7732)
    timeProvider.advanceTimeBySeconds(33); // 100 + 33 = 133 sec

    assertThat(eventAdapter.start()).isCompleted();
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // --- Pre-EIP7732 slot 6: duties at 1/3 and 2/3 timing ---

    // Advance to slot 6 + 1/3 (138 sec). Slot 6 start at 136 sec also fires.
    timeProvider.advanceTimeByMillis(5000); // 133000 + 5000 = 138000 ms
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(UInt64.valueOf(6));
    verify(validatorTimingChannel).onBlockProductionDue(UInt64.valueOf(6));
    verify(validatorTimingChannel, times(1)).onAttestationCreationDue(UInt64.valueOf(6));
    verify(validatorTimingChannel, never()).onAttestationAggregationDue(UInt64.valueOf(6));

    // Advance to slot 6 + 2/3 (140 sec)
    timeProvider.advanceTimeByMillis(2000); // 140000 ms
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(UInt64.valueOf(6));
    verify(validatorTimingChannel, never()).onPayloadAttestationDue(UInt64.valueOf(6));

    // --- Slot 7: last pre-EIP7732 slot, old duties expire after firing ---

    // Advance to slot 7 + 1/3 (144 sec). Slot 7 start at 142 sec also fires.
    // After attestation fires, nextDue (150000) > expiration (148000) -> expires!
    // Expiration callback schedules new attestation at slot 8 start + 1/4
    timeProvider.advanceTimeByMillis(4000); // 144000 ms
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(UInt64.valueOf(7));
    verify(validatorTimingChannel, times(1)).onAttestationCreationDue(UInt64.valueOf(7));

    // Advance to slot 7 + 2/3 (146 sec)
    // After aggregation fires, nextDue (152000) > expiration (148000) -> expires!
    // Expiration callback schedules new aggregation + payload attestation at slot 8
    timeProvider.advanceTimeByMillis(2000); // 146000 ms
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(UInt64.valueOf(7));
    verify(validatorTimingChannel, never()).onPayloadAttestationDue(UInt64.valueOf(7));

    // --- Post-EIP7732 slot 8: fork boundary! Duties at 1/4, 2/4, 3/4 timing ---

    // Advance to slot 8 start (148 sec)
    timeProvider.advanceTimeByMillis(2000); // 148000 ms
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(UInt64.valueOf(8));
    // No attestation/aggregation/payload duties should have fired yet at slot 8 start
    verify(validatorTimingChannel, never()).onAttestationCreationDue(UInt64.valueOf(8));
    verify(validatorTimingChannel, never()).onAttestationAggregationDue(UInt64.valueOf(8));
    verify(validatorTimingChannel, never()).onPayloadAttestationDue(UInt64.valueOf(8));

    // Advance to slot 8 + 1/4 (149.5 sec = 149500 ms) - EIP7732 attestation timing
    timeProvider.advanceTimeByMillis(1500); // 149500 ms
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationCreationDue(UInt64.valueOf(8));

    // Advance to slot 8 + 2/4 (151 sec = 151000 ms) - EIP7732 aggregation timing
    timeProvider.advanceTimeByMillis(1500); // 151000 ms
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(UInt64.valueOf(8));

    // Advance to slot 8 + 3/4 (152.5 sec = 152500 ms) - payload attestation (new in EIP7732!)
    timeProvider.advanceTimeByMillis(1500); // 152500 ms
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onPayloadAttestationDue(UInt64.valueOf(8));
  }
}
