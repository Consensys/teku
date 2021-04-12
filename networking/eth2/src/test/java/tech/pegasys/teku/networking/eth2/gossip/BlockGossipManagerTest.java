/*
 * Copyright 2019 ConsenSys AG.
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

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockGossipManagerTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final TopicChannel topicChannel = mock(TopicChannel.class);

  @SuppressWarnings("unchecked")
  private final OperationProcessor<SignedBeaconBlock> processor = mock(OperationProcessor.class);

  private BlockGossipManager blockGossipManager;

  @BeforeEach
  public void setup() {
    doReturn(topicChannel)
        .when(gossipNetwork)
        .subscribe(contains(BlockGossipManager.TOPIC_NAME), any());
    blockGossipManager =
        new BlockGossipManager(
            spec,
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            dataStructureUtil.randomForkInfo(),
            processor);
  }

  @Test
  public void onBlockProposed() {
    // Should gossip new blocks received from event bus
    SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    Bytes serialized = gossipEncoding.encode(block);
    blockGossipManager.publishBlock(block);

    verify(topicChannel).gossip(serialized);
  }
}
