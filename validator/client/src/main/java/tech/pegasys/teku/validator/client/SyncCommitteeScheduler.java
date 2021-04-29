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
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.duties.SyncCommitteeProductionDuties;

public class SyncCommitteeScheduler implements ValidatorTimingChannel {

  private final MetricsSystem metricsSystem;
  private final Spec spec;
  private final DutyLoader<SyncCommitteeProductionDuties> dutyLoader;

  private Optional<PendingDuties> currentSyncCommitteePeriod = Optional.empty();
  private Optional<PendingDuties> nextSyncCommitteePeriod = Optional.empty();

  public SyncCommitteeScheduler(
      final MetricsSystem metricsSystem,
      final Spec spec,
      final DutyLoader<SyncCommitteeProductionDuties> dutyLoader) {
    this.metricsSystem = metricsSystem;
    this.spec = spec;
    this.dutyLoader = dutyLoader;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final Optional<SyncCommitteeUtil> maybeUtils = spec.getSyncCommitteeUtil(slot);
    if (maybeUtils.isEmpty()) {
      return;
    }
    final SyncCommitteeUtil syncCommitteeUtil = maybeUtils.get();
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    if (currentSyncCommitteePeriod.isEmpty()) {
      currentSyncCommitteePeriod =
          Optional.of(PendingDuties.calculateDuties(metricsSystem, dutyLoader, currentEpoch));
    }

    if (nextSyncCommitteePeriod.isEmpty()) {
      final UInt64 firstEpochOfNextSyncCommitteePeriod =
          syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(currentEpoch);
      nextSyncCommitteePeriod =
          Optional.of(
              PendingDuties.calculateDuties(
                  metricsSystem, dutyLoader, firstEpochOfNextSyncCommitteePeriod));
    }

    // TODO: Check if we need to subscribe to the next committee period.
    // Delay calculating the next committee period duties until we've reached the randomly selected
    // epoch to subscribe
  }

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {
    currentSyncCommitteePeriod.ifPresent(duties -> duties.onProductionDue(slot));
  }

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {
    currentSyncCommitteePeriod.ifPresent(duties -> duties.onAggregationDue(slot));
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
}
