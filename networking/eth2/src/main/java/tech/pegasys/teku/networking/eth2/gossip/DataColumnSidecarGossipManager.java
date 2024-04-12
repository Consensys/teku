/*
 * Copyright Consensys Software Inc., 2023
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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationMilestoneValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.electra.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.electra.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DataColumnSidecarGossipManager implements GossipManager {

  private final Spec spec;
  private final GossipNetwork gossipNetwork;
  private final GossipEncoding gossipEncoding;
  private final Int2ObjectMap<Eth2TopicHandler<DataColumnSidecar>> subnetIdToTopicHandler;

  private final Int2ObjectMap<TopicChannel> subnetIdToChannel = new Int2ObjectOpenHashMap<>();

  public static DataColumnSidecarGossipManager create(
      final RecentChainData recentChainData,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final OperationProcessor<DataColumnSidecar> processor) {
    final SpecVersion forkSpecVersion = spec.atEpoch(forkInfo.getFork().getEpoch());
    final DataColumnSidecarSchema gossipType =
        SchemaDefinitionsElectra.required(forkSpecVersion.getSchemaDefinitions())
            .getDataColumnSidecarSchema();
    final Int2ObjectMap<Eth2TopicHandler<DataColumnSidecar>> subnetIdToTopicHandler =
        new Int2ObjectOpenHashMap<>();
    final SpecConfigElectra specConfigElectra = SpecConfigElectra.required(forkSpecVersion.getConfig());
    IntStream.range(0, specConfigElectra.getDataColumnSidecarSubnetCount())
        .forEach(
            subnetId -> {
              final Eth2TopicHandler<DataColumnSidecar> topicHandler =
                  createDataColumnSidecarTopicHandler(
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
    return new DataColumnSidecarGossipManager(
        spec, gossipNetwork, gossipEncoding, subnetIdToTopicHandler);
  }

  private DataColumnSidecarGossipManager(
      final Spec spec,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final Int2ObjectMap<Eth2TopicHandler<DataColumnSidecar>> subnetIdToTopicHandler) {
    this.spec = spec;
    this.gossipNetwork = gossipNetwork;
    this.gossipEncoding = gossipEncoding;
    this.subnetIdToTopicHandler = subnetIdToTopicHandler;
  }

  public void publishDataColumnSidecar(final DataColumnSidecar message) {
    final int subnetId = spec.computeSubnetForDataColumnSidecar(message).intValue();
    Optional.ofNullable(subnetIdToChannel.get(subnetId))
        .ifPresent(channel -> channel.gossip(gossipEncoding.encode(message)));
  }

  @VisibleForTesting
  Eth2TopicHandler<DataColumnSidecar> getTopicHandler(final int subnetId) {
    return subnetIdToTopicHandler.get(subnetId);
  }

  @Override
  public void subscribe() {
    subnetIdToTopicHandler
        .int2ObjectEntrySet()
        .forEach(
            entry -> {
              final Eth2TopicHandler<DataColumnSidecar> topicHandler = entry.getValue();
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

  private static Eth2TopicHandler<DataColumnSidecar> createDataColumnSidecarTopicHandler(
      final int subnetId,
      final RecentChainData recentChainData,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final OperationProcessor<DataColumnSidecar> processor,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final DataColumnSidecarSchema gossipType) {
    return new Eth2TopicHandler<>(
        recentChainData,
        asyncRunner,
        new TopicSubnetIdAwareOperationProcessor(spec, subnetId, processor),
        gossipEncoding,
        forkInfo.getForkDigest(spec),
        GossipTopicName.getDataColumnSidecarSubnetTopicName(subnetId),
        new OperationMilestoneValidator<>(
            spec,
            forkInfo.getFork(),
            dataColumnSidecar -> spec.computeEpochAtSlot(dataColumnSidecar.getSlot())),
        gossipType,
        spec.getNetworkingConfig());
  }

  private record TopicSubnetIdAwareOperationProcessor(
      Spec spec, int subnetId, OperationProcessor<DataColumnSidecar> delegate)
      implements OperationProcessor<DataColumnSidecar> {

    @Override
    public SafeFuture<InternalValidationResult> process(
        final DataColumnSidecar dataColumnSidecar, final Optional<UInt64> arrivalTimestamp) {
      final int dataColumnSidecarSubnet = spec.computeSubnetForDataColumnSidecar(dataColumnSidecar).intValue();
      if (dataColumnSidecarSubnet != subnetId) {
        return SafeFuture.completedFuture(
            InternalValidationResult.reject(
                "blob sidecar with subnet_id %s does not match the topic subnet_id %d",
                dataColumnSidecarSubnet, subnetId));
      }
      return delegate.process(dataColumnSidecar, arrivalTimestamp);
    }
  }
}
