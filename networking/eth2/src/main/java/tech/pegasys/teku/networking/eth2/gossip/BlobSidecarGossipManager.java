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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobSidecarGossipManager implements GossipManager {

  private final GossipNetwork gossipNetwork;
  private final GossipEncoding gossipEncoding;
  private final Int2ObjectMap<Eth2TopicHandler<SignedBlobSidecar>> indexToTopicHandler;

  private final Int2ObjectMap<TopicChannel> indexToChannel = new Int2ObjectOpenHashMap<>();

  public static BlobSidecarGossipManager create(
      final RecentChainData recentChainData,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final OperationProcessor<SignedBlobSidecar> processor,
      final int maxMessageSize) {
    final SpecVersion forkSpecVersion = spec.atEpoch(forkInfo.getFork().getEpoch());
    final int maxBlobsPerBlock =
        SpecConfigDeneb.required(forkSpecVersion.getConfig()).getMaxBlobsPerBlock();
    final SignedBlobSidecarSchema gossipType =
        SchemaDefinitionsDeneb.required(forkSpecVersion.getSchemaDefinitions())
            .getSignedBlobSidecarSchema();
    final Int2ObjectMap<Eth2TopicHandler<SignedBlobSidecar>> indexToTopicHandler =
        new Int2ObjectOpenHashMap<>();
    IntStream.range(0, maxBlobsPerBlock)
        .forEach(
            index -> {
              final Eth2TopicHandler<SignedBlobSidecar> topicHandler =
                  createBlobSidecarTopicHandler(
                      index,
                      recentChainData,
                      spec,
                      asyncRunner,
                      processor,
                      gossipEncoding,
                      forkInfo,
                      gossipType,
                      maxMessageSize);
              indexToTopicHandler.put(index, topicHandler);
            });
    return new BlobSidecarGossipManager(gossipNetwork, gossipEncoding, indexToTopicHandler);
  }

  private BlobSidecarGossipManager(
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final Int2ObjectMap<Eth2TopicHandler<SignedBlobSidecar>> indexToTopicHandler) {
    this.gossipNetwork = gossipNetwork;
    this.gossipEncoding = gossipEncoding;
    this.indexToTopicHandler = indexToTopicHandler;
  }

  public void publishBlobSidecar(final SignedBlobSidecar message) {
    final int index = message.getBlobSidecar().getIndex().intValue();
    Optional.ofNullable(indexToChannel.get(index))
        .ifPresent(channel -> channel.gossip(gossipEncoding.encode(message)));
  }

  @VisibleForTesting
  Eth2TopicHandler<SignedBlobSidecar> getTopicHandler(final int index) {
    return indexToTopicHandler.get(index);
  }

  @Override
  public void subscribe() {
    indexToTopicHandler
        .int2ObjectEntrySet()
        .forEach(
            entry -> {
              final Eth2TopicHandler<SignedBlobSidecar> topicHandler = entry.getValue();
              final TopicChannel channel =
                  gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);
              indexToChannel.put(entry.getIntKey(), channel);
            });
  }

  @Override
  public void unsubscribe() {
    indexToChannel.values().forEach(TopicChannel::close);
    indexToChannel.clear();
  }

  @Override
  public boolean isEnabledDuringOptimisticSync() {
    return true;
  }

  private static Eth2TopicHandler<SignedBlobSidecar> createBlobSidecarTopicHandler(
      final int index,
      final RecentChainData recentChainData,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final OperationProcessor<SignedBlobSidecar> processor,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final SignedBlobSidecarSchema gossipType,
      final int maxMessageSize) {
    return new Eth2TopicHandler<>(
        recentChainData,
        asyncRunner,
        new TopicIndexAwareOperationProcessor(index, processor),
        gossipEncoding,
        forkInfo.getForkDigest(spec),
        GossipTopicName.getBlobSidecarIndexTopicName(index),
        new OperationMilestoneValidator<>(
            spec,
            forkInfo.getFork(),
            blobSidecar -> spec.computeEpochAtSlot(blobSidecar.getBlobSidecar().getSlot())),
        gossipType,
        maxMessageSize);
  }

  private static class TopicIndexAwareOperationProcessor
      implements OperationProcessor<SignedBlobSidecar> {

    private final int topicIndex;
    private final OperationProcessor<SignedBlobSidecar> delegate;

    private TopicIndexAwareOperationProcessor(
        final int topicIndex, final OperationProcessor<SignedBlobSidecar> delegate) {
      this.topicIndex = topicIndex;
      this.delegate = delegate;
    }

    @Override
    public SafeFuture<InternalValidationResult> process(final SignedBlobSidecar blobSidecar) {
      final int blobSidecarIndex = blobSidecar.getBlobSidecar().getIndex().intValue();
      if (blobSidecarIndex != topicIndex) {
        return SafeFuture.completedFuture(
            InternalValidationResult.reject(
                "blob sidecar with index %d does not match the topic index %d",
                blobSidecarIndex, topicIndex));
      }
      return delegate.process(blobSidecar);
    }
  }
}
