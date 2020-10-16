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

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.subnets.StableSubnetSubscriber;

class ActiveValidatorTrackerTest {
  private final StableSubnetSubscriber stableSubnetSubscriber = mock(StableSubnetSubscriber.class);

  private final ActiveValidatorTracker tracker = new ActiveValidatorTracker(stableSubnetSubscriber);

  @Test
  void shouldUpdateValidatorCountAtStartOfEpoch() {
    final UInt64 slot = UInt64.valueOf(500);
    final UInt64 epoch = compute_epoch_at_slot(slot);
    tracker.onCommitteeSubscriptionRequest(1, slot);
    tracker.onCommitteeSubscriptionRequest(2, slot);
    tracker.onCommitteeSubscriptionRequest(3, slot);

    final UInt64 epochStartSlot = compute_start_slot_at_epoch(epoch);
    tracker.onSlot(epochStartSlot);

    final InOrder inOrder = inOrder(stableSubnetSubscriber);
    inOrder.verify(stableSubnetSubscriber).updateValidatorCount(3);
    inOrder.verify(stableSubnetSubscriber).onSlot(epochStartSlot);
  }

  @Test
  void shouldNotCountDuplicateValidators() {
    final UInt64 slot = UInt64.valueOf(500);
    final UInt64 epoch = compute_epoch_at_slot(slot);
    tracker.onCommitteeSubscriptionRequest(1, slot);
    tracker.onCommitteeSubscriptionRequest(1, slot);
    tracker.onCommitteeSubscriptionRequest(1, slot);

    final UInt64 epochStartSlot = compute_start_slot_at_epoch(epoch);
    tracker.onSlot(epochStartSlot);

    final InOrder inOrder = inOrder(stableSubnetSubscriber);
    inOrder.verify(stableSubnetSubscriber).updateValidatorCount(1);
    inOrder.verify(stableSubnetSubscriber).onSlot(epochStartSlot);
  }

  @Test
  void shouldPruneValidatorCountsAtTheEndOfTheEpoch() {
    final UInt64 slot = UInt64.valueOf(500);
    final UInt64 epoch = compute_epoch_at_slot(slot);
    tracker.onCommitteeSubscriptionRequest(1, slot);
    tracker.onCommitteeSubscriptionRequest(2, slot);
    tracker.onCommitteeSubscriptionRequest(3, slot);

    final UInt64 epochStartSlot = compute_start_slot_at_epoch(epoch);
    final UInt64 nextEpochStartSlot = compute_start_slot_at_epoch(epoch.plus(1));

    // For the purpose of testing, we get the slots out of order, so all the requests get dropped
    tracker.onSlot(nextEpochStartSlot);
    tracker.onSlot(epochStartSlot);

    // And both slot updates wind up setting 0 validators
    verify(stableSubnetSubscriber, times(2)).updateValidatorCount(0);
  }

  @Test
  void shouldNotPruneBeforeTheEndOfTheEpoch() {
    final UInt64 slot = UInt64.valueOf(500);
    final UInt64 epoch = compute_epoch_at_slot(slot);
    tracker.onCommitteeSubscriptionRequest(1, slot);
    tracker.onCommitteeSubscriptionRequest(2, slot);
    tracker.onCommitteeSubscriptionRequest(3, slot);

    final UInt64 epochStartSlot = compute_start_slot_at_epoch(epoch);

    // We then get notified of two slots
    tracker.onSlot(epochStartSlot);
    tracker.onSlot(epochStartSlot.plus(1));

    // And both slot updates wind up setting 3 validators
    verify(stableSubnetSubscriber, times(2)).updateValidatorCount(3);
  }
}
