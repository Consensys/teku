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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import io.libp2p.core.pubsub.ValidationResult;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.Eth2GossipMessage;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.BlockValidator;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockTopicHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = mock(EventBus.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(eventBus);
  private final BlockValidator blockValidator =
      new BlockValidator(recentChainData, new StateTransition());
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final BeaconChainUtil beaconChainUtil = BeaconChainUtil.create(2, recentChainData);

  @SuppressWarnings("unchecked")
  private final GossipedItemConsumer<SignedBeaconBlock> gossipedBlockConsumer =
      mock(GossipedItemConsumer.class);

  private BlockTopicHandler topicHandler =
      new BlockTopicHandler(
          asyncRunner,
          gossipEncoding,
          dataStructureUtil.randomForkInfo(),
          blockValidator,
          gossipedBlockConsumer);

  @BeforeEach
  public void setup() {
    beaconChainUtil.initializeStorage();
  }

  private Eth2GossipMessage createMessageStub(Bytes decompressedPayload) {
    return new Eth2GossipMessage("/test/topic", Bytes.EMPTY, () -> decompressedPayload);
  }

  @Test
  public void handleMessage_validBlock() throws Exception {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(UInt64.ONE);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);
    Bytes serialized = gossipEncoding.encode(block);
    beaconChainUtil.setSlot(nextSlot);

    final SafeFuture<ValidationResult> result = topicHandler
        .handleMessage(createMessageStub(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
    verify(gossipedBlockConsumer).forward(block);
  }

  @Test
  public void handleMessage_validFutureBlock() throws Exception {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(UInt64.ONE);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);
    Bytes serialized = gossipEncoding.encode(block);
    beaconChainUtil.setSlot(recentChainData.getHeadSlot());

    final SafeFuture<ValidationResult> result = topicHandler
        .handleMessage(createMessageStub(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
    verify(gossipedBlockConsumer).forward(block);
  }

  @Test
  public void handleMessage_invalidBlock_unknownPreState() {
    SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    Bytes serialized = gossipEncoding.encode(block);

    final SafeFuture<ValidationResult> result = topicHandler
        .handleMessage(createMessageStub(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
    verify(gossipedBlockConsumer).forward(block);
  }

  @Test
  public void handleMessage_invalidBlock_invalidSSZ() {
    Bytes serialized = Bytes.fromHexString("0x1234");

    final SafeFuture<ValidationResult> result = topicHandler
        .handleMessage(createMessageStub(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_invalidBlock_wrongProposer() throws Exception {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(UInt64.ONE);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlotFromInvalidProposer(nextSlot);
    Bytes serialized = gossipEncoding.encode(block);
    beaconChainUtil.setSlot(nextSlot);

    final SafeFuture<ValidationResult> result = topicHandler
        .handleMessage(createMessageStub(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
    verify(gossipedBlockConsumer, never()).forward(block);
  }

  @Test
  public void returnProperTopicName() {
    final Bytes4 forkDigest = Bytes4.fromHexString("0x11223344");
    final ForkInfo forkInfo = mock(ForkInfo.class);
    when(forkInfo.getForkDigest()).thenReturn(forkDigest);
    final BlockTopicHandler topicHandler =
        new BlockTopicHandler(
            asyncRunner, gossipEncoding, forkInfo, blockValidator, gossipedBlockConsumer);
    assertThat(topicHandler.getTopic()).isEqualTo("/eth2/11223344/beacon_block/ssz_snappy");
  }
}
