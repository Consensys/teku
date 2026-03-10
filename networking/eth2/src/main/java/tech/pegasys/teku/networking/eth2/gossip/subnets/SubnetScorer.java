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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.networking.eth2.peers.PeerId;
import tech.pegasys.teku.networking.eth2.peers.PeerScorer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;

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
  public int scoreExistingPeer(final PeerId peerId) {
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
      // By default a comparator does not cache results, but scoring can be expensive so we cache
      // results here to avoid recomputing scores for the same peer multiple times per selection
      // round.
      final Comparator<DiscoveryPeer> cachingComparator =
          new Comparator<>() {
            private final Map<DiscoveryPeer, Integer> cache = new HashMap<>();

            @Override
            public int compare(final DiscoveryPeer a, final DiscoveryPeer b) {
              int scoreA =
                  cache.computeIfAbsent(a, peer -> scoreCandidatePeer(peer, subnetChanges));
              int scoreB =
                  cache.computeIfAbsent(b, peer -> scoreCandidatePeer(peer, subnetChanges));
              return Integer.compare(scoreA, scoreB);
            }
          };
      Optional<DiscoveryPeer> maybeHighestScoringPeer =
          remainingPeers.stream().max(cachingComparator);
      if (maybeHighestScoringPeer.isEmpty()) {
        break;
      }
      DiscoveryPeer highestScoringPeer = maybeHighestScoringPeer.get();
      selectedPeers.add(highestScoringPeer);
      remainingPeers.remove(highestScoringPeer);

      PeerId highestScoringPeerId = PeerId.fromCandidateId(highestScoringPeer.getNodeId());
      peerSubnetSubscriptions
          .getAttestationSubnetSubscriptions(highestScoringPeerId)
          .streamAllSetBits()
          .forEach(i -> subnetChanges.increment(SubnetType.ATTESTATION, i));

      peerSubnetSubscriptions
          .getSyncCommitteeSubscriptions(highestScoringPeerId)
          .streamAllSetBits()
          .forEach(i -> subnetChanges.increment(SubnetType.SYNC_COMMITTEE, i));

      getDataColumnSidecarSubnetsForCandidatePeer(highestScoringPeer)
          .streamAllSetBits()
          .forEach(i -> subnetChanges.increment(SubnetType.DATA_COLUMN_SIDECAR, i));
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
                PeerId.fromCandidateId(cachingCandidate.getNodeId()),
                cachingCandidate.getDasCustodySubnetCount()));
  }

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
                          + subnetChanges.get(SubnetType.ATTESTATION, subnetId);
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
                          + subnetChanges.get(SubnetType.SYNC_COMMITTEE, subnetId);
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
                          + subnetChanges.get(SubnetType.DATA_COLUMN_SIDECAR, subnetId);
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

  public enum SubnetType {
    ATTESTATION,
    SYNC_COMMITTEE,
    DATA_COLUMN_SIDECAR
  }

  public static class SubnetCounts {
    private final Int2IntOpenHashMap counts = new Int2IntOpenHashMap();

    public int get(final int subnetId) {
      return counts.getOrDefault(subnetId, 0);
    }

    public void increment(final int subnetId) {
      counts.addTo(subnetId, 1); // efficient primitive add
    }
  }

  public static class SelectedCandidateSubnetCountChanges {
    private final Map<SubnetType, SubnetCounts> subnetChanges = new EnumMap<>(SubnetType.class);

    public SelectedCandidateSubnetCountChanges() {
      for (SubnetType type : SubnetType.values()) {
        subnetChanges.put(type, new SubnetCounts());
      }
    }

    public int get(final SubnetType type, final int subnetId) {
      return subnetChanges.get(type).get(subnetId);
    }

    public void increment(final SubnetType type, final int subnetId) {
      subnetChanges.get(type).increment(subnetId);
    }
  }
}
