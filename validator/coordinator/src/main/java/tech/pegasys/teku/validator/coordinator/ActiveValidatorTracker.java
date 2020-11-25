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

package tech.pegasys.teku.validator.coordinator;

import static java.util.Collections.emptySet;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker.ATTESTATION_INCLUSION_RANGE;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.subnets.StableSubnetSubscriber;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class ActiveValidatorTracker implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final NavigableMap<UInt64, Set<Integer>> validatorsPerEpoch =
      new ConcurrentSkipListMap<>();

  private final StableSubnetSubscriber stableSubnetSubscriber;

  public ActiveValidatorTracker(final StableSubnetSubscriber stableSubnetSubscriber) {
    this.stableSubnetSubscriber = stableSubnetSubscriber;
  }

  public void onCommitteeSubscriptionRequest(final int validatorIndex, final UInt64 slot) {
    final UInt64 epoch = compute_epoch_at_slot(slot);
    validatorsPerEpoch
        .computeIfAbsent(epoch, __ -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
        .add(validatorIndex);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final UInt64 epoch = compute_epoch_at_slot(slot);
    final int validatorCount = getNumberOfValidatorsForEpoch(epoch);
    LOG.debug("{} active validators counted for epoch {}", validatorCount, epoch);
    stableSubnetSubscriber.onSlot(slot, validatorCount);

    // PerformanceTracker uses validator counts to determine expected attestation count.
    // Thus we wait ATTESTATION_INCLUSION_RANGE epochs, after which the performance is determined,
    // before clearing those from memory.
    if (epoch.isLessThanOrEqualTo(ATTESTATION_INCLUSION_RANGE)) {
      return;
    }
    validatorsPerEpoch.headMap(epoch.minus(ATTESTATION_INCLUSION_RANGE), false).clear();
  }

  public int getNumberOfValidatorsForEpoch(final UInt64 epoch) {
    return validatorsPerEpoch.getOrDefault(epoch, emptySet()).size();
  }
}
