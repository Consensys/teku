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

import static com.google.common.primitives.UnsignedLong.ONE;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.primitives.UnsignedLong;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.metrics.TekuMetricCategory;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class DutyScheduler implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final DutyLoader epochDutiesScheduler;
  private final StableSubnetSubscriber stableSubnetSubscriber;
  private final NavigableMap<UnsignedLong, DutyQueue> dutiesByEpoch = new TreeMap<>();

  public DutyScheduler(
      final MetricsSystem metricsSystem,
      final DutyLoader epochDutiesScheduler,
      final StableSubnetSubscriber stableSubnetSubscriber) {
    this.epochDutiesScheduler = epochDutiesScheduler;
    this.stableSubnetSubscriber = stableSubnetSubscriber;

    metricsSystem.createIntegerGauge(
        TekuMetricCategory.VALIDATOR,
        "scheduled_duties_current",
        "Current number of pending duties that have been scheduled",
        () -> dutiesByEpoch.values().stream().mapToInt(DutyQueue::countDuties).sum());
  }

  @Override
  public void onSlot(final UnsignedLong slot) {
    final UnsignedLong epochNumber = compute_epoch_at_slot(slot);
    removePriorEpochs(epochNumber);
    dutiesByEpoch.computeIfAbsent(epochNumber, this::requestDutiesForEpoch);
    dutiesByEpoch.computeIfAbsent(epochNumber.plus(ONE), this::requestDutiesForEpoch);
    stableSubnetSubscriber.onSlot(slot);
  }

  @Override
  public void onChainReorg(final UnsignedLong newSlot) {
    LOG.debug("Chain reorganisation detected. Recalculating validator duties");
    dutiesByEpoch.clear();
    final UnsignedLong epochNumber = compute_epoch_at_slot(newSlot);
    final UnsignedLong nextEpochNumber = epochNumber.plus(ONE);
    dutiesByEpoch.put(epochNumber, requestDutiesForEpoch(epochNumber));
    dutiesByEpoch.put(nextEpochNumber, requestDutiesForEpoch(nextEpochNumber));
  }

  @Override
  public void onBlockProductionDue(final UnsignedLong slot) {
    notifyDutyQueue(DutyQueue::onBlockProductionDue, slot);
  }

  @Override
  public void onAttestationCreationDue(final UnsignedLong slot) {
    notifyDutyQueue(DutyQueue::onAttestationCreationDue, slot);
  }

  @Override
  public void onAttestationAggregationDue(final UnsignedLong slot) {
    notifyDutyQueue(DutyQueue::onAttestationAggregationDue, slot);
  }

  @Override
  public void onBlockImportedForSlot(final UnsignedLong slot) {
    // From an epoch x we can calculate duties for epoch's x and x+1 and importing more blocks from
    // epoch x won't change those duties.
    // However, importing a block from epoch x-1 will change the duties for x+1 (2 epochs after the
    // block is imported) because it adds another randao reveal.
    // So invalidate any duties for slot.
    // They will be recalculated on the next slot event if required which avoids requesting duties
    // too often if we're syncing a batch of blocks
    final UnsignedLong firstInvalidatedEpoch =
        compute_epoch_at_slot(slot).plus(UnsignedLong.valueOf(2));
    removeEpochs(dutiesByEpoch.tailMap(firstInvalidatedEpoch, true));
  }

  private DutyQueue requestDutiesForEpoch(final UnsignedLong epochNumber) {
    return new DutyQueue(epochDutiesScheduler.loadDutiesForEpoch(epochNumber));
  }

  private void notifyDutyQueue(
      final BiConsumer<DutyQueue, UnsignedLong> action, final UnsignedLong slot) {
    final DutyQueue dutyQueue = dutiesByEpoch.get(compute_epoch_at_slot(slot));
    if (dutyQueue != null) {
      action.accept(dutyQueue, slot);
    }
  }

  private void removePriorEpochs(final UnsignedLong epochNumber) {
    final SortedMap<UnsignedLong, DutyQueue> toRemove = dutiesByEpoch.headMap(epochNumber);
    removeEpochs(toRemove);
  }

  private void removeEpochs(final SortedMap<UnsignedLong, DutyQueue> toRemove) {
    toRemove.values().forEach(DutyQueue::cancel);
    toRemove.clear();
  }
}
