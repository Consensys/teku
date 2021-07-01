/*
 * Copyright 2021 ConsenSys AG.
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;

public class SyncCommitteeSubscriptionManager implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Integer, UInt64> subcommitteeToUnsubscribeSlot = new HashMap<>();
  final Eth2P2PNetwork p2PNetwork;

  public SyncCommitteeSubscriptionManager(final Eth2P2PNetwork p2PNetwork) {
    this.p2PNetwork = p2PNetwork;
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    final Iterator<Map.Entry<Integer, UInt64>> iterator =
        subcommitteeToUnsubscribeSlot.entrySet().iterator();
    while (iterator.hasNext()) {
      final Map.Entry<Integer, UInt64> entry = iterator.next();
      if (entry.getValue().isLessThanOrEqualTo(slot)) {
        iterator.remove();
        LOG.trace("Unsubscribing at slot {}, committee subnet {}", slot, entry.getKey());
        p2PNetwork.unsubscribeFromSyncCommitteeSubnetId(entry.getKey());
      }
    }
  }

  public synchronized void subscribe(final Integer committeeSubnet, final UInt64 unsubscribeSlot) {
    final UInt64 currentUnsubscribeSlot = subcommitteeToUnsubscribeSlot.get(committeeSubnet);
    if (currentUnsubscribeSlot == null) {
      LOG.trace(
          "Subscribe to committee subnet {}, unsubscribe due at slot {}",
          committeeSubnet,
          unsubscribeSlot.toString());
      p2PNetwork.subscribeToSyncCommitteeSubnetId(committeeSubnet);
      subcommitteeToUnsubscribeSlot.put(committeeSubnet, unsubscribeSlot);
    } else if (currentUnsubscribeSlot.isLessThan(unsubscribeSlot)) {
      LOG.trace(
          "Update subscription of committee subnet {}, unsubscribe due at slot {}",
          committeeSubnet,
          unsubscribeSlot.toString());
      subcommitteeToUnsubscribeSlot.put(committeeSubnet, unsubscribeSlot);
    }
  }
}
