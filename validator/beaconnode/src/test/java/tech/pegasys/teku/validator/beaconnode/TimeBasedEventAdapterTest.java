/*
 * Copyright Consensys Software Inc., 2025
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
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class TimeBasedEventAdapterTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final Spec gnosisSpec = TestSpecFactory.create(SpecMilestone.PHASE0, Eth2Network.GNOSIS);

  private final GenesisDataProvider genesisDataProvider = mock(GenesisDataProvider.class);
  private final ValidatorTimingChannel validatorTimingChannel = mock(ValidatorTimingChannel.class);

  final int millisPerSlot = spec.getSlotDurationMillis(ZERO);
  final int millisPerSlotGnosis = gnosisSpec.getSlotDurationMillis(SpecConfig.GENESIS_SLOT);

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final RepeatingTaskScheduler repeatingTaskScheduler =
      new RepeatingTaskScheduler(asyncRunner, timeProvider);

  private final TimeBasedEventAdapter eventAdapter =
      new TimeBasedEventAdapter(
          genesisDataProvider, repeatingTaskScheduler, timeProvider, validatorTimingChannel, spec);

  private final TimeBasedEventAdapter eventAdapterGnosis =
      new TimeBasedEventAdapter(
          genesisDataProvider,
          repeatingTaskScheduler,
          timeProvider,
          validatorTimingChannel,
          gnosisSpec);

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
    final int timeUntilNextSlot = (millisPerSlot / 2) - 1;
    timeProvider.advanceTimeByMillis(millisPerSlot * nextSlot - timeUntilNextSlot);

    assertThat(eventAdapter.start()).isCompleted();

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Fire slot start events when the next slot is due to start
    timeProvider.advanceTimeByMillis(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(firstSlotToFire);
    verify(validatorTimingChannel).onBlockProductionDue(firstSlotToFire);
    verifyNoMoreInteractions(validatorTimingChannel);
  }

  @Test
  void shouldScheduleAttestationEventsStartingFromNextSlot() {
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));
    final UInt64 nextSlot = UInt64.valueOf(25);
    // Starting time is before the aggregation for the current slot should happen, but should still
    // wait until the next slot to start
    final int timeUntilNextSlot = millisPerSlot - 1;
    timeProvider.advanceTimeByMillis(nextSlot.times(millisPerSlot).minus(timeUntilNextSlot));

    assertThat(eventAdapter.start()).isCompleted();

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Attestation should not fire at the start of the slot
    timeProvider.advanceTimeByMillis(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onAttestationCreationDue(nextSlot);

    // But does fire 1/3rds through the slot
    timeProvider.advanceTimeByMillis(spec.getAttestationDueMillis(nextSlot));
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationCreationDue(nextSlot);
  }

  @Test
  void shouldScheduleAttestationEventsStartingFromNextSlotForGnosis() {
    // ensure seconds per slot not divisible by 3 is tested
    assertThat(millisPerSlotGnosis % 3).isNotZero();

    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));
    final UInt64 nextSlot = UInt64.valueOf(25);
    // Starting time is before the aggregation for the current slot should happen, but should still
    // wait until the next slot to start
    final int timeUntilNextSlot = millisPerSlotGnosis - 1000;
    timeProvider.advanceTimeByMillis(nextSlot.times(millisPerSlotGnosis).minus(timeUntilNextSlot));

    assertThat(eventAdapterGnosis.start()).isCompleted();

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Attestation should not fire at the start of the slot
    timeProvider.advanceTimeByMillis(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onAttestationCreationDue(nextSlot);

    // It should also not fire at 1/3 seconds (whole number)
    final int attestationDueMillis = gnosisSpec.getAttestationDueMillis(nextSlot);
    final int offsetMillis = 1;
    timeProvider.advanceTimeByMillis(attestationDueMillis - offsetMillis);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onAttestationCreationDue(nextSlot);

    // But does fire 1/3rds in millis through the slot
    timeProvider.advanceTimeByMillis(offsetMillis);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationCreationDue(nextSlot);
  }

  @Test
  void shouldScheduleAggregateEventsStartingFromNextSlot() {
    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));
    final UInt64 nextSlot = UInt64.valueOf(25);
    // Starting time is before the aggregation for the current slot should happen, but should still
    // wait until the next slot to start
    final int timeUntilNextSlot = millisPerSlot - 1;
    timeProvider.advanceTimeByMillis(nextSlot.times(millisPerSlot).minus(timeUntilNextSlot));

    assertThat(eventAdapter.start()).isCompleted();

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Aggregation should not fire at the start of the slot
    timeProvider.advanceTimeByMillis(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onAttestationAggregationDue(nextSlot);

    // But does fire when aggregation is due
    timeProvider.advanceTimeByMillis(spec.getAggregateDueMillis(nextSlot));
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(nextSlot);
  }

  @Test
  void shouldScheduleAggregateEventsStartingFromNextSlotForGnosis() {
    // ensure seconds per slot not divisible by 3 is tested
    assertThat(gnosisSpec.getAttestationDueMillis(ZERO)).isNotZero();

    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));
    final UInt64 nextSlot = UInt64.valueOf(25);
    // Starting time is before the aggregation for the current slot should happen, but should still
    // wait until the next slot to start
    final int timeUntilNextSlot = millisPerSlotGnosis - 1;
    timeProvider.advanceTimeByMillis(nextSlot.times(millisPerSlotGnosis).minus(timeUntilNextSlot));

    assertThat(eventAdapterGnosis.start()).isCompleted();

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Aggregation should not fire at the start of the slot
    timeProvider.advanceTimeByMillis(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onAttestationAggregationDue(nextSlot);

    // should not advance early
    timeProvider.advanceTimeByMillis(gnosisSpec.getAggregateDueMillis(nextSlot) - 1L);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onAttestationAggregationDue(nextSlot);

    // But does fire 2/3rds in millis through the slot
    timeProvider.advanceTimeByMillis(1);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(nextSlot);
  }

  @Test
  void shouldScheduleEventsAtCorrectTimesStartingFromNextSlotForGloas() {
    final Spec spec = TestSpecFactory.createMinimalGloas();
    final TimeBasedEventAdapter eventAdapter =
        new TimeBasedEventAdapter(
            genesisDataProvider,
            repeatingTaskScheduler,
            timeProvider,
            validatorTimingChannel,
            spec);

    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));

    final UInt64 nextSlot = UInt64.valueOf(25);
    // Starting time is before the aggregation for the current slot should happen, but should still
    // wait until the next slot to start
    final int timeUntilNextSlot = millisPerSlot - 1;
    timeProvider.advanceTimeByMillis(nextSlot.times(millisPerSlot).minus(timeUntilNextSlot));

    assertThat(eventAdapter.start()).isCompleted();

    // Should not fire any events immediately
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // on slot and block production due should fire at the start of the slot
    timeProvider.advanceTimeByMillis(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(nextSlot);
    verify(validatorTimingChannel).onBlockProductionDue(nextSlot);

    // on attestation creation due and on sync committee creation due should fire at 25 % of the
    // slot
    timeProvider.advanceTimeByMillis(millisPerSlot / 4);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onAttestationCreationDue(nextSlot);
    verify(validatorTimingChannel).onSyncCommitteeCreationDue(nextSlot);

    // on attestation aggregation due and on contribution creation due should fire at 50 % of the
    // slot
    timeProvider.advanceTimeByMillis(millisPerSlot / 4);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onAttestationAggregationDue(nextSlot);
    verify(validatorTimingChannel).onContributionCreationDue(nextSlot);

    // on payload attestation creation due should fire at 75% of the slot
    timeProvider.advanceTimeByMillis(millisPerSlot / 4);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onPayloadAttestationCreationDue(nextSlot);
  }
}
