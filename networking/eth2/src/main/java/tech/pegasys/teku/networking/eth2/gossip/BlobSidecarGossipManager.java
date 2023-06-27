/*
 * Copyright ConsenSys Software Inc., 2023
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
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Optional;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationMilestoneValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobSidecarGossipManager implements GossipManager {

  private final Spec spec;
  private final GossipNetwork gossipNetwork;
  private final GossipEncoding gossipEncoding;
  private final Int2ObjectMap<Eth2TopicHandler<SignedBlobSidecar>> subnetIdToTopicHandler;

  private final Int2ObjectMap<TopicChannel> subnetIdToChannel = new Int2ObjectOpenHashMap<>();

  public static BlobSidecarGossipManager create(
      final RecentChainData recentChainData,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final OperationProcessor<SignedBlobSidecar> processor) {
    final SpecVersion forkSpecVersion = spec.atEpoch(forkInfo.getFork().getEpoch());
    final SignedBlobSidecarSchema gossipType =
        SchemaDefinitionsDeneb.required(forkSpecVersion.getSchemaDefinitions())
            .getSignedBlobSidecarSchema();
    final Int2ObjectMap<Eth2TopicHandler<SignedBlobSidecar>> subnetIdToTopicHandler =
        new Int2ObjectOpenHashMap<>();
    final SpecConfigDeneb specConfigDeneb = SpecConfigDeneb.required(forkSpecVersion.getConfig());
    IntStream.range(0, specConfigDeneb.getBlobSidecarSubnetCount())
        .forEach(
            subnetId -> {
              final Eth2TopicHandler<SignedBlobSidecar> topicHandler =
                  createBlobSidecarTopicHandler(
                      subnetId,
                      recentChainData,
                      spec,
                      asyncRunner,
                      processor,
                      gossipEncoding,
                      forkInfo,
                      gossipType);
              subnetIdToTopicHandler.put(subnetId, topicHandler);
            });
    return new BlobSidecarGossipManager(
        spec, gossipNetwork, gossipEncoding, subnetIdToTopicHandler);
  }

  private BlobSidecarGossipManager(
      final Spec spec,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final Int2ObjectMap<Eth2TopicHandler<SignedBlobSidecar>> subnetIdToTopicHandler) {
    this.spec = spec;
    this.gossipNetwork = gossipNetwork;
    this.gossipEncoding = gossipEncoding;
    this.subnetIdToTopicHandler = subnetIdToTopicHandler;
  }

  public void publishBlobSidecar(final SignedBlobSidecar message) {
    final int subnetId = spec.computeSubnetForBlobSidecar(message).intValue();
    Optional.ofNullable(subnetIdToChannel.get(subnetId))
        .ifPresent(channel -> channel.gossip(gossipEncoding.encode(message)));
  }

  @VisibleForTesting
  Eth2TopicHandler<SignedBlobSidecar> getTopicHandler(final int subnetId) {
    return subnetIdToTopicHandler.get(subnetId);
  }

  @Override
  public void subscribe() {
    subnetIdToTopicHandler
        .int2ObjectEntrySet()
        .forEach(
            entry -> {
              final Eth2TopicHandler<SignedBlobSidecar> topicHandler = entry.getValue();
              final TopicChannel channel =
                  gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);
              subnetIdToChannel.put(entry.getIntKey(), channel);
            });
  }

  @Override
  public void unsubscribe() {
    subnetIdToChannel.values().forEach(TopicChannel::close);
    subnetIdToChannel.clear();
  }

  @Override
  public boolean isEnabledDuringOptimisticSync() {
    return true;
  }

  private static Eth2TopicHandler<SignedBlobSidecar> createBlobSidecarTopicHandler(
      final int subnetId,
      final RecentChainData recentChainData,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final OperationProcessor<SignedBlobSidecar> processor,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final SignedBlobSidecarSchema gossipType) {
    return new Eth2TopicHandler<>(
        recentChainData,
        asyncRunner,
        new TopicSubnetIdAwareOperationProcessor(spec, subnetId, processor),
        gossipEncoding,
        forkInfo.getForkDigest(spec),
        GossipTopicName.getBlobSidecarSubnetTopicName(subnetId),
        new OperationMilestoneValidator<>(
            spec,
            forkInfo.getFork(),
            blobSidecar -> spec.computeEpochAtSlot(blobSidecar.getBlobSidecar().getSlot())),
        gossipType,
        spec.getNetworkingConfig());
  }

  private static class TopicSubnetIdAwareOperationProcessor
      implements OperationProcessor<SignedBlobSidecar> {

    private final Spec spec;
    private final int subnetId;
    private final OperationProcessor<SignedBlobSidecar> delegate;

    private TopicSubnetIdAwareOperationProcessor(
        final Spec spec, final int subnetId, final OperationProcessor<SignedBlobSidecar> delegate) {
      this.spec = spec;
      this.subnetId = subnetId;
      this.delegate = delegate;
    }

    @Override
    public SafeFuture<InternalValidationResult> process(final SignedBlobSidecar blobSidecar) {
      final int blobSidecarSubnet = spec.computeSubnetForBlobSidecar(blobSidecar).intValue();
      if (blobSidecarSubnet != subnetId) {
        return SafeFuture.completedFuture(
            InternalValidationResult.reject(
                "blob sidecar with subnet_id %s does not match the topic subnet_id %d",
                blobSidecarSubnet, subnetId));
      }
      return delegate.process(blobSidecar);
    }
  }
}
