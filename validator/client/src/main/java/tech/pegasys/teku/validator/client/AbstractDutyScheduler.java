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
import java.util.TreeMap;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public abstract class AbstractDutyScheduler implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final DutyLoader epochDutiesScheduler;

  protected final NavigableMap<UInt64, DutyQueue> dutiesByEpoch = new TreeMap<>();

  protected AbstractDutyScheduler(final DutyLoader epochDutiesScheduler) {
    this.epochDutiesScheduler = epochDutiesScheduler;
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
    final UInt64 epochNumber = compute_epoch_at_slot(slot);
    removePriorEpochs(epochNumber);
    recalculateDuties(epochNumber);
  }

  @Override
  public void onChainReorg(final UInt64 newSlot) {
    LOG.debug("Chain reorganisation detected. Recalculating validator duties");
    dutiesByEpoch.clear();
    recalculateDuties(compute_epoch_at_slot(newSlot));
  }

  protected void recalculateDuties(final UInt64 epochNumber) {
    dutiesByEpoch.computeIfAbsent(epochNumber, this::requestDutiesForEpoch);
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
  public void onBlockImportedForSlot(final UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {}
}
