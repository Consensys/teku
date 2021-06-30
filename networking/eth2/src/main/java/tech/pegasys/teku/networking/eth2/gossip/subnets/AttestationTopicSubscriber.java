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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;

public class AttestationTopicSubscriber implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Integer, UInt64> subnetIdToUnsubscribeSlot = new HashMap<>();
  private final Set<Integer> persistentSubnetIdSet = new HashSet<>();
  private final Eth2P2PNetwork eth2P2PNetwork;
  private final Spec spec;

  public AttestationTopicSubscriber(final Spec spec, final Eth2P2PNetwork eth2P2PNetwork) {
    this.spec = spec;
    this.eth2P2PNetwork = eth2P2PNetwork;
  }

  public synchronized void subscribeToCommitteeForAggregation(
      final int committeeIndex, final UInt64 committeesAtSlot, final UInt64 aggregationSlot) {
    final int subnetId =
        spec.computeSubnetForCommittee(
            aggregationSlot, UInt64.valueOf(committeeIndex), committeesAtSlot);
    final UInt64 currentUnsubscriptionSlot = subnetIdToUnsubscribeSlot.getOrDefault(subnetId, ZERO);
    if (currentUnsubscriptionSlot.equals(ZERO)) {
      eth2P2PNetwork.subscribeToAttestationSubnetId(subnetId);
    }
    final UInt64 unsubscribeSlot = currentUnsubscriptionSlot.max(aggregationSlot);
    LOG.trace(
        "Subscribing to subnet {} with unsubscribe due at slot {}", subnetId, unsubscribeSlot);
    subnetIdToUnsubscribeSlot.put(subnetId, unsubscribeSlot);
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
                eth2P2PNetwork.subscribeToAttestationSubnetId(subnetId);
                return ZERO;
              });

      subnetIdToUnsubscribeSlot.put(
          subnetId, existingUnsubscriptionSlot.max(subnetSubscription.getUnsubscriptionSlot()));
    }

    if (shouldUpdateENR) {
      eth2P2PNetwork.setLongTermAttestationSubnetSubscriptions(persistentSubnetIdSet);
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
        LOG.trace("Unsubscribing from subnet {}", subnetId);
        eth2P2PNetwork.unsubscribeFromAttestationSubnetId(subnetId);

        if (persistentSubnetIdSet.contains(subnetId)) {
          persistentSubnetIdSet.remove(subnetId);
          shouldUpdateENR = true;
        }
      }
    }

    if (shouldUpdateENR) {
      eth2P2PNetwork.setLongTermAttestationSubnetSubscriptions(persistentSubnetIdSet);
    }
  }
}
