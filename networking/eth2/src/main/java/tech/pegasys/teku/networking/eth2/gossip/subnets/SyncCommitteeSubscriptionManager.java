/*
 * Copyright ConsenSys Software Inc., 2022
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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;

public class SyncCommitteeSubscriptionManager implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final Int2ObjectMap<UInt64> subcommitteeToUnsubscribeSlot = new Int2ObjectOpenHashMap<>();
  final Eth2P2PNetwork p2PNetwork;

  public SyncCommitteeSubscriptionManager(final Eth2P2PNetwork p2PNetwork) {
    this.p2PNetwork = p2PNetwork;
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    final ObjectIterator<Int2ObjectMap.Entry<UInt64>> iterator =
        subcommitteeToUnsubscribeSlot.int2ObjectEntrySet().iterator();
    while (iterator.hasNext()) {
      final Int2ObjectMap.Entry<UInt64> entry = iterator.next();
      if (entry.getValue().isLessThanOrEqualTo(slot)) {
        LOG.trace("Unsubscribing at slot {}, committee subnet {}", slot, entry.getIntKey());
        p2PNetwork.unsubscribeFromSyncCommitteeSubnetId(entry.getIntKey());
        iterator.remove();
      }
    }
  }

  public synchronized void subscribe(final int committeeSubnet, final UInt64 unsubscribeSlot) {
    final UInt64 currentUnsubscribeSlot =
        subcommitteeToUnsubscribeSlot.getOrDefault(committeeSubnet, null);
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
