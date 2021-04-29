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
          new SyncCommitteePeriod(
              syncCommitteeUtil.computeFirstEpochOfCurrentSyncCommitteePeriod(currentEpoch), 0);
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
              new SyncCommitteePeriod(
                  firstEpochOfNextSyncCommitteePeriod, subscribeEpochsPriorToNextSyncPeriod));
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

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {
    // TODO: Probably should check we are still in the right sync committee period
    currentSyncCommitteePeriod
        .flatMap(period -> period.duties)
        .ifPresent(duties -> duties.onProductionDue(slot));
  }

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {
    // TODO: Probably should check we are still in the right sync committee period
    currentSyncCommitteePeriod
        .flatMap(period -> period.duties)
        .ifPresent(duties -> duties.onAggregationDue(slot));
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
    private final UInt64 subscribeEpoch;

    private SyncCommitteePeriod(
        final UInt64 periodStartEpoch, final int numberOfEpochsPriorToStartToSubscribe) {
      this.periodStartEpoch = periodStartEpoch;
      this.subscribeEpoch = periodStartEpoch.minusMinZero(numberOfEpochsPriorToStartToSubscribe);
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
