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

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static tech.pegasys.teku.networking.p2p.connection.PeerConnectionType.RANDOMLY_SELECTED;
import static tech.pegasys.teku.networking.p2p.connection.PeerConnectionType.SCORE_BASED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.networking.eth2.ActiveEth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.gossip.subnets.NodeIdToDataColumnSidecarSubnetsCalculator;
import tech.pegasys.teku.networking.eth2.gossip.subnets.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.p2p.connection.PeerConnectionType;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.connection.PeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;

public class Eth2PeerSelectionStrategy implements PeerSelectionStrategy {
  private static final Logger LOG = LogManager.getLogger();

  private final TargetPeerRange targetPeerCountRange;
  private final PeerSubnetSubscriptions.Factory peerSubnetSubscriptionsFactory;
  private final ReputationManager reputationManager;
  private final Shuffler shuffler;

  private final NodeIdToDataColumnSidecarSubnetsCalculator
      nodeIdToDataColumnSidecarSubnetsCalculator;
  private final AtomicReference<ActiveEth2P2PNetwork> activeEth2P2PNetworkReference;

  public Eth2PeerSelectionStrategy(
      final TargetPeerRange targetPeerCountRange,
      final PeerSubnetSubscriptions.Factory peerSubnetSubscriptionsFactory,
      final ReputationManager reputationManager,
      final AtomicReference<ActiveEth2P2PNetwork> activeEth2P2PNetworkReference,
      final NodeIdToDataColumnSidecarSubnetsCalculator nodeIdToDataColumnSidecarSubnetsCalculator,
      final Shuffler shuffler) {
    this.targetPeerCountRange = targetPeerCountRange;
    this.peerSubnetSubscriptionsFactory = peerSubnetSubscriptionsFactory;
    this.reputationManager = reputationManager;
    this.activeEth2P2PNetworkReference = activeEth2P2PNetworkReference;
    this.nodeIdToDataColumnSidecarSubnetsCalculator = nodeIdToDataColumnSidecarSubnetsCalculator;
    this.shuffler = shuffler;
  }

  @Override
  public List<PeerAddress> selectPeersToConnect(
      final P2PNetwork<?> network,
      final PeerPools peerPools,
      final Supplier<? extends Collection<DiscoveryPeer>> candidates) {
    final PeerSubnetSubscriptions peerSubnetSubscriptions =
        peerSubnetSubscriptionsFactory.create(network);
    final int peersRequiredForPeerCount =
        targetPeerCountRange.getPeersToAdd(network.getPeerCount());
    final int randomlySelectedPeerCount = getCurrentRandomlySelectedPeerCount(network, peerPools);
    final int randomlySelectedPeersToAdd =
        targetPeerCountRange.getRandomlySelectedPeersToAdd(randomlySelectedPeerCount);

    final int peersRequiredForSubnets = peerSubnetSubscriptions.getSubscribersRequired();
    final int scoreBasedPeersToAdd =
        Math.max(peersRequiredForPeerCount - randomlySelectedPeerCount, peersRequiredForSubnets);
    final int maxPeersToAdd = scoreBasedPeersToAdd + randomlySelectedPeersToAdd;
    LOG.trace("Connecting to up to {} known peers", maxPeersToAdd);
    if (maxPeersToAdd == 0) {
      return emptyList();
    }

    final List<DiscoveryPeer> allCandidatePeers = new ArrayList<>(candidates.get());
    final List<PeerAddress> selectedPeers = new ArrayList<>();

    if (randomlySelectedPeersToAdd > 0) {
      selectedPeers.addAll(
          selectAndRemoveRandomPeers(
              network, peerPools, randomlySelectedPeersToAdd, allCandidatePeers));
    }

    if (scoreBasedPeersToAdd > 0) {
      selectedPeers.addAll(
          selectPeersByScore(
              network, peerSubnetSubscriptions, scoreBasedPeersToAdd, allCandidatePeers));
    }
    return unmodifiableList(selectedPeers); // Unmodifiable to make errorprone happy
  }

  private List<PeerAddress> selectAndRemoveRandomPeers(
      final P2PNetwork<?> network,
      final PeerPools peerPools,
      final int randomlySelectedPeersToAdd,
      final List<DiscoveryPeer> allCandidatePeers) {
    final List<PeerAddress> selectedPeers = new ArrayList<>();
    shuffler.shuffle(allCandidatePeers);
    while (!allCandidatePeers.isEmpty() && selectedPeers.size() < randomlySelectedPeersToAdd) {
      final DiscoveryPeer candidate = allCandidatePeers.removeFirst();
      checkCandidate(candidate, network)
          .ifPresent(
              peerAddress -> {
                peerPools.addPeerToPool(peerAddress.getId(), RANDOMLY_SELECTED);
                selectedPeers.add(peerAddress);
              });
    }
    return selectedPeers;
  }

  private List<PeerAddress> selectPeersByScore(
      final P2PNetwork<?> network,
      final PeerSubnetSubscriptions peerSubnetSubscriptions,
      final int scoreBasedPeersToAdd,
      final List<DiscoveryPeer> allCandidatePeers) {
    final PeerScorer peerScorer = peerSubnetSubscriptions.createScorer();
    return allCandidatePeers.stream()
        .sorted(
            Comparator.comparing((Function<DiscoveryPeer, Integer>) peerScorer::scoreCandidatePeer)
                .reversed())
        .flatMap(candidate -> checkCandidate(candidate, network).stream())
        .limit(scoreBasedPeersToAdd)
        .toList();
  }

  private int getCurrentRandomlySelectedPeerCount(
      final P2PNetwork<?> network, final PeerPools peerPools) {
    return (int)
        network
            .streamPeers()
            .filter(peer -> peerPools.getPeerConnectionType(peer.getId()) == RANDOMLY_SELECTED)
            .count();
  }

  private Optional<PeerAddress> checkCandidate(
      final DiscoveryPeer candidate, final P2PNetwork<?> network) {
    return Optional.of(network.createPeerAddress(candidate))
        .filter(reputationManager::isConnectionInitiationAllowed)
        .filter(peerAddress -> !network.isConnected(peerAddress));
  }

  @Override
  public List<Peer> selectPeersToDisconnect(
      final P2PNetwork<?> network,
      final DiscoveryService discoveryService,
      final PeerPools peerPools) {

    final Map<PeerConnectionType, List<Peer>> peersBySource =
        network
            .streamPeers()
            .collect(Collectors.groupingBy(peer -> peerPools.getPeerConnectionType(peer.getId())));

    final List<Peer> randomlySelectedPeers =
        peersBySource.getOrDefault(RANDOMLY_SELECTED, new ArrayList<>());
    final int randomlySelectedPeerCount = randomlySelectedPeers.size();

    final int currentPeerCount = network.getPeerCount();
    final int peersToDrop = targetPeerCountRange.getPeersToDrop(currentPeerCount);

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
                    Function.identity()));
    final PeerSubnetSubscriptions peerSubnetSubscriptions =
        peerSubnetSubscriptionsFactory.create(network);

    final List<Peer> falseAdvertisingPeers =
        Stream.concat(
                peersBySource.getOrDefault(RANDOMLY_SELECTED, emptyList()).stream(),
                peersBySource.getOrDefault(SCORE_BASED, emptyList()).stream())
            .map(
                peer ->
                    verifyPeerAdvertisement(
                        peer, topicsBySubscriber, discoveryPeerMap, peerSubnetSubscriptions))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

    if (peersToDrop == 0) {
      return falseAdvertisingPeers;
    }

    final PeerScorer peerScorer = peerSubnetSubscriptionsFactory.create(network).createScorer();
    final int randomlySelectedPeersToDrop =
        targetPeerCountRange.getRandomlySelectedPeersToDrop(
            randomlySelectedPeerCount, currentPeerCount);
    shuffler.shuffle(randomlySelectedPeers);
    final List<Peer> randomlySelectedPeersBeingDropped =
        randomlySelectedPeers.subList(
            0, Math.min(randomlySelectedPeersToDrop, randomlySelectedPeers.size()));
    // Peers from the randomly selected pool that have been chosen are moved have their special
    // randomly selected status revoked, leaving them in the default pool where they are considered
    // for disconnection based on their score
    randomlySelectedPeersBeingDropped.forEach(
        peer -> peerPools.addPeerToPool(peer.getId(), SCORE_BASED));

    if (peersToDrop > falseAdvertisingPeers.size()) {
      return Stream.concat(
              falseAdvertisingPeers.stream(),
              Stream.concat(
                      randomlySelectedPeersBeingDropped.stream(),
                      peersBySource.getOrDefault(SCORE_BASED, emptyList()).stream())
                  .sorted(Comparator.comparing(peerScorer::scoreExistingPeer))
                  .limit(peersToDrop - falseAdvertisingPeers.size()))
          .toList();
    } else {
      return falseAdvertisingPeers;
    }
  }

  private Optional<Peer> verifyPeerAdvertisement(
      final Peer peer,
      final Map<NodeId, List<String>> topicsBySubscriber,
      final Map<UInt256, DiscoveryPeer> discoveryPeerMap,
      final PeerSubnetSubscriptions peerSubnetSubscriptions) {
    if (activeEth2P2PNetworkReference.get() == null) {
      return Optional.empty();
    }
    final ActiveEth2P2PNetwork activeEth2P2PNetwork = activeEth2P2PNetworkReference.get();

    final List<String> actualPeerTopics = topicsBySubscriber.get(peer.getId());
    if (actualPeerTopics.isEmpty()) {
      return Optional.empty();
    }

    final Optional<Eth2Peer> eth2Peer = activeEth2P2PNetwork.getPeer(peer.getId());
    if (eth2Peer.isEmpty()) {
      return Optional.empty();
    }

    final Optional<UInt256> discoveryNodeId = eth2Peer.get().getDiscoveryNodeId();
    if (discoveryNodeId.isEmpty()) {
      return Optional.empty();
    }

    if (!discoveryPeerMap.containsKey(discoveryNodeId.get())) {
      return Optional.empty();
    }

    final DiscoveryPeer discoveryPeer = discoveryPeerMap.get(discoveryNodeId.get());

    // Check for sync committee subnets
    final SszBitvector advertisedSyncCommitteeSubnets = discoveryPeer.getSyncCommitteeSubnets();
    final SszBitvector syncCommitteeSubnets =
        peerSubnetSubscriptions.getAttestationSubnetSubscriptions(peer.getId());
    if (!syncCommitteeSubnets.isSuperSetOf(advertisedSyncCommitteeSubnets)) {
      reputationManager.adjustReputation(peer.getAddress(), ReputationAdjustment.LARGE_PENALTY);
      return Optional.of(peer);
    }

    // Check for attestation subnets
    final SszBitvector advertisedPersistentAttestationSubnets =
        discoveryPeer.getPersistentAttestationSubnets();
    final SszBitvector persistedAttestationSubnets =
        peerSubnetSubscriptions.getAttestationSubnetSubscriptions(peer.getId());
    if (!persistedAttestationSubnets.isSuperSetOf(advertisedPersistentAttestationSubnets)) {
      reputationManager.adjustReputation(peer.getAddress(), ReputationAdjustment.LARGE_PENALTY);
      return Optional.of(peer);
    }

    // for each check that there are subscriptions
    final Optional<SszBitvector> maybeDasSubnets =
        nodeIdToDataColumnSidecarSubnetsCalculator.calculateSubnets(
            discoveryNodeId.get(), discoveryPeer.getDasCustodyGroupCount());
    if (maybeDasSubnets.isPresent()) {
      final SszBitvector advertisedDasSubnets = maybeDasSubnets.get();
      final SszBitvector dasSubnets =
          peerSubnetSubscriptions.getDataColumnSidecarSubnetSubscriptions(peer.getId());
      if (!dasSubnets.isSuperSetOf(advertisedDasSubnets)) {
        reputationManager.adjustReputation(peer.getAddress(), ReputationAdjustment.LARGE_PENALTY);
        return Optional.of(peer);
      }
    }

    return Optional.empty();
  }

  @FunctionalInterface
  public interface Shuffler {
    void shuffle(List<?> list);
  }
}
