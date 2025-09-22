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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.networking.eth2.peers.PeerScorer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

/** Scores peers higher if they are tracking subnets that are not tracked by other peers. */
public class SubnetScorer implements PeerScorer {
  private static final int MAX_SUBNET_SCORE = 1000;
  private static final int DISCOVERY_PEER_CACHE_COUNT = 1000;
  private final PeerSubnetSubscriptions peerSubnetSubscriptions;
  private final LRUCache<DiscoveryPeer, SszBitvector> peerToDataColumnSubnets =
      LRUCache.create(DISCOVERY_PEER_CACHE_COUNT);

  private SubnetScorer(final PeerSubnetSubscriptions peerSubnetSubscriptions) {
    this.peerSubnetSubscriptions = peerSubnetSubscriptions;
  }

  public static SubnetScorer create(final PeerSubnetSubscriptions peerSubscriptions) {
    return new SubnetScorer(peerSubscriptions);
  }

  @Override
  public int scoreExistingPeer(final NodeId peerId) {
    final SszBitvector attSubscriptions =
        peerSubnetSubscriptions.getAttestationSubnetSubscriptions(peerId);
    final SszBitvector syncCommitteeSubscriptions =
        peerSubnetSubscriptions.getSyncCommitteeSubscriptions(peerId);
    final SszBitvector dataColumnSidecarSubscriptions =
        peerSubnetSubscriptions.getDataColumnSidecarSubnetSubscriptions(peerId);
    return score(
        attSubscriptions,
        syncCommitteeSubscriptions,
        dataColumnSidecarSubscriptions,
        this::scoreSubnetForExistingPeer,
        new SelectedCandidateSubnetCountChanges());
  }

  @Override
  public List<DiscoveryPeer> selectCandidatePeers(
      final List<DiscoveryPeer> candidates, final int maxToSelect) {
    final SelectedCandidateSubnetCountChanges subnetChanges =
        new SelectedCandidateSubnetCountChanges();

    List<DiscoveryPeer> selectedPeers = new ArrayList<>();
    List<DiscoveryPeer> remainingPeers = new ArrayList<>(candidates);
    while (selectedPeers.size() < maxToSelect) {
      Optional<DiscoveryPeer> maybeHighestScoringPeer =
          remainingPeers.stream()
              .max(Comparator.comparing(peer -> scoreCandidatePeer(peer, subnetChanges)));
      if (maybeHighestScoringPeer.isEmpty()) {
        break;
      }
      DiscoveryPeer highestScoringPeer = maybeHighestScoringPeer.get();
      selectedPeers.add(highestScoringPeer);
      remainingPeers.remove(highestScoringPeer);
      peerSubnetSubscriptions
          .getAttestationSubnetSubscriptions(highestScoringPeer.getNodeId())
          .streamAllSetBits()
          .forEach(
              i ->
                  subnetChanges.attestationSubnets.put(
                      i, subnetChanges.attestationSubnets.getOrDefault(i, 0) + 1));
      peerSubnetSubscriptions
          .getSyncCommitteeSubscriptions(highestScoringPeer.getNodeId())
          .streamAllSetBits()
          .forEach(
              i ->
                  subnetChanges.syncCommitteeSubnets.put(
                      i, subnetChanges.syncCommitteeSubnets.getOrDefault(i, 0) + 1));
      getDataColumnSidecarSubnetsForCandidatePeer(highestScoringPeer)
          .streamAllSetBits()
          .forEach(
              i ->
                  subnetChanges.dataColumnSidecarSubnets.put(
                      i, subnetChanges.dataColumnSidecarSubnets.getOrDefault(i, 0) + 1));
    }

    return selectedPeers;
  }

  public int scoreCandidatePeer(
      final DiscoveryPeer candidate, final SelectedCandidateSubnetCountChanges subnetChanges) {
    return scoreCandidatePeer(
        candidate.getPersistentAttestationSubnets(),
        candidate.getSyncCommitteeSubnets(),
        getDataColumnSidecarSubnetsForCandidatePeer(candidate),
        subnetChanges);
  }

  private SszBitvector getDataColumnSidecarSubnetsForCandidatePeer(final DiscoveryPeer candidate) {
    return peerToDataColumnSubnets.get(
        candidate,
        cachingCandidate ->
            peerSubnetSubscriptions.getDataColumnSidecarSubnetSubscriptionsByNodeId(
                UInt256.fromBytes(cachingCandidate.getNodeId()),
                cachingCandidate.getDasCustodySubnetCount()));
  }

  //  @Override
  public int scoreCandidatePeer(
      final SszBitvector attSubnetSubscriptions,
      final SszBitvector syncCommitteeSubnetSubscriptions,
      final SszBitvector dataColumnSidecarSubscriptions,
      final SelectedCandidateSubnetCountChanges subnetChanges) {
    return score(
        attSubnetSubscriptions,
        syncCommitteeSubnetSubscriptions,
        dataColumnSidecarSubscriptions,
        this::scoreSubnetForCandidatePeer,
        subnetChanges);
  }

  private int score(
      final SszBitvector attestationSubnetSubscriptions,
      final SszBitvector syncCommitteeSubnetSubscriptions,
      final SszBitvector dataColumnSidecarSubnetSubscriptions,
      final IntUnaryOperator subscriberCountToScore,
      final SelectedCandidateSubnetCountChanges subnetChanges) {
    final int attestationSubnetScore =
        attestationSubnetSubscriptions
            .streamAllSetBits()
            .filter(peerSubnetSubscriptions::isAttestationSubnetRelevant)
            .map(
                subnetId -> {
                  int subscriberCount =
                      peerSubnetSubscriptions.getSubscriberCountForAttestationSubnet(subnetId)
                          + subnetChanges.attestationSubnets.getOrDefault(subnetId, 0);
                  return subscriberCountToScore.applyAsInt(subscriberCount);
                })
            .sum();

    final int syncCommitteeSubnetScore =
        syncCommitteeSubnetSubscriptions
            .streamAllSetBits()
            .filter(peerSubnetSubscriptions::isSyncCommitteeSubnetRelevant)
            .map(
                subnetId -> {
                  int subscriberCount =
                      peerSubnetSubscriptions.getSubscriberCountForSyncCommitteeSubnet(subnetId)
                          + subnetChanges.syncCommitteeSubnets.getOrDefault(subnetId, 0);
                  return subscriberCountToScore.applyAsInt(subscriberCount);
                })
            .sum();

    final int dataColumnSidecarSubnetScore =
        dataColumnSidecarSubnetSubscriptions
            .streamAllSetBits()
            .filter(peerSubnetSubscriptions::isDataColumnSidecarSubnetRelevant)
            .map(
                subnetId -> {
                  int subscriberCount =
                      peerSubnetSubscriptions.getSubscriberCountForDataColumnSidecarSubnet(subnetId)
                          + subnetChanges.dataColumnSidecarSubnets.getOrDefault(subnetId, 0);
                  return subscriberCountToScore.applyAsInt(subscriberCount);
                })
            .sum();

    return attestationSubnetScore + syncCommitteeSubnetScore + dataColumnSidecarSubnetScore;
  }

  private int scoreSubnetForExistingPeer(final int subscriberCount) {
    // The peer we're scoring is already included in the subscriberCount
    return scoreSubnetForCandidatePeer(Math.max(0, subscriberCount - 1));
  }

  private int scoreSubnetForCandidatePeer(final int numberOfOtherSubscribers) {
    final int value = numberOfOtherSubscribers + 1;
    return MAX_SUBNET_SCORE
        / (value * value * value); // third power because having a first peer is very important
  }

  public static class SelectedCandidateSubnetCountChanges {
    final Map<Integer, Integer> attestationSubnets = new HashMap<>();
    final Map<Integer, Integer> syncCommitteeSubnets = new HashMap<>();
    final Map<Integer, Integer> dataColumnSidecarSubnets = new HashMap<>();
  }
}
