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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.netty.buffer.ByteBuf;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.MockMessageApi;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class BlocksTopicHandlerTest {

  private final PubsubPublisherApi publisher = mock(PubsubPublisherApi.class);
  private final EventBus eventBus = spy(new EventBus());
  private final ChainStorageClient storageClient = new ChainStorageClient(eventBus);
  private final BeaconChainUtil beaconChainUtil = BeaconChainUtil.create(12, storageClient);

  private final BlocksTopicHandler blocksTopicHandler =
      new BlocksTopicHandler(publisher, eventBus, storageClient);

  ArgumentCaptor<ByteBuf> byteBufCaptor = ArgumentCaptor.forClass(ByteBuf.class);
  ArgumentCaptor<Topic> topicCaptor = ArgumentCaptor.forClass(Topic.class);

  @BeforeEach
  public void setup() {
    beaconChainUtil.initializeStorage();
    doReturn(CompletableFuture.completedFuture(null)).when(publisher).publish(any(), any());
    eventBus.register(blocksTopicHandler);
  }

  @Test
  public void onNewBlock() {
    // Should gossip new blocks received from event bus
    BeaconBlock block = DataStructureUtil.randomBeaconBlock(1, 100);
    Bytes serialized = SimpleOffsetSerializer.serialize(block);
    eventBus.post(block);

    verify(publisher).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getValue().array()).isEqualTo(serialized.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(topicCaptor.getValue()).isEqualTo(blocksTopicHandler.getTopic());
  }

  @Test
  public void accept_validBlock() throws Exception {
    final UnsignedLong nextSlot = storageClient.getBestSlot().plus(UnsignedLong.ONE);
    final BeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);
    Bytes serialized = SimpleOffsetSerializer.serialize(block);

    final MessageApi mockMessage = new MockMessageApi(serialized, blocksTopicHandler.getTopic());
    blocksTopicHandler.accept(mockMessage);

    verify(publisher).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getValue().array()).isEqualTo(serialized.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(topicCaptor.getValue()).isEqualTo(blocksTopicHandler.getTopic());
  }

  @Test
  public void accept_invalidBlock_random() {
    BeaconBlock block = DataStructureUtil.randomBeaconBlock(1, 100);
    Bytes serialized = SimpleOffsetSerializer.serialize(block);

    final MessageApi mockMessage = new MockMessageApi(serialized, blocksTopicHandler.getTopic());
    blocksTopicHandler.accept(mockMessage);

    verify(publisher, never()).publish(any(), any());
  }

  @Test
  public void accept_invalidBlock_badData() {
    Bytes serialized = Bytes.fromHexString("0x1234");

    final MessageApi mockMessage = new MockMessageApi(serialized, blocksTopicHandler.getTopic());
    blocksTopicHandler.accept(mockMessage);

    verify(publisher, never()).publish(any(), any());
  }

  @Test
  public void accept_invalidBlock_wrongProposer() throws Exception {
    final UnsignedLong nextSlot = storageClient.getBestSlot().plus(UnsignedLong.ONE);
    final BeaconBlock block = beaconChainUtil.createBlockAtSlotFromInvalidProposer(nextSlot);
    Bytes serialized = SimpleOffsetSerializer.serialize(block);

    final MessageApi mockMessage = new MockMessageApi(serialized, blocksTopicHandler.getTopic());
    blocksTopicHandler.accept(mockMessage);

    verify(publisher, never()).publish(any(), any());
  }
}
