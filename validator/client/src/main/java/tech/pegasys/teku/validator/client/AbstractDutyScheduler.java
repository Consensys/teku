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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public abstract class AbstractDutyScheduler implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final DutyLoader epochDutiesScheduler;
  private final int lookAheadEpochs;

  protected final NavigableMap<UInt64, DutyQueue> dutiesByEpoch = new TreeMap<>();
  private Optional<UInt64> currentEpoch = Optional.empty();

  protected AbstractDutyScheduler(
      final DutyLoader epochDutiesScheduler, final int lookAheadEpochs) {
    this.epochDutiesScheduler = epochDutiesScheduler;
    this.lookAheadEpochs = lookAheadEpochs;
  }

  protected DutyQueue requestDutiesForEpoch(final UInt64 epochNumber) {
    return new DutyQueue(epochDutiesScheduler.loadDutiesForEpoch(epochNumber));
  }

  protected void notifyDutyQueue(final BiConsumer<DutyQueue, UInt64> action, final UInt64 slot) {
    final DutyQueue dutyQueue = dutiesByEpoch.get(compute_epoch_at_slot(slot));
    if (dutyQueue != null) {
      action.accept(dutyQueue, slot);
    }
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final UInt64 currentEpoch = compute_epoch_at_slot(slot);
    this.currentEpoch = Optional.of(currentEpoch);
    removePriorEpochs(currentEpoch);
    recalculateDuties(currentEpoch);
  }

  @Override
  public void onChainReorg(final UInt64 newSlot, final UInt64 commonAncestorSlot) {
    final UInt64 changedEpoch = compute_epoch_at_slot(commonAncestorSlot);
    // Because duties for an epoch can be calculated from the very start of that epoch, the epoch
    // containing the common ancestor is not affected by the reorg.
    // Similarly epochs within the look-ahead distance of the common ancestor must not be affected
    final UInt64 lastUnaffectedEpoch = changedEpoch.plus(lookAheadEpochs);
    LOG.debug(
        "Chain reorganisation detected. Invalidating validator duties after epoch {}",
        lastUnaffectedEpoch);
    removeEpochs(dutiesByEpoch.tailMap(lastUnaffectedEpoch, false));
    recalculateDuties(compute_epoch_at_slot(newSlot));
  }

  @Override
  public void onPossibleMissedEvents() {
    // We may have missed a re-org notification so we need to recalculate all duties.
    removeEpochs(dutiesByEpoch);
    currentEpoch.ifPresent(this::recalculateDuties);
  }

  protected void recalculateDuties(final UInt64 epochNumber) {
    dutiesByEpoch.computeIfAbsent(epochNumber, this::requestDutiesForEpoch);
    for (int i = 1; i <= lookAheadEpochs; i++) {
      dutiesByEpoch.computeIfAbsent(epochNumber.plus(i), this::requestDutiesForEpoch);
    }
  }

  protected void removePriorEpochs(final UInt64 epochNumber) {
    final NavigableMap<UInt64, DutyQueue> toRemove = dutiesByEpoch.headMap(epochNumber, false);
    removeEpochs(toRemove);
  }

  protected void removeEpochs(final NavigableMap<UInt64, DutyQueue> toRemove) {
    toRemove.values().forEach(DutyQueue::cancel);
    toRemove.clear();
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
    if (signingEpoch.isGreaterThan(epoch.plus(lookAheadEpochs + 1))) {
      return false;
    }
    return true;
  }

  @Override
  public void onBlockProductionDue(final UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {}
}
