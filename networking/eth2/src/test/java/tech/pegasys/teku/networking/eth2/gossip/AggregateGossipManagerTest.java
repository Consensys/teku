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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.networking.eth2.gossip.topics.AggregateTopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AggregateGossipManagerTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = new EventBus();
  private final RecentChainData storageClient = MemoryOnlyRecentChainData.create(eventBus);
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final TopicChannel topicChannel = mock(TopicChannel.class);

  @BeforeEach
  public void setup() {
    doReturn(topicChannel).when(gossipNetwork).subscribe(eq(AggregateTopicHandler.TOPIC), any());
    new AggregateGossipManager(gossipNetwork, eventBus, storageClient);
  }

  @Test
  public void onNewAggregate() {
    final SignedAggregateAndProof aggregate = dataStructureUtil.randomSignedAggregateAndProof();
    final Bytes serialized = SimpleOffsetSerializer.serialize(aggregate);

    eventBus.post(aggregate);
    verify(topicChannel).gossip(serialized);
  }
}
