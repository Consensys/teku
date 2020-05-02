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

package tech.pegasys.teku.networking.eth2.gossip;

import static com.google.common.primitives.UnsignedLong.ZERO;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.max;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.committeeIndexToSubnetId;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

import tech.pegasys.teku.datastructures.networking.discovery.SubnetSubscription;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class AttestationTopicSubscriber implements SlotEventsChannel {
  private final Map<Integer, UnsignedLong> unsubscriptionSlotBySubnetId = new HashMap<>();
  private final Eth2Network eth2Network;

  public AttestationTopicSubscriber(final Eth2Network eth2Network) {
    this.eth2Network = eth2Network;
  }

  public synchronized void subscribeToCommitteeForAggregation(
      final int committeeIndex, final UnsignedLong aggregationSlot) {
    final int subnetId = committeeIndexToSubnetId(committeeIndex);
    final UnsignedLong currentUnsubscriptionSlot =
        unsubscriptionSlotBySubnetId.getOrDefault(subnetId, ZERO);
    if (currentUnsubscriptionSlot.equals(ZERO)) {
      eth2Network.subscribeToAttestationSubnetId(subnetId);
    }
    unsubscriptionSlotBySubnetId.put(subnetId, max(currentUnsubscriptionSlot, aggregationSlot));
  }

  public synchronized void subscribeToPersistentSubnets(
      final Set<SubnetSubscription> newSubscriptions) {
    boolean updated = false;

    for (SubnetSubscription subnetSubscription : newSubscriptions) {
      int subnetId = subnetSubscription.getSubnetId();
      UnsignedLong existingUnsubscriptionSlot =
              Optional.ofNullable(unsubscriptionSlotBySubnetId.get(subnetId))
              .orElse(ZERO);

      if (existingUnsubscriptionSlot.equals(ZERO)) {
        updated = true;
      }

      unsubscriptionSlotBySubnetId.put(
              subnetId,
              max(existingUnsubscriptionSlot, subnetSubscription.getUnsubscriptionSlot())
      );
    }

    if (updated) {
      eth2Network.setLongTermAttestationSubnetSubscriptions(unsubscriptionSlotBySubnetId.keySet());
    }
  }

  @Override
  public synchronized void onSlot(final UnsignedLong slot) {
    final Iterator<Entry<Integer, UnsignedLong>> iterator =
        unsubscriptionSlotBySubnetId.entrySet().iterator();
    while (iterator.hasNext()) {
      final Entry<Integer, UnsignedLong> entry = iterator.next();
      if (entry.getValue().compareTo(slot) < 0) {
        iterator.remove();
        int subnetId = entry.getKey();
        eth2Network.unsubscribeFromAttestationSubnetId(subnetId);
      }
    }
  }
}
