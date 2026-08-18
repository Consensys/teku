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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarSchemaFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.gloas.DataColumnSidecarSchemaGloas;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

/**
 * Verifies that the DataColumnSidecar gossip schema is resolved from the fork the subscription
 * belongs to and not from the highest supported milestone. On a network with Gloas scheduled, Fulu
 * era topics must still decode sidecars using the Fulu schema.
 */
public class DataColumnSidecarSubnetSubscriptionsTest {

  private static final UInt64 FULU_FORK_EPOCH = UInt64.ZERO;
  private static final UInt64 GLOAS_FORK_EPOCH = UInt64.valueOf(2);
  private static final int SUBNET_ID = 1;

  private final Spec spec = TestSpecFactory.createMinimalWithGloasForkEpoch(GLOAS_FORK_EPOCH);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
  private final RecentChainData recentChainData = storageSystem.recentChainData();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;

  @SuppressWarnings("unchecked")
  private final OperationProcessor<DataColumnSidecar> processor = mock(OperationProcessor.class);

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
    when(gossipNetwork.subscribe(any(), any())).thenReturn(mock(TopicChannel.class));
  }

  @Test
  void shouldUseFuluSchemaForFuluForkSubscription() {
    assertThat(createSubnetSubscriptions(FULU_FORK_EPOCH).getDataColumnSidecarSchema())
        .isInstanceOf(DataColumnSidecarSchemaFulu.class);
  }

  @Test
  void shouldUseGloasSchemaForGloasForkSubscription() {
    assertThat(createSubnetSubscriptions(GLOAS_FORK_EPOCH).getDataColumnSidecarSchema())
        .isInstanceOf(DataColumnSidecarSchemaGloas.class);
  }

  @Test
  void shouldDecodeFuluSidecarOnFuluForkSubscription() {
    final DataColumnSidecar sidecar = randomDataColumnSidecar(FULU_FORK_EPOCH);

    assertThat(handleMessage(createSubnetSubscriptions(FULU_FORK_EPOCH), sidecar))
        .isCompletedWithValue(ValidationResult.Valid);
    verify(processor).process(eq(sidecar), any());
  }

  @Test
  void shouldDecodeGloasSidecarOnGloasForkSubscription() {
    final DataColumnSidecar sidecar = randomDataColumnSidecar(GLOAS_FORK_EPOCH);

    assertThat(handleMessage(createSubnetSubscriptions(GLOAS_FORK_EPOCH), sidecar))
        .isCompletedWithValue(ValidationResult.Valid);
    verify(processor).process(eq(sidecar), any());
  }

  private DataColumnSidecarSubnetSubscriptions createSubnetSubscriptions(final UInt64 forkEpoch) {
    final ForkInfo forkInfo = recentChainData.getForkInfo(forkEpoch).orElseThrow();
    final Bytes4 forkDigest = recentChainData.getForkDigest(forkEpoch);
    final DataColumnSidecarSubnetSubscriptions subnetSubscriptions =
        new DataColumnSidecarSubnetSubscriptions(
            spec,
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            recentChainData,
            processor,
            DebugDataDumper.NOOP,
            forkInfo,
            forkDigest);
    subnetSubscriptions.subscribe();
    return subnetSubscriptions;
  }

  private DataColumnSidecar randomDataColumnSidecar(final UInt64 forkEpoch) {
    final SignedBeaconBlockHeader blockHeader =
        dataStructureUtil.randomSignedBeaconBlockHeader(spec.computeStartSlotAtEpoch(forkEpoch));
    return dataStructureUtil.randomDataColumnSidecar(blockHeader, UInt64.valueOf(SUBNET_ID));
  }

  @SuppressWarnings("unchecked")
  private SafeFuture<ValidationResult> handleMessage(
      final DataColumnSidecarSubnetSubscriptions subnetSubscriptions,
      final DataColumnSidecar sidecar) {
    when(processor.process(any(), any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    subnetSubscriptions.subscribeToSubnetId(SUBNET_ID);
    final Eth2TopicHandler<DataColumnSidecar> topicHandler =
        (Eth2TopicHandler<DataColumnSidecar>)
            subnetSubscriptions.getSubscribedTopicHandler(SUBNET_ID);
    final Bytes serialized = gossipEncoding.encode(sidecar);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    return result;
  }
}
