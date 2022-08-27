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

package tech.pegasys.teku.networking.eth2.peers;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.networking.p2p.connection.PeerPools.PeerPool.RANDOMLY_SELECTED;
import static tech.pegasys.teku.networking.p2p.connection.PeerPools.PeerPool.SCORE_BASED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.eth2.gossip.subnets.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.connection.PeerPools.PeerPool;
import tech.pegasys.teku.networking.p2p.connection.PeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;

public class Eth2PeerSelectionStrategy implements PeerSelectionStrategy {
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
    LOG.info("selectPeersToConnect:{}, {}", network, allCandidatePeers.size());
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
      final DiscoveryPeer candidate = allCandidatePeers.remove(0);
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
        .collect(toList());
  }

  private int getCurrentRandomlySelectedPeerCount(
      final P2PNetwork<?> network, final PeerPools peerPools) {
    return (int)
        network
            .streamPeers()
            .filter(peer -> peerPools.getPool(peer.getId()) == RANDOMLY_SELECTED)
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

    final Map<PeerPool, List<Peer>> peersBySource =
        network
            .streamPeers()
            .collect(Collectors.groupingBy(peer -> peerPools.getPool(peer.getId())));

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
        .collect(toList());
  }

  @FunctionalInterface
  public interface Shuffler {
    void shuffle(List<?> list);
  }
}
