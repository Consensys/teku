/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.networking.eth2.peers;

import static tech.pegasys.teku.infrastructure.logging.P2PLogger.P2P_LOG;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.logging.P2PLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;

public class PeersDetailedLogger implements SlotEventsChannel {

  private final Spec spec;
  private final Eth2P2PNetwork network;

  public PeersDetailedLogger(final Spec spec, final Eth2P2PNetwork network) {
    this.spec = spec;
    this.network = network;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    // once per slot
    final Map<P2PLogger.Peer, Double> peersScores =
        network
            .streamPeers()
            .collect(
                Collectors.toMap(
                    peer ->
                        new P2PLogger.Peer(
                            peer.getId().toBase58(), peer.getPeerClientType().getDisplayName()),
                    Peer::getGossipScore));
    if (!peersScores.isEmpty()) {
      P2P_LOG.peersGossipScores(peersScores);
    }
    // once per epoch
    if (isStartSlotOfTheEpoch(slot)) {
      final Map<String, List<String>> topicSubscribersByPeerId =
          network.getSubscribersByTopic().entrySet().stream()
              // sort alphabetically
              .sorted(Map.Entry.comparingByKey())
              .collect(
                  Collectors.toMap(
                      Entry::getKey,
                      entry -> entry.getValue().stream().map(NodeId::toBase58).toList(),
                      (oldValue, newValue) -> oldValue,
                      LinkedHashMap::new));
      if (!topicSubscribersByPeerId.isEmpty()) {
        P2P_LOG.peersTopicSubscriptions(topicSubscribersByPeerId);
      }
    }
  }

  private boolean isStartSlotOfTheEpoch(final UInt64 slot) {
    return slot.equals(spec.computeStartSlotAtEpoch(spec.computeEpochAtSlot(slot)));
  }
}
