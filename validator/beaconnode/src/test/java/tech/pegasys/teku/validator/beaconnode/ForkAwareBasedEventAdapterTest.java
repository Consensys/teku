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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class ForkAwareBasedEventAdapterTest {

  private final GenesisDataProvider genesisDataProvider = mock(GenesisDataProvider.class);
  private final ValidatorTimingChannel validatorTimingChannel = mock(ValidatorTimingChannel.class);

  @Test
  void shouldScheduleEventsOnceGenesisIsKnown() {
    final Spec spec = TestSpecFactory.createMinimalAltair();

    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final RepeatingTaskScheduler scheduler = new RepeatingTaskScheduler(asyncRunner, timeProvider);

    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider, scheduler, timeProvider, validatorTimingChannel, spec);

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
  void shouldTransitionFromPhase0ToAltairAtForkBoundary() {
    // PHASE0 at epoch 0, ALTAIR at epoch 1 (slot 8). Minimal: 8 slots/epoch, 6 sec/slot
    final Spec spec = TestSpecFactory.createMinimalWithAltairForkEpoch(UInt64.ONE);
    final int secondsPerSlot = spec.getGenesisSpecConfig().getSecondsPerSlot();

    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final RepeatingTaskScheduler scheduler = new RepeatingTaskScheduler(asyncRunner, timeProvider);

    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider, scheduler, timeProvider, validatorTimingChannel, spec);

    final UInt64 genesisTime = timeProvider.getTimeInSeconds(); // 100s
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));

    // Advance to slot 6 (last PHASE0 epoch, 2 slots before boundary)
    timeProvider.advanceTimeBySeconds(secondsPerSlot * 6L);

    assertThat(eventAdapter.start()).isCompleted();
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoInteractions(validatorTimingChannel);

    // --- Slot 7: last PHASE0 slot ---

    // Advance to slot 7 start
    timeProvider.advanceTimeBySeconds(secondsPerSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(UInt64.valueOf(7));
    verify(validatorTimingChannel).onBlockProductionDue(UInt64.valueOf(7));

    // Advance to slot 7 + 1/3 (attestation fires)
    timeProvider.advanceTimeBySeconds(secondsPerSlot / 3);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationCreationDue(UInt64.valueOf(7));
    // No sync committee in PHASE0
    verify(validatorTimingChannel, never()).onSyncCommitteeCreationDue(UInt64.valueOf(7));

    // Advance to slot 7 + 2/3 (aggregation fires)
    timeProvider.advanceTimeBySeconds(secondsPerSlot / 3);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(UInt64.valueOf(7));
    // No contribution in PHASE0
    verify(validatorTimingChannel, never()).onContributionCreationDue(UInt64.valueOf(7));

    // --- Slot 8: first ALTAIR slot ---

    // Advance to slot 8 start
    timeProvider.advanceTimeBySeconds(secondsPerSlot - secondsPerSlot / 3 * 2);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(UInt64.valueOf(8));

    // Advance to slot 8 + 1/3 — attestation AND sync committee should fire (ALTAIR!)
    timeProvider.advanceTimeBySeconds(secondsPerSlot / 3);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationCreationDue(UInt64.valueOf(8));
    verify(validatorTimingChannel, times(1)).onSyncCommitteeCreationDue(UInt64.valueOf(8));

    // Advance to slot 8 + 2/3 — aggregation AND contribution
    timeProvider.advanceTimeBySeconds(secondsPerSlot / 3);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(UInt64.valueOf(8));
    verify(validatorTimingChannel, times(1)).onContributionCreationDue(UInt64.valueOf(8));
  }

  @Test
  void shouldTransitionFromAltairToGloasAtForkBoundary() {
    // ALTAIR at epoch 0, GLOAS at epoch 1 (slot 8). Minimal: 8 slots/epoch, 6 sec/slot
    final Spec spec = TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.ONE);
    final int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();
    final int secondsPerSlot = millisPerSlot / 1000;

    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final RepeatingTaskScheduler scheduler = new RepeatingTaskScheduler(asyncRunner, timeProvider);

    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider, scheduler, timeProvider, validatorTimingChannel, spec);

    final UInt64 genesisTime = timeProvider.getTimeInSeconds(); // 100s
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));

    // Advance to slot 6 (2 slots before GLOAS boundary)
    timeProvider.advanceTimeBySeconds(secondsPerSlot * 6L);

    assertThat(eventAdapter.start()).isCompleted();
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoInteractions(validatorTimingChannel);

    // --- Slot 7: last pre-GLOAS slot (ALTAIR timing: 1/3, 2/3) ---

    // Advance to slot 7 + 1/3
    timeProvider.advanceTimeByMillis(millisPerSlot + millisPerSlot / 3L);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(UInt64.valueOf(7));
    verify(validatorTimingChannel, times(1)).onAttestationCreationDue(UInt64.valueOf(7));
    verify(validatorTimingChannel, times(1)).onSyncCommitteeCreationDue(UInt64.valueOf(7));

    // Advance to slot 7 + 2/3
    timeProvider.advanceTimeByMillis(millisPerSlot / 3L);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(UInt64.valueOf(7));
    verify(validatorTimingChannel, times(1)).onContributionCreationDue(UInt64.valueOf(7));
    verify(validatorTimingChannel, never()).onPayloadAttestationCreationDue(UInt64.valueOf(7));

    // --- Slot 8: first GLOAS slot (timing: 1/4, 2/4, 3/4) ---

    // Advance to slot 8 start
    timeProvider.advanceTimeByMillis(millisPerSlot - millisPerSlot / 3L * 2);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(UInt64.valueOf(8));
    verify(validatorTimingChannel, never()).onAttestationCreationDue(UInt64.valueOf(8));

    // Advance to slot 8 + 1/4 — GLOAS attestation + sync committee
    timeProvider.advanceTimeByMillis(millisPerSlot / 4L);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationCreationDue(UInt64.valueOf(8));
    verify(validatorTimingChannel, times(1)).onSyncCommitteeCreationDue(UInt64.valueOf(8));

    // Advance to slot 8 + 2/4 — GLOAS aggregation + contribution
    timeProvider.advanceTimeByMillis(millisPerSlot / 4L);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(UInt64.valueOf(8));
    verify(validatorTimingChannel, times(1)).onContributionCreationDue(UInt64.valueOf(8));

    // Advance to slot 8 + 3/4 — payload attestation (new in GLOAS)
    timeProvider.advanceTimeByMillis(millisPerSlot / 4L);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onPayloadAttestationCreationDue(UInt64.valueOf(8));
  }

  @Test
  void shouldWorkWithPhase0OnlyNetwork() {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final int secondsPerSlot = spec.getGenesisSpecConfig().getSecondsPerSlot();

    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final RepeatingTaskScheduler scheduler = new RepeatingTaskScheduler(asyncRunner, timeProvider);

    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider, scheduler, timeProvider, validatorTimingChannel, spec);

    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));

    final long nextSlot = 25;
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    assertThat(eventAdapter.start()).isCompleted();
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Advance to next slot start
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(UInt64.valueOf(nextSlot));

    // Advance to 1/3 slot — attestation fires
    timeProvider.advanceTimeBySeconds(secondsPerSlot / 3);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationCreationDue(UInt64.valueOf(nextSlot));
    // No sync committee in Phase0
    verify(validatorTimingChannel, never()).onSyncCommitteeCreationDue(UInt64.valueOf(nextSlot));

    // Advance to 2/3 slot — aggregation fires
    timeProvider.advanceTimeBySeconds(secondsPerSlot / 3);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(UInt64.valueOf(nextSlot));
    // No contribution in Phase0
    verify(validatorTimingChannel, never()).onContributionCreationDue(UInt64.valueOf(nextSlot));
    // No payload attestation
    verify(validatorTimingChannel, never())
        .onPayloadAttestationCreationDue(UInt64.valueOf(nextSlot));
  }

  @Test
  void shouldStartMidChainInAltairEraWithGloasScheduled() {
    // ALTAIR at epoch 0, GLOAS at epoch 3 (slot 24)
    final Spec spec = TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.valueOf(3));
    final int secondsPerSlot = spec.getGenesisSpecConfig().getSecondsPerSlot();

    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final RepeatingTaskScheduler scheduler = new RepeatingTaskScheduler(asyncRunner, timeProvider);

    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider, scheduler, timeProvider, validatorTimingChannel, spec);

    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));

    // Start at slot 10 (in ALTAIR era, well before GLOAS at slot 24)
    final long currentSlot = 10;
    final long nextSlot = currentSlot + 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * currentSlot + secondsPerSlot / 2);

    assertThat(eventAdapter.start()).isCompleted();
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoInteractions(validatorTimingChannel);

    // Advance to next slot start
    timeProvider.advanceTimeBySeconds(secondsPerSlot / 2);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(UInt64.valueOf(nextSlot));

    // Advance to 1/3 slot — ALTAIR attestation + sync committee should fire
    timeProvider.advanceTimeBySeconds(secondsPerSlot / 3);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationCreationDue(UInt64.valueOf(nextSlot));
    verify(validatorTimingChannel, times(1)).onSyncCommitteeCreationDue(UInt64.valueOf(nextSlot));

    // Advance to 2/3 slot — ALTAIR aggregation + contribution
    timeProvider.advanceTimeBySeconds(secondsPerSlot / 3);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(UInt64.valueOf(nextSlot));
    verify(validatorTimingChannel, times(1)).onContributionCreationDue(UInt64.valueOf(nextSlot));

    // No payload attestation in ALTAIR era
    verify(validatorTimingChannel, never())
        .onPayloadAttestationCreationDue(UInt64.valueOf(nextSlot));
  }

  @Test
  void shouldScheduleGloasEventsWhenGloasIsAtGenesis() {
    final Spec spec = TestSpecFactory.createMinimalGloas();
    final int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();
    final int secondsPerSlot = millisPerSlot / 1000;

    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final RepeatingTaskScheduler scheduler = new RepeatingTaskScheduler(asyncRunner, timeProvider);

    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider, scheduler, timeProvider, validatorTimingChannel, spec);

    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));

    final long nextSlot = 25;
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    assertThat(eventAdapter.start()).isCompleted();
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Advance to slot start
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never())
        .onPayloadAttestationCreationDue(UInt64.valueOf(nextSlot));

    // Advance to 3/4 slot — payload attestation fires
    timeProvider.advanceTimeByMillis(millisPerSlot * 3L / 4);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1))
        .onPayloadAttestationCreationDue(UInt64.valueOf(nextSlot));
  }

  @Test
  void shouldNotScheduleGloasEventsWhenGloasIsNotScheduled() {
    final Spec spec = TestSpecFactory.createMinimalElectra();
    final int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();
    final int secondsPerSlot = millisPerSlot / 1000;

    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final RepeatingTaskScheduler scheduler = new RepeatingTaskScheduler(asyncRunner, timeProvider);

    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider, scheduler, timeProvider, validatorTimingChannel, spec);

    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));

    final long nextSlot = 25;
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    assertThat(eventAdapter.start()).isCompleted();
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Advance through entire slot
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();

    timeProvider.advanceTimeByMillis(millisPerSlot * 3L / 4);
    asyncRunner.executeDueActionsRepeatedly();

    // No payload attestation should fire
    verify(validatorTimingChannel, never())
        .onPayloadAttestationCreationDue(UInt64.valueOf(nextSlot));
  }

  @Test
  void shouldNotSchedulePayloadAttestationEventsPreGloas() {
    final Spec spec = TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.ONE);
    final int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();
    final int secondsPerSlot = millisPerSlot / 1000;

    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final RepeatingTaskScheduler scheduler = new RepeatingTaskScheduler(asyncRunner, timeProvider);

    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider, scheduler, timeProvider, validatorTimingChannel, spec);

    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));
    final long nextSlot = 2;
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    assertThat(eventAdapter.start()).isCompleted();

    // Advance to start of next slot
    asyncRunner.executeDueActionsRepeatedly();
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never())
        .onPayloadAttestationCreationDue(UInt64.valueOf(nextSlot));

    // Even at 3/4 through slot, no payload attestation pre-GLOAS
    timeProvider.advanceTimeByMillis(millisPerSlot * 3L / 4);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never())
        .onPayloadAttestationCreationDue(UInt64.valueOf(nextSlot));
  }
}
