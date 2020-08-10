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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import java.util.function.IntUnaryOperator;
import tech.pegasys.teku.networking.eth2.peers.PeerScorer;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;

public class AttestationSubnetScorer implements PeerScorer {
  private static final int MAX_SUBNET_SCORE = 1000;
  private final PeerSubnetSubscriptions peerSubnetSubscriptions;

  private AttestationSubnetScorer(final PeerSubnetSubscriptions peerSubnetSubscriptions) {
    this.peerSubnetSubscriptions = peerSubnetSubscriptions;
  }

  public static AttestationSubnetScorer create(final PeerSubnetSubscriptions peerSubscriptions) {
    return new AttestationSubnetScorer(peerSubscriptions);
  }

  public static AttestationSubnetScorer create(
      final GossipNetwork gossipNetwork,
      final AttestationSubnetTopicProvider topicProvider,
      final int targetSubnetSubscriberCount) {
    return new AttestationSubnetScorer(
        PeerSubnetSubscriptions.create(gossipNetwork, topicProvider, targetSubnetSubscriberCount));
  }

  @Override
  public int scoreExistingPeer(final NodeId peerId) {
    final Bitvector subscriptions = peerSubnetSubscriptions.getSubscriptionsForPeer(peerId);
    return subscriptions != null ? score(subscriptions, this::scoreSubnetForExistingPeer) : 0;
  }

  @Override
  public int scoreCandidatePeer(final Bitvector subscriptions) {
    return score(subscriptions, this::scoreSubnetForCandidatePeer);
  }

  private int score(
      final Bitvector subnetSubscriptions, final IntUnaryOperator subscriberCountToScore) {
    return subnetSubscriptions
        .streamAllSetBits()
        .map(
            subnetId -> {
              int subscriberCount = peerSubnetSubscriptions.getSubscriberCountForSubnet(subnetId);
              return subscriberCountToScore.applyAsInt(subscriberCount);
            })
        .sum();
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
