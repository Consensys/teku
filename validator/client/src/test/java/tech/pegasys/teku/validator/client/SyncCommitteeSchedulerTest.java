/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.validator.client.duties.synccommittee.SyncCommitteeScheduledDuties;

class SyncCommitteeSchedulerTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final int epochsPerSyncCommitteePeriod =
      SpecConfigAltair.required(spec.getGenesisSpecConfig()).getEpochsPerSyncCommitteePeriod();
  private final SyncCommitteeUtil syncCommitteeUtil =
      spec.getSyncCommitteeUtilRequired(UInt64.ZERO);

  private final Map<UInt64, SafeFuture<Optional<SyncCommitteeScheduledDuties>>>
      requestedDutiesByEpoch = new HashMap<>();

  @SuppressWarnings("unchecked")
  private final DutyLoader<SyncCommitteeScheduledDuties> dutyLoader = mock(DutyLoader.class);

  private final SyncCommitteeScheduledDuties duties = createScheduledDuties();
  private final Random earlySubscribeRandomSource = Mockito.mock(Random.class);

  private final SyncCommitteeScheduler scheduler =
      new SyncCommitteeScheduler(
          new StubMetricsSystem(), spec, dutyLoader, earlySubscribeRandomSource);

  @BeforeEach
  void setUp() {
    when(dutyLoader.loadDutiesForEpoch(any()))
        .thenAnswer(
            invocation -> {
              final UInt64 epoch = invocation.getArgument(0);
              return requestedDutiesByEpoch.computeIfAbsent(epoch, __ -> new SafeFuture<>());
            });
  }

  @Test
  void shouldCalculateCurrentPeriodDutiesOnFirstSlot() {
    scheduler.onSlot(UInt64.ONE);

    verify(dutyLoader).loadDutiesForEpoch(UInt64.ZERO);
  }

  @Test
  void shouldNotReloadDutiesAtNextEpochWhenInSameSyncCommitteePeriod() {
    scheduler.onSlot(UInt64.ONE);
    verify(dutyLoader).loadDutiesForEpoch(UInt64.ZERO);

    scheduler.onSlot(spec.computeStartSlotAtEpoch(UInt64.ONE));
    verify(dutyLoader, never()).loadDutiesForEpoch(UInt64.ONE);
  }

  @Test
  void shouldPerformProductionForEachSlotWhenAttestationCreationDue() {
    scheduler.onSlot(UInt64.ONE);

    requestedDutiesByEpoch.get(UInt64.ZERO).complete(Optional.of(duties));

    UInt64.range(UInt64.ONE, UInt64.valueOf(10))
        .forEach(
            slot -> {
              scheduler.onAttestationCreationDue(slot);
              verify(duties).performProductionDuty(slot);
            });
  }

  @Test
  void shouldPerformAggregationForEachSlotWhenAttestationAggregationDue() {
    scheduler.onSlot(UInt64.ONE);

    requestedDutiesByEpoch.get(UInt64.ZERO).complete(Optional.of(duties));

    UInt64.range(UInt64.ONE, UInt64.valueOf(10))
        .forEach(
            slot -> {
              scheduler.onAttestationAggregationDue(slot);
              verify(duties).performAggregationDuty(slot);
            });
  }

  @Test
  void shouldCalculateNextSyncPeriodDutiesRandomNumberOfEpochsPriorToStart() {
    when(earlySubscribeRandomSource.nextInt(epochsPerSyncCommitteePeriod)).thenReturn(4);
    final UInt64 nextSyncCommitteePeriodStartEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO);
    final UInt64 subscribeEpoch = nextSyncCommitteePeriodStartEpoch.minus(4);

    scheduler.onSlot(UInt64.ONE);
    verify(dutyLoader, never()).loadDutiesForEpoch(nextSyncCommitteePeriodStartEpoch);
    scheduler.onSlot(spec.computeStartSlotAtEpoch(subscribeEpoch));

    verify(dutyLoader).loadDutiesForEpoch(nextSyncCommitteePeriodStartEpoch);
  }

  @Test
  void shouldNotSelectNewRandomNumberEachSlot() {
    when(earlySubscribeRandomSource.nextInt(epochsPerSyncCommitteePeriod)).thenReturn(4);

    scheduler.onSlot(UInt64.ONE);
    verify(earlySubscribeRandomSource).nextInt(epochsPerSyncCommitteePeriod);

    // Already picked a random epoch to subscribe, so don't pick again
    scheduler.onSlot(UInt64.valueOf(2));
    verifyNoMoreInteractions(earlySubscribeRandomSource);
  }

  @Test
  void shouldNotRecalculateDutiesEverySlot() {
    when(earlySubscribeRandomSource.nextInt(epochsPerSyncCommitteePeriod)).thenReturn(4);
    final UInt64 nextSyncCommitteePeriodStartEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO);
    final UInt64 subscribeEpoch = nextSyncCommitteePeriodStartEpoch.minus(4);
    final UInt64 subscribeSlot = spec.computeStartSlotAtEpoch(subscribeEpoch);

    scheduler.onSlot(UInt64.ONE);
    verify(dutyLoader).loadDutiesForEpoch(UInt64.ZERO);

    scheduler.onSlot(subscribeSlot);
    verify(dutyLoader).loadDutiesForEpoch(nextSyncCommitteePeriodStartEpoch);
    verifyNoMoreInteractions(dutyLoader);

    // Already calculated all the duties we need so don't calculate them again
    scheduler.onSlot(subscribeSlot.plus(1));
    scheduler.onSlot(spec.computeStartSlotAtEpoch(nextSyncCommitteePeriodStartEpoch));
    verifyNoMoreInteractions(dutyLoader);
  }

  @Test
  void shouldSwitchToNextCommitteePeriodWhenFirstSlotReached() {
    when(earlySubscribeRandomSource.nextInt(epochsPerSyncCommitteePeriod)).thenReturn(5);
    final UInt64 nextSyncCommitteePeriodStartEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO);
    final UInt64 subscribeEpoch = nextSyncCommitteePeriodStartEpoch.minus(5);
    final UInt64 subscribeSlot = spec.computeStartSlotAtEpoch(subscribeEpoch);
    final UInt64 nextSyncCommitteePeriodStartSlot =
        spec.computeStartSlotAtEpoch(nextSyncCommitteePeriodStartEpoch);
    final SyncCommitteeScheduledDuties nextDuties = createScheduledDuties();

    scheduler.onSlot(UInt64.valueOf(5));
    verify(dutyLoader).loadDutiesForEpoch(UInt64.ZERO);
    requestedDutiesByEpoch.get(UInt64.ZERO).complete(Optional.of(duties));

    scheduler.onSlot(subscribeSlot);
    verify(dutyLoader).loadDutiesForEpoch(nextSyncCommitteePeriodStartEpoch);
    requestedDutiesByEpoch.get(nextSyncCommitteePeriodStartEpoch).complete(Optional.of(nextDuties));

    // Subscribed, but still performing duties for first sync committee period
    scheduler.onAttestationCreationDue(subscribeSlot);
    verify(duties).performProductionDuty(subscribeSlot);

    scheduler.onSlot(nextSyncCommitteePeriodStartSlot);
    scheduler.onAttestationCreationDue(nextSyncCommitteePeriodStartSlot);
    verify(nextDuties).performProductionDuty(nextSyncCommitteePeriodStartSlot);
    verify(duties, never()).performProductionDuty(nextSyncCommitteePeriodStartSlot);
  }

  /**
   * Weird corner case where clocks are out of sync between validator client and beacon chain. We
   * get a block imported notice for the next slot prior to getting onSlot from local clock
   */
  @Test
  void shouldUseNextPeriodForDutiesWhenOnSlotNotYetCalled() {
    when(earlySubscribeRandomSource.nextInt(epochsPerSyncCommitteePeriod)).thenReturn(5);
    final UInt64 nextSyncCommitteePeriodStartEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO);
    final UInt64 subscribeEpoch = nextSyncCommitteePeriodStartEpoch.minus(5);
    final UInt64 subscribeSlot = spec.computeStartSlotAtEpoch(subscribeEpoch);
    final UInt64 nextSyncCommitteePeriodStartSlot =
        spec.computeStartSlotAtEpoch(nextSyncCommitteePeriodStartEpoch);
    final SyncCommitteeScheduledDuties nextDuties = createScheduledDuties();

    // Trigger calculation of duties for both current and next periods
    scheduler.onSlot(subscribeSlot);
    requestedDutiesByEpoch.get(UInt64.ZERO).complete(Optional.of(duties));
    requestedDutiesByEpoch.get(nextSyncCommitteePeriodStartEpoch).complete(Optional.of(nextDuties));

    // Unexpectedly jump ahead to the next committee period without getting a slot event first
    scheduler.onAttestationCreationDue(nextSyncCommitteePeriodStartSlot);
    scheduler.onAttestationAggregationDue(nextSyncCommitteePeriodStartSlot);

    // Should use duties from the next period
    verify(nextDuties).performProductionDuty(nextSyncCommitteePeriodStartSlot);
    verify(nextDuties).performAggregationDuty(nextSyncCommitteePeriodStartSlot);

    // Should not perform duties if the epoch is past the end of the next sync committee period
    final UInt64 tooFarInFutureEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(
            nextSyncCommitteePeriodStartEpoch);
    final UInt64 tooFarInFutureSlot = spec.computeStartSlotAtEpoch(tooFarInFutureEpoch);
    scheduler.onAttestationCreationDue(tooFarInFutureSlot);
    scheduler.onAttestationAggregationDue(tooFarInFutureSlot);

    verify(duties, never()).performProductionDuty(tooFarInFutureSlot);
    verify(duties, never()).performAggregationDuty(tooFarInFutureSlot);
    verify(nextDuties, never()).performProductionDuty(tooFarInFutureSlot);
    verify(nextDuties, never()).performAggregationDuty(tooFarInFutureSlot);
  }

  @Test
  void shouldRecalculateDutiesWhenBeaconNodeRestarts() {
    scheduler.onSlot(UInt64.ZERO);
    verify(dutyLoader).loadDutiesForEpoch(UInt64.ZERO);

    // Reconnecting the event stream may mean the node restarted so recalculate duties to ensure
    // subscriptions are refreshed
    scheduler.onPossibleMissedEvents();
    verify(dutyLoader, times(2)).loadDutiesForEpoch(UInt64.ZERO);
  }

  private SyncCommitteeScheduledDuties createScheduledDuties() {
    final SyncCommitteeScheduledDuties duties = mock(SyncCommitteeScheduledDuties.class);
    when(duties.performProductionDuty(any())).thenReturn(new SafeFuture<>());
    when(duties.performAggregationDuty(any())).thenReturn(new SafeFuture<>());
    return duties;
  }
}
