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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.spec.config.Constants.GOSSIP_MAX_SIZE;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class BlobSidecarGossipManagerTest {

  private final Spec spec = TestSpecFactory.createMainnetDeneb();
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @SuppressWarnings("unchecked")
  private final OperationProcessor<SignedBlobSidecar> processor = mock(OperationProcessor.class);

  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final TopicChannel topicChannel = mock(TopicChannel.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final ForkInfo forkInfo =
      new ForkInfo(spec.fork(UInt64.ZERO), dataStructureUtil.randomBytes32());

  private BlobSidecarGossipManager blobSidecarGossipManager;

  @BeforeEach
  public void setup() {
    storageSystem.chainUpdater().initializeGenesis();
    doReturn(topicChannel)
        .when(gossipNetwork)
        .subscribe(contains(GossipTopicName.BLOB_SIDECAR.toString()), any());
    blobSidecarGossipManager =
        new BlobSidecarGossipManager(
            storageSystem.recentChainData(),
            spec,
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            forkInfo,
            processor,
            GOSSIP_MAX_SIZE);
    blobSidecarGossipManager.subscribe();
  }

  @Test
  public void testGossipingBlobSidecar() {
    final SignedBlobSidecar blobSidecar = dataStructureUtil.randomSignedBlobSidecar();
    final Bytes serialized = gossipEncoding.encode(blobSidecar);

    blobSidecarGossipManager.publishBlobSidecar(blobSidecar);

    verify(topicChannel).gossip(serialized);
  }
}
