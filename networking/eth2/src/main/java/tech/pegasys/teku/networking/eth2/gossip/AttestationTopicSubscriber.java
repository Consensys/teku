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

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class AttestationTopicSubscriber implements SlotEventsChannel {
  private final Map<Integer, UnsignedLong> subnetIdToUnsubscribeSlot = new HashMap<>();
  private final Set<Integer> persistentSubnetIdSet = new HashSet<>();
  private final Eth2Network eth2Network;
  private final RecentChainData recentChainData;

  public AttestationTopicSubscriber(
      final Eth2Network eth2Network, final RecentChainData recentChainData) {
    this.eth2Network = eth2Network;
    this.recentChainData = recentChainData;
  }

  public synchronized void subscribeToCommitteeForAggregation(
      final int committeeIndex, final UnsignedLong aggregationSlot) {
    recentChainData
        .getBestState()
        // No point aggregating for historic slots and we can't calculate the subnet ID
        .filter(state -> state.getSlot().compareTo(aggregationSlot) <= 0)
        .ifPresent(
            state -> subscribeToCommitteeForAggregation(state, committeeIndex, aggregationSlot));
  }

  private void subscribeToCommitteeForAggregation(
      final BeaconState state, final int committeeIndex, final UnsignedLong aggregationSlot) {
    final int subnetId =
        CommitteeUtil.computeSubnetForCommittee(
            state, aggregationSlot, UnsignedLong.valueOf(committeeIndex));
    final UnsignedLong currentUnsubscriptionSlot =
        subnetIdToUnsubscribeSlot.getOrDefault(subnetId, ZERO);
    if (currentUnsubscriptionSlot.equals(ZERO)) {
      eth2Network.subscribeToAttestationSubnetId(subnetId);
    }
    subnetIdToUnsubscribeSlot.put(subnetId, max(currentUnsubscriptionSlot, aggregationSlot));
  }

  public synchronized void subscribeToPersistentSubnets(
      final Set<SubnetSubscription> newSubscriptions) {
    boolean shouldUpdateENR = false;

    for (SubnetSubscription subnetSubscription : newSubscriptions) {
      int subnetId = subnetSubscription.getSubnetId();
      shouldUpdateENR = persistentSubnetIdSet.add(subnetId) || shouldUpdateENR;

      UnsignedLong existingUnsubscriptionSlot =
          subnetIdToUnsubscribeSlot.computeIfAbsent(
              subnetId,
              (key) -> {
                eth2Network.subscribeToAttestationSubnetId(subnetId);
                return ZERO;
              });

      subnetIdToUnsubscribeSlot.put(
          subnetId, max(existingUnsubscriptionSlot, subnetSubscription.getUnsubscriptionSlot()));
    }

    if (shouldUpdateENR) {
      eth2Network.setLongTermAttestationSubnetSubscriptions(persistentSubnetIdSet);
    }
  }

  @Override
  public synchronized void onSlot(final UnsignedLong slot) {
    boolean shouldUpdateENR = false;

    final Iterator<Entry<Integer, UnsignedLong>> iterator =
        subnetIdToUnsubscribeSlot.entrySet().iterator();
    while (iterator.hasNext()) {
      final Entry<Integer, UnsignedLong> entry = iterator.next();
      if (entry.getValue().compareTo(slot) < 0) {
        iterator.remove();
        int subnetId = entry.getKey();
        eth2Network.unsubscribeFromAttestationSubnetId(subnetId);

        if (persistentSubnetIdSet.contains(subnetId)) {
          persistentSubnetIdSet.remove(subnetId);
          shouldUpdateENR = true;
        }
      }
    }

    if (shouldUpdateENR) {
      eth2Network.setLongTermAttestationSubnetSubscriptions(persistentSubnetIdSet);
    }
  }
}
