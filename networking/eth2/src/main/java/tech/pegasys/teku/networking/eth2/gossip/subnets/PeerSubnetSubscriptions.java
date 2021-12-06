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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.networking.eth2.SubnetSubscriptionService;
import tech.pegasys.teku.networking.eth2.peers.PeerScorer;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;

public class PeerSubnetSubscriptions {

  private final SubnetSubscriptions attestationSubnetSubscriptions;
  private final SubnetSubscriptions syncCommitteeSubnetSubscriptions;
  private final int targetSubnetSubscriberCount;

  private PeerSubnetSubscriptions(
      final SubnetSubscriptions attestationSubnetSubscriptions,
      final SubnetSubscriptions syncCommitteeSubnetSubscriptions,
      final int targetSubnetSubscriberCount) {
    this.attestationSubnetSubscriptions = attestationSubnetSubscriptions;
    this.syncCommitteeSubnetSubscriptions = syncCommitteeSubnetSubscriptions;
    this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
  }

  public static PeerSubnetSubscriptions create(
      final SchemaDefinitionsSupplier currentSchemaDefinitions,
      final GossipNetwork network,
      final AttestationSubnetTopicProvider attestationTopicProvider,
      final SyncCommitteeSubnetTopicProvider syncCommitteeSubnetTopicProvider,
      final SubnetSubscriptionService syncCommitteeSubnetService,
      final int targetSubnetSubscriberCount) {
    final Map<String, Collection<NodeId>> subscribersByTopic = network.getSubscribersByTopic();

    return builder(currentSchemaDefinitions)
        .targetSubnetSubscriberCount(targetSubnetSubscriberCount)
        .attestationSubnetSubscriptions(
            b ->
                // Track all attestation subnets
                streamAllAttestationSubnetIds(currentSchemaDefinitions)
                    .forEach(
                        attestationSubnet -> {
                          b.addRelevantSubnet(attestationSubnet);
                          subscribersByTopic
                              .getOrDefault(
                                  attestationTopicProvider.getTopicForSubnet(attestationSubnet),
                                  Collections.emptySet())
                              .forEach(
                                  subscriber -> b.addSubscriber(attestationSubnet, subscriber));
                        }))
        .syncCommitteeSubnetSubscriptions(
            b ->
                // Only track sync committee subnets that we're subscribed to
                syncCommitteeSubnetService
                    .getSubnets()
                    .forEach(
                        syncCommitteeSubnet -> {
                          b.addRelevantSubnet(syncCommitteeSubnet);
                          subscribersByTopic
                              .getOrDefault(
                                  syncCommitteeSubnetTopicProvider.getTopicForSubnet(
                                      syncCommitteeSubnet),
                                  Collections.emptySet())
                              .forEach(
                                  subscriber -> b.addSubscriber(syncCommitteeSubnet, subscriber));
                        }))
        .build();
  }

  private static IntStream streamAllAttestationSubnetIds(
      final SchemaDefinitionsSupplier currentSchemaDefinitions) {
    return IntStream.range(0, currentSchemaDefinitions.getAttnetsENRFieldSchema().getLength());
  }

  static Builder builder(final SchemaDefinitionsSupplier currentSchemaDefinitions) {
    return new Builder(currentSchemaDefinitions);
  }

  @VisibleForTesting
  static PeerSubnetSubscriptions createEmpty(
      final SchemaDefinitionsSupplier currentSchemaDefinitions) {
    return builder(currentSchemaDefinitions).build();
  }

  public int getSubscriberCountForAttestationSubnet(final int subnetId) {
    return attestationSubnetSubscriptions.getSubscriberCountForSubnet(subnetId);
  }

  public int getSubscriberCountForSyncCommitteeSubnet(final int subnetId) {
    return syncCommitteeSubnetSubscriptions.getSubscriberCountForSubnet(subnetId);
  }

  public SszBitvector getAttestationSubnetSubscriptions(final NodeId peerId) {
    return attestationSubnetSubscriptions.getSubnetSubscriptions(peerId);
  }

  public SszBitvector getSyncCommitteeSubscriptions(final NodeId peerId) {
    return syncCommitteeSubnetSubscriptions.getSubnetSubscriptions(peerId);
  }

  public boolean isSyncCommitteeSubnetRelevant(final int subnetId) {
    return syncCommitteeSubnetSubscriptions.isSubnetRelevant(subnetId);
  }

  public boolean isAttestationSubnetRelevant(final int subnetId) {
    return attestationSubnetSubscriptions.isSubnetRelevant(subnetId);
  }

  public PeerScorer createScorer() {
    return SubnetScorer.create(this);
  }

  public int getSubscribersRequired() {
    return getMinSubscriberCount().map(c -> Math.max(targetSubnetSubscriberCount - c, 0)).orElse(0);
  }

  private Optional<Integer> getMinSubscriberCount() {
    final Optional<Integer> minAttestationSubscribers =
        attestationSubnetSubscriptions.getMinSubscriberCount();
    final Optional<Integer> minSyncnetSubscribers =
        syncCommitteeSubnetSubscriptions.getMinSubscriberCount();
    if (minAttestationSubscribers.isPresent() && minSyncnetSubscribers.isPresent()) {
      return Optional.of(Math.min(minAttestationSubscribers.get(), minSyncnetSubscribers.get()));
    } else {
      return minAttestationSubscribers.or(() -> minSyncnetSubscribers);
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

  public static class SubnetSubscriptions {
    private final SszBitvectorSchema<?> subscriptionSchema;
    private final Set<Integer> relevantSubnets;
    private final Map<Integer, Integer> subscriberCountBySubnetId;
    private final Map<NodeId, SszBitvector> subscriptionsByPeer;

    private SubnetSubscriptions(
        final SszBitvectorSchema<?> subscriptionSchema,
        final Set<Integer> relevantSubnets,
        final Map<Integer, Integer> subscriberCountBySubnetId,
        final Map<NodeId, SszBitvector> subscriptionsByPeer) {
      this.subscriptionSchema = subscriptionSchema;
      this.relevantSubnets = relevantSubnets;
      this.subscriberCountBySubnetId = subscriberCountBySubnetId;
      this.subscriptionsByPeer = subscriptionsByPeer;
    }

    public static Builder builder(SszBitvectorSchema<?> subscriptionSchema) {
      return new Builder(subscriptionSchema);
    }

    public boolean isSubnetRelevant(final int subnetId) {
      return relevantSubnets.contains(subnetId);
    }

    private Stream<Integer> streamRelevantSubnets() {
      return relevantSubnets.stream();
    }

    /**
     * @return The minimum subscriber count across relevant subnets. Returns an empty value if is
     *     there are no relevant subnets.
     */
    public Optional<Integer> getMinSubscriberCount() {
      return streamRelevantSubnets().map(this::getSubscriberCountForSubnet).min(Integer::compare);
    }

    public int getSubscriberCountForSubnet(final int subnetId) {
      return subscriberCountBySubnetId.getOrDefault(subnetId, 0);
    }

    public SszBitvector getSubnetSubscriptions(final NodeId peerId) {
      return subscriptionsByPeer.getOrDefault(peerId, subscriptionSchema.getDefault());
    }

    public static class Builder {
      private final SszBitvectorSchema<?> subscriptionSchema;

      private final Set<Integer> relevantSubnets = new HashSet<>();
      private final Map<Integer, Integer> subscriberCountBySubnetId = new HashMap<>();
      private final Map<NodeId, SszBitvector> subscriptionsByPeer = new HashMap<>();

      private Builder(final SszBitvectorSchema<?> subscriptionSchema) {
        this.subscriptionSchema = subscriptionSchema;
      }

      public Builder addRelevantSubnet(final int subnetId) {
        relevantSubnets.add(subnetId);
        return this;
      }

      public Builder addSubscriber(final int subnetId, final NodeId peer) {
        subscriberCountBySubnetId.put(
            subnetId, subscriberCountBySubnetId.getOrDefault(subnetId, 0) + 1);
        subscriptionsByPeer.compute(
            peer,
            (__, existingVector) ->
                existingVector == null
                    ? subscriptionSchema.ofBits(subnetId)
                    : existingVector.withBit(subnetId));
        return this;
      }

      public SubnetSubscriptions build() {
        return new SubnetSubscriptions(
            subscriptionSchema, relevantSubnets, subscriberCountBySubnetId, subscriptionsByPeer);
      }
    }
  }

  public static class Builder {
    private final SubnetSubscriptions.Builder attestationSubnetSubscriptions;
    private final SubnetSubscriptions.Builder syncCommitteeSubnetSubscriptions;
    private int targetSubnetSubscriberCount = 2;

    private Builder(final SchemaDefinitionsSupplier currentSchemaDefinitions) {
      attestationSubnetSubscriptions =
          SubnetSubscriptions.builder(currentSchemaDefinitions.getAttnetsENRFieldSchema());
      syncCommitteeSubnetSubscriptions =
          SubnetSubscriptions.builder(currentSchemaDefinitions.getSyncnetsENRFieldSchema());
    }

    public PeerSubnetSubscriptions build() {
      return new PeerSubnetSubscriptions(
          attestationSubnetSubscriptions.build(),
          syncCommitteeSubnetSubscriptions.build(),
          targetSubnetSubscriberCount);
    }

    public Builder targetSubnetSubscriberCount(final Integer targetSubnetSubscriberCount) {
      checkNotNull(targetSubnetSubscriberCount);
      this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
      return this;
    }

    public Builder attestationSubnetSubscriptions(
        final Consumer<SubnetSubscriptions.Builder> consumer) {
      consumer.accept(attestationSubnetSubscriptions);
      return this;
    }

    public Builder syncCommitteeSubnetSubscriptions(
        final Consumer<SubnetSubscriptions.Builder> consumer) {
      consumer.accept(syncCommitteeSubnetSubscriptions);
      return this;
    }
  }
}
