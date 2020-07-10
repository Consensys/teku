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

package tech.pegasys.teku.networking.eth2.peers;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.eth2.gossip.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.p2p.connection.PeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.connection.ReputationManager;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.Peer;

public class Eth2PeerSelectionStrategy implements PeerSelectionStrategy {
  private static final Logger LOG = LogManager.getLogger();

  private final TargetPeerRange targetPeerCountRange;
  private final PeerSubnetSubscriptions.Factory peerSubnetSubscriptionsFactory;
  private final ReputationManager reputationManager;

  public Eth2PeerSelectionStrategy(
      final TargetPeerRange targetPeerCountRange,
      final PeerSubnetSubscriptions.Factory peerSubnetSubscriptionsFactory,
      final ReputationManager reputationManager) {
    this.targetPeerCountRange = targetPeerCountRange;
    this.peerSubnetSubscriptionsFactory = peerSubnetSubscriptionsFactory;
    this.reputationManager = reputationManager;
  }

  @Override
  public List<PeerAddress> selectPeersToConnect(
      final P2PNetwork<?> network, final Supplier<List<DiscoveryPeer>> candidates) {
    final PeerSubnetSubscriptions peerSubnetSubscriptions =
        peerSubnetSubscriptionsFactory.create(network);
    final int peersRequiredForPeerCount =
        targetPeerCountRange.getPeersToAdd(network.getPeerCount());
    final int peersRequiredForSubnets = peerSubnetSubscriptions.getSubscribersRequired();
    final int maxAttempts = Math.max(peersRequiredForPeerCount, peersRequiredForSubnets);
    LOG.trace("Connecting to up to {} known peers", maxAttempts);
    if (maxAttempts == 0) {
      return emptyList();
    }
    final PeerScorer peerScorer = peerSubnetSubscriptions.createScorer();
    return candidates.get().stream()
        .sorted(
            Comparator.comparing((Function<DiscoveryPeer, Integer>) peerScorer::scoreCandidatePeer)
                .reversed())
        .map(network::createPeerAddress)
        .filter(reputationManager::isConnectionInitiationAllowed)
        .filter(peerAddress -> !network.isConnected(peerAddress))
        .limit(maxAttempts)
        .collect(toList());
  }

  @Override
  public List<Peer> selectPeersToDisconnect(
      final P2PNetwork<?> network, final Predicate<Peer> canBeDisconnected) {
    final int peersToDrop = targetPeerCountRange.getPeersToDrop(network.getPeerCount());
    if (peersToDrop == 0) {
      return emptyList();
    }
    final PeerScorer peerScorer = peerSubnetSubscriptionsFactory.create(network).createScorer();
    return network
        .streamPeers()
        .filter(canBeDisconnected)
        .sorted(Comparator.comparing(peerScorer::scoreExistingPeer))
        .limit(peersToDrop)
        .collect(toList());
  }
}
