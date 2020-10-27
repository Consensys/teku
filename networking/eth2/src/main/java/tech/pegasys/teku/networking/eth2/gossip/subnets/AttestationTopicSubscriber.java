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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class AttestationTopicSubscriber implements SlotEventsChannel {
  private final Map<Integer, UInt64> subnetIdToUnsubscribeSlot = new HashMap<>();
  private final Set<Integer> persistentSubnetIdSet = new HashSet<>();
  private final Eth2Network eth2Network;

  public AttestationTopicSubscriber(final Eth2Network eth2Network) {
    this.eth2Network = eth2Network;
  }

  public void subscribeToCommitteeForAggregation(
      final int committeeIndex, final UInt64 committeesAtSlot, final UInt64 aggregationSlot) {
    final int subnetId =
        CommitteeUtil.computeSubnetForCommittee(
            aggregationSlot, UInt64.valueOf(committeeIndex), committeesAtSlot);
    final UInt64 currentUnsubscriptionSlot = subnetIdToUnsubscribeSlot.getOrDefault(subnetId, ZERO);
    if (currentUnsubscriptionSlot.equals(ZERO)) {
      eth2Network.subscribeToAttestationSubnetId(subnetId);
    }
    subnetIdToUnsubscribeSlot.put(subnetId, currentUnsubscriptionSlot.max(aggregationSlot));
  }

  public synchronized void subscribeToPersistentSubnets(
      final Set<SubnetSubscription> newSubscriptions) {
    boolean shouldUpdateENR = false;

    for (SubnetSubscription subnetSubscription : newSubscriptions) {
      int subnetId = subnetSubscription.getSubnetId();
      shouldUpdateENR = persistentSubnetIdSet.add(subnetId) || shouldUpdateENR;

      UInt64 existingUnsubscriptionSlot =
          subnetIdToUnsubscribeSlot.computeIfAbsent(
              subnetId,
              (key) -> {
                eth2Network.subscribeToAttestationSubnetId(subnetId);
                return ZERO;
              });

      subnetIdToUnsubscribeSlot.put(
          subnetId, existingUnsubscriptionSlot.max(subnetSubscription.getUnsubscriptionSlot()));
    }

    if (shouldUpdateENR) {
      eth2Network.setLongTermAttestationSubnetSubscriptions(persistentSubnetIdSet);
    }
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    boolean shouldUpdateENR = false;

    final Iterator<Map.Entry<Integer, UInt64>> iterator =
        subnetIdToUnsubscribeSlot.entrySet().iterator();
    while (iterator.hasNext()) {
      final Map.Entry<Integer, UInt64> entry = iterator.next();
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
