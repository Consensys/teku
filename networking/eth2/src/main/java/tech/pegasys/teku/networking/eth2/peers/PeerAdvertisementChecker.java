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

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.networking.eth2.ActiveEth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.gossip.subnets.NodeIdToDataColumnSidecarSubnetsCalculator;
import tech.pegasys.teku.networking.eth2.gossip.subnets.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;

/** Verifies peer advertisement in ENR against its actual subnet topic subscriptions */
public class PeerAdvertisementChecker {
  private static final Logger LOG = LogManager.getLogger();
  private final PeerSubnetSubscriptions.Factory peerSubnetSubscriptionsFactory;
  private final ReputationManager reputationManager;

  private final NodeIdToDataColumnSidecarSubnetsCalculator
      nodeIdToDataColumnSidecarSubnetsCalculator;
  private final AtomicReference<ActiveEth2P2PNetwork> activeEth2P2PNetworkReference;

  public PeerAdvertisementChecker(
      final PeerSubnetSubscriptions.Factory peerSubnetSubscriptionsFactory,
      final ReputationManager reputationManager,
      final NodeIdToDataColumnSidecarSubnetsCalculator nodeIdToDataColumnSidecarSubnetsCalculator,
      final AtomicReference<ActiveEth2P2PNetwork> activeEth2P2PNetworkReference) {
    this.peerSubnetSubscriptionsFactory = peerSubnetSubscriptionsFactory;
    this.reputationManager = reputationManager;
    this.nodeIdToDataColumnSidecarSubnetsCalculator = nodeIdToDataColumnSidecarSubnetsCalculator;
    this.activeEth2P2PNetworkReference = activeEth2P2PNetworkReference;
  }

  /** List of peers that are not subscribed to the topics for which they advertise the data */
  public List<Peer> findFalseAdvertisingPeers(
      final Stream<Peer> candidatePeers,
      final P2PNetwork<?> network,
      final DiscoveryService discoveryService) {
    final Map<String, Collection<NodeId>> subscribersByTopic = network.getSubscribersByTopic();
    final Map<NodeId, List<String>> topicsBySubscriber =
        subscribersByTopic.entrySet().stream()
            .flatMap(
                entry -> entry.getValue().stream().map(nodeId -> Map.entry(nodeId, entry.getKey())))
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    final Map<UInt256, DiscoveryPeer> discoveryPeerMap =
        discoveryService
            .streamKnownPeers()
            .collect(
                Collectors.toMap(
                    discoveryPeer -> UInt256.fromBytes(discoveryPeer.getNodeId()),
                    Function.identity(),
                    (existing, replacement) -> replacement));
    final PeerSubnetSubscriptions peerSubnetSubscriptions =
        peerSubnetSubscriptionsFactory.create(network);

    return candidatePeers
        .filter(
            peer ->
                !verifyPeerAdvertisement(
                    peer, topicsBySubscriber, discoveryPeerMap, peerSubnetSubscriptions))
        .toList();
  }

  /**
   * Verifies peer advertisement in ENR against its actual subnet topic subscriptions. For a good
   * peer advertisement should be <= actual subscription. When we don't have enough data for
   * decision, considers peer to be good.
   *
   * @return <b>true</b> if peer doesn't advertise anything it's not subscribed to. <b>false</b> if
   *     peer advertises data for which it's not subscribed for appropriate subnets.
   */
  @VisibleForTesting
  boolean verifyPeerAdvertisement(
      final Peer peer,
      final Map<NodeId, List<String>> topicsBySubscriber,
      final Map<UInt256, DiscoveryPeer> discoveryPeerMap,
      final PeerSubnetSubscriptions peerSubnetSubscriptions) {

    // If any data required for check is missing, consider peer to be good
    if (activeEth2P2PNetworkReference.get() == null) {
      return true;
    }
    final ActiveEth2P2PNetwork activeEth2P2PNetwork = activeEth2P2PNetworkReference.get();

    final List<String> actualPeerTopics = topicsBySubscriber.getOrDefault(peer.getId(), List.of());
    if (actualPeerTopics.isEmpty()) {
      return true;
    }

    final Optional<Eth2Peer> eth2Peer = activeEth2P2PNetwork.getPeer(peer.getId());
    if (eth2Peer.isEmpty()) {
      return true;
    }

    final Optional<UInt256> discoveryNodeId = eth2Peer.get().getDiscoveryNodeId();
    if (discoveryNodeId.isEmpty()) {
      return true;
    }

    if (!discoveryPeerMap.containsKey(discoveryNodeId.get())) {
      return true;
    }

    // Actual check
    final DiscoveryPeer discoveryPeer = discoveryPeerMap.get(discoveryNodeId.get());

    // Check for sync committee subnets
    final SszBitvector advertisedSyncCommitteeSubnets = discoveryPeer.getSyncCommitteeSubnets();
    final Optional<SszBitvector> maybeSyncCommitteeSubnets =
        peerSubnetSubscriptions.getSyncCommitteeSubscriptionsIfAvailable(peer.getId());
    if (maybeSyncCommitteeSubnets.isPresent()
        && !maybeSyncCommitteeSubnets.get().isSuperSetOf(advertisedSyncCommitteeSubnets)) {
      LOG.debug(
          "Sync committee subnet subscriptions ({}) doesn't include ENR advertised subnets ({}) for peer {}. Downscoring.",
          () -> maybeSyncCommitteeSubnets.get().sszSerialize().toHexString(),
          () -> advertisedSyncCommitteeSubnets.sszSerialize().toHexString(),
          peer::getId);
      reputationManager.adjustReputation(peer.getAddress(), ReputationAdjustment.LARGE_PENALTY);
      return false;
    }

    // Check for attestation subnets
    final SszBitvector advertisedPersistentAttestationSubnets =
        discoveryPeer.getPersistentAttestationSubnets();
    final Optional<SszBitvector> maybePersistedAttestationSubnets =
        peerSubnetSubscriptions.getAttestationSubnetSubscriptionsIfAvailable(peer.getId());
    if (maybePersistedAttestationSubnets.isPresent()
        && !maybePersistedAttestationSubnets
            .get()
            .isSuperSetOf(advertisedPersistentAttestationSubnets)) {
      LOG.debug(
          "Attestation subnet subscriptions ({}) doesn't include ENR advertised subnets ({}) for peer {}. Downscoring.",
          () -> maybePersistedAttestationSubnets.get().sszSerialize().toHexString(),
          () -> advertisedPersistentAttestationSubnets.sszSerialize().toHexString(),
          peer::getId);
      reputationManager.adjustReputation(peer.getAddress(), ReputationAdjustment.LARGE_PENALTY);
      return false;
    }

    // for each check that there are subscriptions
    final Optional<SszBitvector> maybeDasSubnets =
        nodeIdToDataColumnSidecarSubnetsCalculator.calculateSubnets(
            discoveryNodeId.get(), discoveryPeer.getDasCustodyGroupCount());
    if (maybeDasSubnets.isPresent()) {
      final SszBitvector advertisedDasSubnets = maybeDasSubnets.get();
      final Optional<SszBitvector> maybeSubscribedDasSubnets =
          peerSubnetSubscriptions.getDataColumnSidecarSubnetSubscriptionsIfAvailable(peer.getId());
      if (maybeSubscribedDasSubnets.isPresent()
          && !maybeSubscribedDasSubnets.get().isSuperSetOf(advertisedDasSubnets)) {
        LOG.debug(
            "Data column sidecar subnet subscriptions ({}) doesn't include ENR advertised subnets ({}) for peer {}. Downscoring.",
            () -> maybeSubscribedDasSubnets.get().sszSerialize().toHexString(),
            () -> advertisedDasSubnets.sszSerialize().toHexString(),
            peer::getId);
        reputationManager.adjustReputation(peer.getAddress(), ReputationAdjustment.LARGE_PENALTY);
        return false;
      }
    }

    return true;
  }
}
