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

package tech.pegasys.artemis.networking.eth2.gossip.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.networking.eth2.gossip.events.GossipedBlockEvent;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.api.StorageUpdateChannel;
import tech.pegasys.artemis.storage.events.diskupdates.SuccessfulStorageUpdateResult;
import tech.pegasys.artemis.util.async.SafeFuture;

public class BlockTopicHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = mock(EventBus.class);
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);
  private final ChainStorageClient storageClient =
      ChainStorageClient.memoryOnlyClient(eventBus, storageUpdateChannel);
  private final BeaconChainUtil beaconChainUtil = BeaconChainUtil.create(2, storageClient);
  private final BlockTopicHandler topicHandler = new BlockTopicHandler(eventBus, storageClient);

  @BeforeEach
  public void setup() {
    when(storageUpdateChannel.onStorageUpdate(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                new SuccessfulStorageUpdateResult(Collections.emptySet(), Collections.emptySet())));
    beaconChainUtil.initializeStorage();
  }

  @Test
  public void handleMessage_validBlock() throws Exception {
    final UnsignedLong nextSlot = storageClient.getBestSlot().plus(UnsignedLong.ONE);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);
    Bytes serialized = SimpleOffsetSerializer.serialize(block);
    beaconChainUtil.setSlot(nextSlot);

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(true);
    verify(eventBus).post(new GossipedBlockEvent(block));
  }

  @Test
  public void handleMessage_validFutureBlock() throws Exception {
    final UnsignedLong nextSlot = storageClient.getBestSlot().plus(UnsignedLong.ONE);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);
    Bytes serialized = SimpleOffsetSerializer.serialize(block);
    beaconChainUtil.setSlot(storageClient.getBestSlot());

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(false);
    verify(eventBus).post(new GossipedBlockEvent(block));
  }

  @Test
  public void handleMessage_invalidBlock_unknownPreState() {
    SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    Bytes serialized = SimpleOffsetSerializer.serialize(block);

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(false);
    verify(eventBus).post(new GossipedBlockEvent(block));
  }

  @Test
  public void handleMessage_invalidBlock_invalidSSZ() {
    Bytes serialized = Bytes.fromHexString("0x1234");

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(false);
  }

  @Test
  public void handleMessage_invalidBlock_wrongProposer() throws Exception {
    final UnsignedLong nextSlot = storageClient.getBestSlot().plus(UnsignedLong.ONE);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlotFromInvalidProposer(nextSlot);
    Bytes serialized = SimpleOffsetSerializer.serialize(block);
    beaconChainUtil.setSlot(nextSlot);

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(false);
    verify(eventBus, never()).post(new GossipedBlockEvent(block));
  }
}
