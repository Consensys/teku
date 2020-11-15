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

import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public abstract class AbstractDutyScheduler implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final DutyLoader epochDutiesScheduler;
  private final int lookAheadEpochs;

  protected final NavigableMap<UInt64, DutyQueue> dutiesByEpoch = new TreeMap<>();
  private final Counter targetRootInvalidationCounter;
  private final Counter reorgInvalidationCounter;
  protected Optional<UInt64> currentEpoch = Optional.empty();

  protected AbstractDutyScheduler(
      final MetricsSystem metricsSystem,
      final String dutyType,
      final DutyLoader epochDutiesScheduler,
      final int lookAheadEpochs) {
    final LabelledMetric<Counter> invalidationCounters =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.VALIDATOR, "invalidated_duties_epochs", "type", "cause");
    targetRootInvalidationCounter = invalidationCounters.labels(dutyType, "targetRootChange");
    reorgInvalidationCounter = invalidationCounters.labels(dutyType, "reorg");
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
  public void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 headBlockRoot,
      final Bytes32 currentTargetRoot,
      final Bytes32 previousTargetRoot) {
    final UInt64 headEpoch = compute_epoch_at_slot(slot);

    final NavigableMap<UInt64, DutyQueue> potentiallyAffectedEpochs =
        dutiesByEpoch.tailMap(headEpoch, true);
    for (Iterator<Map.Entry<UInt64, DutyQueue>> iterator =
            potentiallyAffectedEpochs.entrySet().iterator();
        iterator.hasNext(); ) {
      final Map.Entry<UInt64, DutyQueue> entry = iterator.next();
      final UInt64 currentEpoch = entry.getKey();
      final DutyQueue dutyQueue = entry.getValue();
      final Bytes32 targetRoot =
          getExpectedTargetRoot(
              headBlockRoot, currentTargetRoot, previousTargetRoot, headEpoch, currentEpoch);
      final Optional<Bytes32> dutiesTargetRoot = dutyQueue.getTargetRoot();
      if (dutiesTargetRoot.isEmpty() || !dutiesTargetRoot.get().equals(targetRoot)) {
        // Invalidate epoch.
        // We invalidate if the duties haven't yet been calculated as the request has already been
        // sent, prior to getting the new head
        targetRootInvalidationCounter.inc();
        dutyQueue.cancel();
        iterator.remove();
      }
    }
    recalculateDuties(headEpoch);
  }

  protected abstract Bytes32 getExpectedTargetRoot(
      final Bytes32 headBlockRoot,
      final Bytes32 currentTargetRoot,
      final Bytes32 previousTargetRoot,
      final UInt64 headEpoch,
      final UInt64 currentEpoch);

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
    final NavigableMap<UInt64, DutyQueue> epochsToInvalidate =
        dutiesByEpoch.tailMap(lastUnaffectedEpoch, false);
    reorgInvalidationCounter.inc(epochsToInvalidate.size());
    //    removeEpochs(epochsToInvalidate);
    //    recalculateDuties(compute_epoch_at_slot(newSlot));
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

  @Override
  public void onBlockProductionDue(final UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {}
}
