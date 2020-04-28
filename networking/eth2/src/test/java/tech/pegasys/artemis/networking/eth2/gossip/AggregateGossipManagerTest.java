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

package tech.pegasys.artemis.networking.eth2.gossip;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.networking.eth2.gossip.topics.AggregateTopicHandler;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.SignedAggregateAndProofValidator;
import tech.pegasys.artemis.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.artemis.networking.p2p.gossip.TopicChannel;
import tech.pegasys.artemis.ssz.SSZTypes.Bytes4;

public class AggregateGossipManagerTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = new EventBus();
  private final SignedAggregateAndProofValidator validator =
      mock(SignedAggregateAndProofValidator.class);
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final TopicChannel topicChannel = mock(TopicChannel.class);

  @BeforeEach
  public void setup() {
    doReturn(topicChannel)
        .when(gossipNetwork)
        .subscribe(contains(AggregateTopicHandler.TOPIC_NAME), any());
    new AggregateGossipManager(gossipNetwork, eventBus, validator, Bytes4.rightPad(Bytes.EMPTY));
  }

  @Test
  public void onNewAggregate() {
    final SignedAggregateAndProof aggregate = dataStructureUtil.randomSignedAggregateAndProof();
    final Bytes serialized = SimpleOffsetSerializer.serialize(aggregate);

    eventBus.post(aggregate);
    verify(topicChannel).gossip(serialized);
  }
}
