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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.metrics.TekuMetricCategory;

public class BlockDutyScheduler extends AbstractDutyScheduler {
  private static final Logger LOG = LogManager.getLogger();

  public BlockDutyScheduler(
      final MetricsSystem metricsSystem, final DutyLoader epochDutiesScheduler) {
    super(epochDutiesScheduler);

    metricsSystem.createIntegerGauge(
        TekuMetricCategory.VALIDATOR,
        "scheduled_block_duties_current",
        "Current number of pending block duties that have been scheduled",
        () -> dutiesByEpoch.values().stream().mapToInt(DutyQueue::countDuties).sum());
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final UInt64 epochNumber = compute_epoch_at_slot(slot);
    removePriorEpochs(epochNumber);
    dutiesByEpoch.computeIfAbsent(epochNumber, this::requestDutiesForEpoch);
  }

  @Override
  public void onChainReorg(final UInt64 newSlot) {
    LOG.debug("Chain reorganisation detected. Recalculating validator block duties");
    dutiesByEpoch.clear();
    final UInt64 epochNumber = compute_epoch_at_slot(newSlot);
    dutiesByEpoch.put(epochNumber, requestDutiesForEpoch(epochNumber));
  }

  @Override
  public void onBlockProductionDue(final UInt64 slot) {
    notifyDutyQueue(DutyQueue::onBlockProductionDue, slot);
  }

  @Override
  public void onBlockImportedForSlot(final UInt64 slot) {
    // From an epoch x we can calculate duties for epoch's x and x+1 and importing more blocks from
    // epoch x won't change those duties.
    // However, importing a block from epoch x-1 will change the duties for x+1 (2 epochs after the
    // block is imported) because it adds another randao reveal.
    // So invalidate any duties for slot.
    // They will be recalculated on the next slot event if required which avoids requesting duties
    // too often if we're syncing a batch of blocks
    final UInt64 firstInvalidatedEpoch = compute_epoch_at_slot(slot).plus(2);
    removeEpochs(dutiesByEpoch.tailMap(firstInvalidatedEpoch, true));
  }
}
