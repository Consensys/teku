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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.all;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.max;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.committeeIndexToSubnetId;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import tech.pegasys.teku.datastructures.networking.discovery.SubnetSubscription;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class AttestationTopicSubscriber implements SlotEventsChannel {
  private final Map<Integer, UnsignedLong> shortTermSubscriptions = new HashMap<>();
  private final Map<Integer, UnsignedLong> persistentSubscriptions = new HashMap<>();
  private final Eth2Network eth2Network;

  public AttestationTopicSubscriber(final Eth2Network eth2Network) {
    this.eth2Network = eth2Network;
  }

  public synchronized void subscribeToCommitteeForAggregation(
      final int committeeIndex, final UnsignedLong aggregationSlot) {
    final int subnetId = committeeIndexToSubnetId(committeeIndex);
    final UnsignedLong currentUnsubscriptionSlot =
        shortTermSubscriptions.getOrDefault(subnetId, ZERO);
    if (currentUnsubscriptionSlot.equals(ZERO)) {
      eth2Network.subscribeToAttestationSubnetId(subnetId);
    }
    shortTermSubscriptions.put(subnetId, max(currentUnsubscriptionSlot, aggregationSlot));
  }

  public synchronized void subscribeToPersistentSubnets(
      final Set<SubnetSubscription> newSubscriptions) {
    boolean shouldUpdateENR = false;

    for (SubnetSubscription subnetSubscription : newSubscriptions) {
      int subnetId = subnetSubscription.getSubnetId();
      UnsignedLong existingUnsubscriptionSlot =
              Optional.ofNullable(persistentSubscriptions.get(subnetId))
              .orElse(ZERO);

      if (existingUnsubscriptionSlot.equals(ZERO)) {
        shouldUpdateENR = true;
        if (!shortTermSubscriptions.containsKey(subnetId)) {
          eth2Network.subscribeToAttestationSubnetId(subnetId);
        }
      }

      persistentSubscriptions.put(
              subnetId,
              max(existingUnsubscriptionSlot, subnetSubscription.getUnsubscriptionSlot())
      );
    }

    if (shouldUpdateENR) {
      eth2Network.setLongTermAttestationSubnetSubscriptions(persistentSubscriptions.keySet());
    }
  }

  @Override
  public synchronized void onSlot(final UnsignedLong slot) {
    boolean shouldUpdateENR = false;
    final Set<Integer> allSubnetIds = new HashSet<>();
    allSubnetIds.addAll(shortTermSubscriptions.keySet());
    allSubnetIds.addAll(persistentSubscriptions.keySet());

    for (int subnetId : allSubnetIds) {

      Optional<UnsignedLong> shortTermUnsubscriptionSlot =
              Optional.ofNullable(shortTermSubscriptions.get(subnetId));
      Optional<UnsignedLong> persistentUnsubscriptionSlot =
              Optional.ofNullable(persistentSubscriptions.get(subnetId));

      if (shortTermUnsubscriptionSlot.isPresent() && persistentUnsubscriptionSlot.isPresent()) {
        if (slot.compareTo(persistentUnsubscriptionSlot.get()) > 0) {
          shouldUpdateENR = true;
          persistentSubscriptions.remove(subnetId);
        }

        if (slot.compareTo(shortTermUnsubscriptionSlot.get()) > 0) {
          shortTermSubscriptions.remove(subnetId);
        }

        if (slot.compareTo(persistentUnsubscriptionSlot.get()) > 0 &&
                slot.compareTo(shortTermUnsubscriptionSlot.get()) > 0) {
          eth2Network.unsubscribeFromAttestationSubnetId(subnetId);
        }

      } else if (shortTermUnsubscriptionSlot.isPresent()) {

        if (slot.compareTo(shortTermUnsubscriptionSlot.get()) > 0) {
          shortTermSubscriptions.remove(subnetId);
         eth2Network.unsubscribeFromAttestationSubnetId(subnetId);
        }
        
      } else if (persistentUnsubscriptionSlot.isPresent()) {

        if (slot.compareTo(persistentUnsubscriptionSlot.get()) > 0) {
          eth2Network.unsubscribeFromAttestationSubnetId(subnetId);
          persistentSubscriptions.remove(subnetId);
          shouldUpdateENR = true;
        }

      }
    }

    if (shouldUpdateENR) {
      eth2Network.setLongTermAttestationSubnetSubscriptions(persistentSubscriptions.keySet());
    }
  }
}
