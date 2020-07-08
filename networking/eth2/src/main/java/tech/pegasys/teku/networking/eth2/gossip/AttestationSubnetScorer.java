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

package tech.pegasys.teku.networking.eth2.gossip;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;
import tech.pegasys.teku.networking.p2p.connection.PeerScorer;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.util.config.Constants;

public class AttestationSubnetScorer implements PeerScorer {
  private static final int MAX_SUBNET_SCORE = 1000;
  private final Map<Integer, Integer> subscriberCountBySubnetId;
  private final Map<NodeId, Bitvector> subscriptionsByPeer;

  private AttestationSubnetScorer(
      final Map<Integer, Integer> subscriberCountBySubnetId,
      final Map<NodeId, Bitvector> subscriptionsByPeer) {
    this.subscriberCountBySubnetId = subscriberCountBySubnetId;
    this.subscriptionsByPeer = subscriptionsByPeer;
  }

  public static AttestationSubnetScorer create(
      final GossipNetwork gossipNetwork, final AttestationSubnetTopicProvider topicProvider) {
    final AttestationSubnetScorer.Builder builder = new AttestationSubnetScorer.Builder();
    final Map<String, Collection<NodeId>> allSubscriptions = gossipNetwork.getSubscribersByTopic();
    IntStream.range(0, Constants.ATTESTATION_SUBNET_COUNT)
        .forEach(
            subnetId ->
                allSubscriptions
                    .getOrDefault(topicProvider.getTopicForSubnet(subnetId), Collections.emptySet())
                    .forEach(subscriber -> builder.addSubscriber(subnetId, subscriber)));
    return builder.build();
  }

  @Override
  public int scoreExistingPeer(final NodeId peerId) {
    final Bitvector subscriptions = subscriptionsByPeer.get(peerId);
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
              int subscriberCount = subscriberCountBySubnetId.getOrDefault(subnetId, 0);
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

  public interface AttestationSubnetTopicProvider {
    String getTopicForSubnet(int subnetId);
  }

  @VisibleForTesting
  static class Builder {
    private final Map<Integer, Integer> subscriberCountBySubnetId = new HashMap<>();

    private final Map<NodeId, Bitvector> subscriptionsByPeer = new HashMap<>();

    public Builder addSubscriber(final int subnetId, final NodeId peer) {
      subscriberCountBySubnetId.put(
          subnetId, subscriberCountBySubnetId.getOrDefault(subnetId, 0) + 1);
      subscriptionsByPeer
          .computeIfAbsent(peer, __ -> new Bitvector(Constants.ATTESTATION_SUBNET_COUNT))
          .setBit(subnetId);
      return this;
    }

    public AttestationSubnetScorer build() {
      return new AttestationSubnetScorer(subscriberCountBySubnetId, subscriptionsByPeer);
    }
  }
}
