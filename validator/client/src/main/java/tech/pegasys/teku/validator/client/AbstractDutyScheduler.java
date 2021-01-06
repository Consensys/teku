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

package tech.pegasys.teku.validator.client;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public abstract class AbstractDutyScheduler implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final boolean useDependentRoots;
  private final DutyLoader epochDutiesScheduler;
  private final int lookAheadEpochs;

  protected final NavigableMap<UInt64, EpochDuties> dutiesByEpoch = new TreeMap<>();
  private Optional<UInt64> currentEpoch = Optional.empty();

  protected AbstractDutyScheduler(
      final DutyLoader epochDutiesScheduler,
      final int lookAheadEpochs,
      final boolean useDependentRoots) {
    this.epochDutiesScheduler = epochDutiesScheduler;
    this.lookAheadEpochs = lookAheadEpochs;
    this.useDependentRoots = useDependentRoots;
  }

  protected void notifyEpochDuties(
      final BiConsumer<EpochDuties, UInt64> action, final UInt64 slot) {
    final EpochDuties epochDuties = dutiesByEpoch.get(compute_epoch_at_slot(slot));
    if (epochDuties != null) {
      action.accept(epochDuties, slot);
    }
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final UInt64 currentEpoch = compute_epoch_at_slot(slot);
    this.currentEpoch = Optional.of(currentEpoch);
    removePriorEpochs(currentEpoch);
    calculateDuties(currentEpoch);
  }

  @Override
  public void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {
    if (!useDependentRoots) {
      return;
    }
    final UInt64 headEpoch = compute_epoch_at_slot(slot);
    dutiesByEpoch
        .tailMap(headEpoch, true)
        .forEach(
            (dutyEpoch, duties) ->
                duties.onHeadUpdate(
                    getExpectedDependentRoot(
                        headBlockRoot,
                        previousDutyDependentRoot,
                        currentDutyDependentRoot,
                        headEpoch,
                        dutyEpoch)));
  }

  protected abstract Bytes32 getExpectedDependentRoot(
      Bytes32 headBlockRoot,
      Bytes32 previousDutyDependentRoot,
      Bytes32 currentDutyDependentRoot,
      UInt64 headEpoch,
      UInt64 dutyEpoch);

  @Override
  public void onChainReorg(final UInt64 newSlot, final UInt64 commonAncestorSlot) {
    if (useDependentRoots) {
      return;
    }
    final UInt64 changedEpoch = compute_epoch_at_slot(commonAncestorSlot);
    // Because duties for an epoch can be calculated from the very start of that epoch, the epoch
    // containing the common ancestor is not affected by the reorg.
    // Similarly epochs within the look-ahead distance of the common ancestor must not be affected
    final UInt64 lastUnaffectedEpoch = changedEpoch.plus(lookAheadEpochs);
    LOG.debug(
        "Chain reorganisation detected. Invalidating validator duties after epoch {}",
        lastUnaffectedEpoch);
    invalidateEpochs(dutiesByEpoch.tailMap(lastUnaffectedEpoch, false));
    calculateDuties(compute_epoch_at_slot(newSlot));
  }

  @Override
  public void onPossibleMissedEvents() {
    // We may have missed a re-org or head notification so we need to recalculate all duties.
    invalidateEpochs(dutiesByEpoch);
  }

  private void calculateDuties(final UInt64 epochNumber) {
    dutiesByEpoch.computeIfAbsent(epochNumber, this::createEpochDuties);
    for (int i = 1; i <= lookAheadEpochs; i++) {
      dutiesByEpoch.computeIfAbsent(epochNumber.plus(i), this::createEpochDuties);
    }
  }

  private EpochDuties createEpochDuties(final UInt64 epochNumber) {
    return EpochDuties.calculateDuties(epochDutiesScheduler, epochNumber);
  }

  private void removePriorEpochs(final UInt64 epochNumber) {
    final NavigableMap<UInt64, EpochDuties> toRemove = dutiesByEpoch.headMap(epochNumber, false);
    toRemove.values().forEach(EpochDuties::cancel);
    toRemove.clear();
  }

  private void invalidateEpochs(final NavigableMap<UInt64, EpochDuties> toInvalidate) {
    toInvalidate.values().forEach(EpochDuties::recalculate);
  }

  protected Optional<UInt64> getCurrentEpoch() {
    return currentEpoch;
  }

  protected boolean isAbleToVerifyEpoch(final UInt64 slot) {
    if (currentEpoch.isEmpty()) {
      return false;
    }
    final UInt64 signingEpoch = compute_epoch_at_slot(slot);
    final UInt64 epoch = currentEpoch.get();
    return !signingEpoch.isGreaterThan(epoch.plus(lookAheadEpochs + 1));
  }

  @Override
  public void onBlockProductionDue(final UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {}
}
