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

import java.util.Optional;
import java.util.function.IntUnaryOperator;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.networking.eth2.peers.PeerScorer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

/** Scores peers higher if they are tracking subnets that are not tracked by other peers. */
public class SubnetScorer implements PeerScorer {
  private static final int MAX_SUBNET_SCORE = 1000;
  private final PeerSubnetSubscriptions peerSubnetSubscriptions;

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
    final Optional<SszBitvector> executionProofSubscriptions =
        peerSubnetSubscriptions.getExecutionProofSubnetSubscriptions(peerId);
    return score(
        attSubscriptions,
        syncCommitteeSubscriptions,
        dataColumnSidecarSubscriptions,
        executionProofSubscriptions,
        this::scoreSubnetForExistingPeer);
  }

  @Override
  public int scoreCandidatePeer(final DiscoveryPeer candidate) {
    return scoreCandidatePeer(
        candidate.getPersistentAttestationSubnets(),
        candidate.getSyncCommitteeSubnets(),
        peerSubnetSubscriptions.getDataColumnSidecarSubnetSubscriptionsByNodeId(
            UInt256.fromBytes(candidate.getNodeId()), candidate.getDasCustodySubnetCount()),
        candidate.getExecutionProofSubnets());
  }

  //  @Override
  public int scoreCandidatePeer(
      final SszBitvector attSubnetSubscriptions,
      final SszBitvector syncCommitteeSubnetSubscriptions,
      final SszBitvector dataColumnSidecarSubscriptions,
      final Optional<SszBitvector> executionProofSubnetSubscriptions) {
    return score(
        attSubnetSubscriptions,
        syncCommitteeSubnetSubscriptions,
        dataColumnSidecarSubscriptions,
        executionProofSubnetSubscriptions,
        this::scoreSubnetForCandidatePeer);
  }

  private int score(
      final SszBitvector attestationSubnetSubscriptions,
      final SszBitvector syncCommitteeSubnetSubscriptions,
      final SszBitvector dataColumnSidecarSubnetSubscriptions,
      final Optional<SszBitvector> executionProofSubnetSubscriptions,
      final IntUnaryOperator subscriberCountToScore) {
    final int attestationSubnetScore =
        attestationSubnetSubscriptions
            .streamAllSetBits()
            .filter(peerSubnetSubscriptions::isAttestationSubnetRelevant)
            .map(
                subnetId -> {
                  int subscriberCount =
                      peerSubnetSubscriptions.getSubscriberCountForAttestationSubnet(subnetId);
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
                      peerSubnetSubscriptions.getSubscriberCountForSyncCommitteeSubnet(subnetId);
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
                      peerSubnetSubscriptions.getSubscriberCountForDataColumnSidecarSubnet(
                          subnetId);
                  return subscriberCountToScore.applyAsInt(subscriberCount);
                })
            .sum();

    final int executionProofSubnetScore =
        executionProofSubnetSubscriptions
            .map(
                executionProof ->
                    executionProof
                        .streamAllSetBits()
                        .filter(peerSubnetSubscriptions::isExecutionProofSubnetRelevant)
                        .map(
                            subnetId -> {
                              int subscriberCount =
                                  peerSubnetSubscriptions.getSubscriberCountForExecutionProofSubnet(
                                      subnetId);
                              return subscriberCountToScore.applyAsInt(subscriberCount);
                            })
                        .sum())
            .orElse(0);

    return attestationSubnetScore
        + syncCommitteeSubnetScore
        + dataColumnSidecarSubnetScore
        + executionProofSubnetScore;
  }

  private int scoreSubnetForExistingPeer(final int subscriberCount) {
    // The peer we're scoring is already included in the subscriberCount
    return scoreSubnetForCandidatePeer(Math.max(0, subscriberCount - 1));
  }

  private int scoreSubnetForCandidatePeer(final int numberOfOtherSubscribers) {
    final int value = numberOfOtherSubscribers + 1;
    return MAX_SUBNET_SCORE / (value * value);
  }
}
