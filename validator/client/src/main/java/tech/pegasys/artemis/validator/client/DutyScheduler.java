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

package tech.pegasys.artemis.validator.client;

import static com.google.common.primitives.UnsignedLong.ONE;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.primitives.UnsignedLong;
import java.util.NavigableMap;
import java.util.TreeMap;
import tech.pegasys.artemis.validator.api.ValidatorTimingChannel;

public class DutyScheduler implements ValidatorTimingChannel {
  private final EpochDutiesScheduler epochDutiesScheduler;
  private NavigableMap<UnsignedLong, DutyQueue> dutiesByEpoch = new TreeMap<>();

  public DutyScheduler(final EpochDutiesScheduler epochDutiesScheduler) {
    this.epochDutiesScheduler = epochDutiesScheduler;
  }

  @Override
  public void onSlot(final UnsignedLong slot) {
    final UnsignedLong epochNumber = compute_epoch_at_slot(slot);
    removePriorEpochs(epochNumber);
    dutiesByEpoch.computeIfAbsent(epochNumber, this::requestDutiesForEpoch);
    dutiesByEpoch.computeIfAbsent(epochNumber.plus(ONE), this::requestDutiesForEpoch);
  }

  @Override
  public void onChainReorg(final UnsignedLong newSlot) {
    dutiesByEpoch.clear();
    final UnsignedLong epochNumber = compute_epoch_at_slot(newSlot);
    final UnsignedLong nextEpochNumber = epochNumber.plus(ONE);
    dutiesByEpoch.put(epochNumber, requestDutiesForEpoch(epochNumber));
    dutiesByEpoch.put(nextEpochNumber, requestDutiesForEpoch(nextEpochNumber));
  }

  @Override
  public void onBlockProductionDue(final UnsignedLong slot) {
    getEpochDutiesForSlot(slot).onBlockProductionDue(slot);
  }

  @Override
  public void onAttestationCreationDue(final UnsignedLong slot) {
    getEpochDutiesForSlot(slot).onAttestationCreationDue(slot);
  }

  @Override
  public void onAttestationAggregationDue(final UnsignedLong slot) {
    getEpochDutiesForSlot(slot).onAttestationAggregationDue(slot);
  }

  private DutyQueue requestDutiesForEpoch(final UnsignedLong epochNumber) {
    return new DutyQueue(epochDutiesScheduler.fetchDutiesForEpoch(epochNumber));
  }

  private DutyQueue getEpochDutiesForSlot(final UnsignedLong slot) {
    return dutiesByEpoch.getOrDefault(compute_epoch_at_slot(slot), DutyQueue.NONE);
  }

  private void removePriorEpochs(final UnsignedLong epochNumber) {
    dutiesByEpoch.headMap(epochNumber.minus(ONE)).clear();
  }
}
