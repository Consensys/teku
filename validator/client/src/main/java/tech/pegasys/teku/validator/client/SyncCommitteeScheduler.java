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

import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.duties.SyncCommitteeScheduledDuties;

public class SyncCommitteeScheduler implements ValidatorTimingChannel {

  private final MetricsSystem metricsSystem;
  private final Spec spec;
  private final DutyLoader<SyncCommitteeScheduledDuties> dutyLoader;
  private final Random earlySubscribeRandomSource;

  private Optional<SyncCommitteePeriod> currentSyncCommitteePeriod = Optional.empty();
  private Optional<SyncCommitteePeriod> nextSyncCommitteePeriod = Optional.empty();

  public SyncCommitteeScheduler(
      final MetricsSystem metricsSystem,
      final Spec spec,
      final DutyLoader<SyncCommitteeScheduledDuties> dutyLoader,
      final Random earlySubscribeRandomSource) {
    this.metricsSystem = metricsSystem;
    this.spec = spec;
    this.dutyLoader = dutyLoader;
    this.earlySubscribeRandomSource = earlySubscribeRandomSource;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final Optional<SyncCommitteeUtil> maybeUtils = spec.getSyncCommitteeUtil(slot);
    if (maybeUtils.isEmpty()) {
      return;
    }
    final SyncCommitteeUtil syncCommitteeUtil = maybeUtils.get();
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    final SpecConfigAltair specConfig = SpecConfigAltair.required(spec.getSpecConfig(currentEpoch));
    if (currentSyncCommitteePeriod.isEmpty()) {
      final SyncCommitteePeriod committeePeriod =
          createSyncCommitteePeriod(
              syncCommitteeUtil,
              syncCommitteeUtil.computeFirstEpochOfCurrentSyncCommitteePeriod(currentEpoch),
              0);
      committeePeriod.calculateDuties();
      currentSyncCommitteePeriod = Optional.of(committeePeriod);
    }

    if (nextSyncCommitteePeriod.isEmpty()) {
      final UInt64 firstEpochOfNextSyncCommitteePeriod =
          syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(currentEpoch);
      final int subscribeEpochsPriorToNextSyncPeriod =
          earlySubscribeRandomSource.nextInt(specConfig.getEpochsPerSyncCommitteePeriod());
      nextSyncCommitteePeriod =
          Optional.of(
              createSyncCommitteePeriod(
                  syncCommitteeUtil,
                  firstEpochOfNextSyncCommitteePeriod,
                  subscribeEpochsPriorToNextSyncPeriod));
    }

    final SyncCommitteePeriod nextSyncCommitteePeriod = this.nextSyncCommitteePeriod.get();
    if (currentEpoch.isGreaterThanOrEqualTo(nextSyncCommitteePeriod.subscribeEpoch)) {
      nextSyncCommitteePeriod.calculateDuties();
    }
    if (currentEpoch.isGreaterThanOrEqualTo(nextSyncCommitteePeriod.periodStartEpoch)) {
      this.currentSyncCommitteePeriod = this.nextSyncCommitteePeriod;
      this.nextSyncCommitteePeriod = Optional.empty();
    }
  }

  private SyncCommitteePeriod createSyncCommitteePeriod(
      final SyncCommitteeUtil syncCommitteeUtil,
      final UInt64 periodStartEpoch,
      final int subscribeEpochsPriorToNextSyncPeriod) {
    return new SyncCommitteePeriod(
        periodStartEpoch,
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(periodStartEpoch),
        subscribeEpochsPriorToNextSyncPeriod);
  }

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {
    getDutiesForSlot(slot).ifPresent(duties -> duties.onProductionDue(slot));
  }

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {
    getDutiesForSlot(slot).ifPresent(duties -> duties.onAggregationDue(slot));
  }

  private Optional<PendingDuties> getDutiesForSlot(final UInt64 slot) {
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    return Stream.of(currentSyncCommitteePeriod, nextSyncCommitteePeriod)
        .flatMap(Optional::stream)
        .filter(period -> period.isCurrentPeriodForEpoch(epoch))
        .findAny()
        .flatMap(period -> period.duties);
  }

  @Override
  public void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {}

  @Override
  public void onChainReorg(final UInt64 newSlot, final UInt64 commonAncestorSlot) {}

  @Override
  public void onPossibleMissedEvents() {}

  @Override
  public void onBlockProductionDue(final UInt64 slot) {}

  private class SyncCommitteePeriod {
    private Optional<PendingDuties> duties = Optional.empty();
    private final UInt64 periodStartEpoch;
    private final UInt64 nextPeriodStartEpoch;
    private final UInt64 subscribeEpoch;

    private SyncCommitteePeriod(
        final UInt64 periodStartEpoch,
        final UInt64 nextPeriodStartEpoch,
        final int numberOfEpochsPriorToStartToSubscribe) {
      this.periodStartEpoch = periodStartEpoch;
      this.nextPeriodStartEpoch = nextPeriodStartEpoch;
      this.subscribeEpoch = periodStartEpoch.minusMinZero(numberOfEpochsPriorToStartToSubscribe);
    }

    public boolean isCurrentPeriodForEpoch(final UInt64 epoch) {
      return periodStartEpoch.isLessThanOrEqualTo(epoch) && epoch.isLessThan(nextPeriodStartEpoch);
    }

    public void calculateDuties() {
      duties =
          duties.or(
              () ->
                  Optional.of(
                      PendingDuties.calculateDuties(metricsSystem, dutyLoader, periodStartEpoch)));
    }
  }
}
