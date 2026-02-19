/*
 * Copyright Consensys Software Inc., 2026
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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.networking.eth2.gossip.subnets.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.p2p.connection.PeerConnectionType;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.connection.PeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;

public class Eth2PeerSelectionStrategy implements PeerSelectionStrategy {

  public static final int MIN_PEERS_PER_DATA_COLUMN_SUBNET = 1;
  private static final Logger LOG = LogManager.getLogger();

  private final TargetPeerRange targetPeerCountRange;
  private final PeerSubnetSubscriptions.Factory peerSubnetSubscriptionsFactory;
  private final ReputationManager reputationManager;
  private final Shuffler shuffler;

  public Eth2PeerSelectionStrategy(
      final TargetPeerRange targetPeerCountRange,
      final PeerSubnetSubscriptions.Factory peerSubnetSubscriptionsFactory,
      final ReputationManager reputationManager,
      final Shuffler shuffler) {
    this.targetPeerCountRange = targetPeerCountRange;
    this.peerSubnetSubscriptionsFactory = peerSubnetSubscriptionsFactory;
    this.reputationManager = reputationManager;
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

  private record AvailableDiscoveryPeer(DiscoveryPeer discoveryPeer, PeerAddress peerAddress) {}

  private List<PeerAddress> selectPeersByScore(
      final P2PNetwork<?> network,
      final PeerSubnetSubscriptions peerSubnetSubscriptions,
      final int scoreBasedPeersToAdd,
      final List<DiscoveryPeer> allCandidatePeers) {

    // Summary of the peer selection algorithm
    // First peers are scored like pre-Fusaka: a score is calculated per peer individually depending
    // on the relevant subnets for attestations, sync committee and data columns needed. This leads
    // to an overall peer score which takes into account the already connected peer count for each
    // subnet
    // and this list is sorted by highest scoring peers.
    // Then from this list, we select the top peers that satisfy the datacolumn subnet requirements
    // to make sure we have at least one peer per subnet.
    // This list is then the list of 'must have' candidates, and the other peers are 'optional extra
    // candidates'. We combine both lists and select the top ones up to scoreBasedPeersToAdd

    final PeerScorer peerScorer = peerSubnetSubscriptions.createScorer();
    final Map<Integer, Integer> requiredExtraPeerCountPerSubnetId =
        peerSubnetSubscriptions
            .streamRelevantSubnets()
            .boxed()
            .collect(
                Collectors.toMap(
                    subnetId -> subnetId,
                    subnetId ->
                        peerSubnetSubscriptions.getSubscriberCountForDataColumnSidecarSubnet(
                                    subnetId)
                                >= MIN_PEERS_PER_DATA_COLUMN_SUBNET
                            ? 0
                            : MIN_PEERS_PER_DATA_COLUMN_SUBNET));

    // Step 1: Convert candidates to AvailableDiscoveryPeer if they pass filtering
    final List<AvailableDiscoveryPeer> availablePeers =
        allCandidatePeers.stream()
            .map(
                candidate ->
                    checkCandidate(candidate, network)
                        .map(addr -> new AvailableDiscoveryPeer(candidate, addr)))
            .flatMap(Optional::stream)
            .sorted(
                Comparator.comparingInt(
                        (AvailableDiscoveryPeer p) ->
                            peerScorer.scoreCandidatePeer(p.discoveryPeer()))
                    .reversed())
            .toList();

    // Step 2: Partition into must-have and optional
    List<AvailableDiscoveryPeer> mustHavePeers = new ArrayList<>();
    List<AvailableDiscoveryPeer> optionalPeers = new ArrayList<>();

    for (AvailableDiscoveryPeer peer : availablePeers) {
      boolean isMustHave = false;

      var subnets =
          peerSubnetSubscriptions.getDataColumnSidecarSubnetSubscriptionsByNodeId(
              UInt256.fromBytes(peer.discoveryPeer().getNodeId()),
              peer.discoveryPeer().getDasCustodySubnetCount());

      for (int subnetId : subnets.streamAllSetBits().toArray()) {
        Integer remaining = requiredExtraPeerCountPerSubnetId.get(subnetId);
        if (remaining != null && remaining > 0) {
          requiredExtraPeerCountPerSubnetId.put(subnetId, remaining - 1);
          isMustHave = true;
        }
      }

      if (isMustHave) {
        mustHavePeers.add(peer);
      } else {
        optionalPeers.add(peer);
      }
    }

    // Step 3: Combine and limit the total result
    return Stream.concat(mustHavePeers.stream(), optionalPeers.stream())
        .limit(scoreBasedPeersToAdd)
        .map(AvailableDiscoveryPeer::peerAddress)
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
      final P2PNetwork<?> network, final PeerPools peerPools) {

    final Map<PeerConnectionType, List<Peer>> peersBySource =
        network
            .streamPeers()
            .collect(Collectors.groupingBy(peer -> peerPools.getPeerConnectionType(peer.getId())));

    final List<Peer> randomlySelectedPeers =
        peersBySource.getOrDefault(RANDOMLY_SELECTED, new ArrayList<>());
    final int randomlySelectedPeerCount = randomlySelectedPeers.size();

    final int currentPeerCount = network.getPeerCount();
    final int peersToDrop = targetPeerCountRange.getPeersToDrop(currentPeerCount);
    if (peersToDrop == 0) {
      return emptyList();
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
    return Stream.concat(
            randomlySelectedPeersBeingDropped.stream(),
            peersBySource.getOrDefault(SCORE_BASED, emptyList()).stream())
        .sorted(Comparator.comparing(peerScorer::scoreExistingPeer))
        .limit(peersToDrop)
        .toList();
  }

  @FunctionalInterface
  public interface Shuffler {
    void shuffle(List<?> list);
  }
}
