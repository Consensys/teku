/*
 * Copyright Consensys Software Inc., 2024
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

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DasPeerCustodyCountSupplier;

public class GossipTopicDasPeerCustodyTracker
    implements DasPeerCustodyCountSupplier, PeerConnectedSubscriber<Eth2Peer> {
  private static final Logger LOG = LogManager.getLogger();

  public static final int NO_SUBNET_COUNT_INFO = -1;

  private final Spec spec;
  private final GossipNetwork gossipNetwork;
  private final GossipEncoding gossipEncoding;
  private final Supplier<Optional<ForkInfo>> currentForkInfoSupplier;

  private final Map<UInt256, Entry> connectedPeerSubnets = new ConcurrentHashMap<>();

  public GossipTopicDasPeerCustodyTracker(
      final Spec spec,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final Supplier<Optional<ForkInfo>> currentForkInfoSupplier,
      final AsyncRunner asyncRunner) {
    this.spec = spec;
    this.gossipNetwork = gossipNetwork;
    this.gossipEncoding = gossipEncoding;
    this.currentForkInfoSupplier = currentForkInfoSupplier;
    asyncRunner.runWithFixedDelay(
        this::refreshExistingSubscriptions,
        Duration.ofSeconds(1),
        e -> LOG.warn("[nyota] Error {}", e, e));
  }

  @Override
  public void onConnected(final Eth2Peer peer) {
    connectedPeerSubnets.put(
        peer.getDiscoveryNodeId().orElseThrow(), new Entry(peer.getId(), NO_SUBNET_COUNT_INFO));
    peer.subscribeDisconnect((__, ___) -> peerDisconnected(peer));
    refreshExistingSubscriptions();
  }

  private void peerDisconnected(final Eth2Peer peer) {
    connectedPeerSubnets.remove(peer.getDiscoveryNodeId().orElseThrow());
  }

  private Set<String> getCurrentDasTopics() {
    return currentForkInfoSupplier
        .get()
        .map(
            forkInfo ->
                GossipTopics.getAllDataColumnSidecarSubnetTopics(
                    gossipEncoding, forkInfo.getForkDigest(spec), spec))
        .orElse(Collections.emptySet());
  }

  private synchronized void refreshExistingSubscriptions() {
    Map<String, Collection<NodeId>> subscribersByTopic = gossipNetwork.getSubscribersByTopic();
    Set<String> dasTopics = getCurrentDasTopics();
    record NodeTopic(NodeId nodeId, String topic) {}

    Map<NodeId, Long> nodeToSubnetCount =
        subscribersByTopic.entrySet().stream()
            .flatMap(
                entry ->
                    entry.getValue().stream().map(nodeId -> new NodeTopic(nodeId, entry.getKey())))
            .filter(entry -> dasTopics.contains(entry.topic()))
            .collect(Collectors.groupingBy(NodeTopic::nodeId, Collectors.counting()));
    connectedPeerSubnets.replaceAll(
        (nodeId, entry) -> {
          Long maybeCount = nodeToSubnetCount.get(entry.libp2pPeerId());
          int count = maybeCount == null ? NO_SUBNET_COUNT_INFO : maybeCount.intValue();
          return entry.withSubnetCount(count);
        });
  }

  @Override
  public int getCustodyGroupCountForPeer(final UInt256 nodeId) {
    Entry entry = connectedPeerSubnets.get(nodeId);
    return entry != null ? entry.subnetCount() : 0;
  }

  private record Entry(NodeId libp2pPeerId, Integer subnetCount) {
    public Entry withSubnetCount(final int newCount) {
      return newCount == subnetCount ? this : new Entry(libp2pPeerId, newCount);
    }
  }
}
