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

import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import tech.pegasys.teku.networking.eth2.peers.PeerScorer;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.ssz.collections.SszBitvector;
import tech.pegasys.teku.ssz.schema.collections.SszBitvectorSchema;

public class PeerSubnetSubscriptions {

  public static final SszBitvectorSchema<?> SUBNET_SUBSCRIPTIONS_SCHEMA =
      SszBitvectorSchema.create(ATTESTATION_SUBNET_COUNT);

  private final Map<Integer, Integer> subscriberCountBySubnetId;
  private final Map<NodeId, SszBitvector> subscriptionsByPeer;
  private final int targetSubnetSubscriberCount;

  private PeerSubnetSubscriptions(
      final Map<Integer, Integer> subscriberCountBySubnetId,
      final Map<NodeId, SszBitvector> subscriptionsByPeer,
      final int targetSubnetSubscriberCount) {
    this.subscriberCountBySubnetId = subscriberCountBySubnetId;
    this.subscriptionsByPeer = subscriptionsByPeer;
    this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
  }

  public static PeerSubnetSubscriptions create(
      final GossipNetwork network,
      final AttestationSubnetTopicProvider topicProvider,
      final int targetSubnetSubscriberCount) {
    final PeerSubnetSubscriptions.Builder builder = new PeerSubnetSubscriptions.Builder();
    final Map<String, Collection<NodeId>> subscribersByTopic = network.getSubscribersByTopic();
    streamSubnetIds()
        .forEach(
            subnetId ->
                subscribersByTopic
                    .getOrDefault(topicProvider.getTopicForSubnet(subnetId), Collections.emptySet())
                    .forEach(subscriber -> builder.addSubscriber(subnetId, subscriber)));
    return builder.targetSubnetSubscriberCount(targetSubnetSubscriberCount).build();
  }

  public int getSubscriberCountForSubnet(final int subnetId) {
    return subscriberCountBySubnetId.getOrDefault(subnetId, 0);
  }

  public SszBitvector getSubscriptionsForPeer(final NodeId peerId) {
    return subscriptionsByPeer.getOrDefault(peerId, SUBNET_SUBSCRIPTIONS_SCHEMA.getDefault());
  }

  public PeerScorer createScorer() {
    return AttestationSubnetScorer.create(this);
  }

  public int getSubscribersRequired() {
    return targetSubnetSubscriberCount
        - streamSubnetIds().map(this::getSubscriberCountForSubnet).min().orElse(0);
  }

  private static IntStream streamSubnetIds() {
    return IntStream.range(0, ATTESTATION_SUBNET_COUNT);
  }

  @VisibleForTesting
  static class Builder {
    private final Map<Integer, Integer> subscriberCountBySubnetId = new HashMap<>();

    private final Map<NodeId, SszBitvector> subscriptionsByPeer = new HashMap<>();
    private int targetSubnetSubscriberCount = 2;

    public Builder addSubscriber(final int subnetId, final NodeId peer) {
      subscriberCountBySubnetId.put(
          subnetId, subscriberCountBySubnetId.getOrDefault(subnetId, 0) + 1);
      subscriptionsByPeer.compute(
          peer,
          (__, existingVector) ->
              existingVector == null
                  ? SUBNET_SUBSCRIPTIONS_SCHEMA.ofBits(subnetId)
                  : existingVector.withBit(subnetId));
      return this;
    }

    public Builder targetSubnetSubscriberCount(final int targetSubnetSubscriberCount) {
      this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
      return this;
    }

    public PeerSubnetSubscriptions build() {
      return new PeerSubnetSubscriptions(
          subscriberCountBySubnetId, subscriptionsByPeer, targetSubnetSubscriberCount);
    }
  }

  public interface Factory {

    /**
     * Creates a new PeerSubnetSubscriptions which reports the subscriptions from the supplied
     * network at time of creation.
     *
     * @param gossipNetwork the network to load subscriptions from
     * @return the new PeerSubnetSubscriptions
     */
    PeerSubnetSubscriptions create(GossipNetwork gossipNetwork);
  }
}
