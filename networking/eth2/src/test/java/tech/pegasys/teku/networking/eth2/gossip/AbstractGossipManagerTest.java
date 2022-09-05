/*
 * Copyright ConsenSys Software Inc., 2022
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static tech.pegasys.teku.spec.config.Constants.GOSSIP_MAX_SIZE;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class AbstractGossipManagerTest {
  private static final GossipTopicName TOPIC_NAME = GossipTopicName.VOLUNTARY_EXIT;

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final TopicChannel topicChannel1 = mock(TopicChannel.class);
  private final TopicChannel topicChannel2 = mock(TopicChannel.class);
  private final ForkInfo forkInfo =
      new ForkInfo(spec.fork(UInt64.ZERO), dataStructureUtil.randomBytes32());

  @SuppressWarnings("unchecked")
  private final OperationProcessor<SszUInt64> processor = mock(OperationProcessor.class);

  private TestGossipManager gossipManager;

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
    doReturn(topicChannel1, topicChannel2)
        .when(gossipNetwork)
        .subscribe(contains(TOPIC_NAME.toString()), any());
    gossipManager =
        new TestGossipManager(
            storageSystem.recentChainData(),
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            forkInfo,
            processor,
            SszPrimitiveSchemas.UINT64_SCHEMA,
            GOSSIP_MAX_SIZE);
  }

  @Test
  void shouldSubscribeWhenSubscribedCalled() {
    gossipManager.subscribe();

    verify(gossipNetwork).subscribe(contains(TOPIC_NAME.toString()), any());
  }

  @Test
  void shouldBeSafeToUnsubscribeWhenNotSubscribed() {
    gossipManager.unsubscribe();

    verifyNoInteractions(gossipNetwork);
  }

  @Test
  void shouldCloseTopicChannelWhenUnsubscribing() {
    gossipManager.subscribe();
    gossipManager.unsubscribe();
    verify(topicChannel1).close();
  }

  @Test
  void shouldPublishMessageWhenSubscribed() {
    gossipManager.subscribe();

    final SszUInt64 message = wrap(1);
    gossipManager.publishMessage(message);

    verify(topicChannel1).gossip(gossipEncoding.encode(message));
  }

  @Test
  void shouldNotPublishMessageWhenUnsubscribed() {
    gossipManager.subscribe();
    gossipManager.unsubscribe();

    final SszUInt64 message = wrap(1);
    gossipManager.publishMessage(message);

    verify(topicChannel1, never()).gossip(any());
  }

  @Test
  void shouldNotPublishMessagesPriorToSubscribing() {
    gossipManager.publishMessage(wrap(1));

    verifyNoInteractions(topicChannel1, gossipNetwork);
  }

  @Test
  void shouldPublishMessagesAfterResubscribing() {
    gossipManager.subscribe();
    final SszUInt64 message1 = wrap(1);
    gossipManager.publishMessage(message1);
    verify(topicChannel1).gossip(gossipEncoding.encode(message1));

    gossipManager.unsubscribe();

    final SszUInt64 message2 = wrap(2);
    gossipManager.publishMessage(message2);
    verify(topicChannel1, never()).gossip(gossipEncoding.encode(message2));
    verify(topicChannel2, never()).gossip(gossipEncoding.encode(message2));

    gossipManager.subscribe();

    final SszUInt64 message3 = wrap(3);
    gossipManager.publishMessage(message3);
    verify(topicChannel2).gossip(gossipEncoding.encode(message3));
  }

  private SszUInt64 wrap(final int value) {
    return SszUInt64.of(UInt64.valueOf(value));
  }

  private static class TestGossipManager extends AbstractGossipManager<SszUInt64> {

    protected TestGossipManager(
        final RecentChainData recentChainData,
        final AsyncRunner asyncRunner,
        final GossipNetwork gossipNetwork,
        final GossipEncoding gossipEncoding,
        final ForkInfo forkInfo,
        final OperationProcessor<SszUInt64> processor,
        final SszSchema<SszUInt64> gossipType,
        final int maxMessageSize) {
      super(
          recentChainData,
          TOPIC_NAME,
          asyncRunner,
          gossipNetwork,
          gossipEncoding,
          forkInfo,
          processor,
          gossipType,
          Optional.empty(),
          maxMessageSize);
    }
  }
}
