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
import static org.mockito.Mockito.spy;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class BlockTopicHandlerTest {
  private final EventBus eventBus = spy(new EventBus());
  private final ChainStorageClient storageClient = new ChainStorageClient(eventBus);
  private final BeaconChainUtil beaconChainUtil = BeaconChainUtil.create(12, storageClient);
  private final BlocksTopicHandler topicHandler = new BlocksTopicHandler(eventBus, storageClient);

  @BeforeEach
  public void setup() {
    beaconChainUtil.initializeStorage();
  }

  @Test
  public void handleMessage_validBlock() throws Exception {
    final UnsignedLong nextSlot = storageClient.getBestSlot().plus(UnsignedLong.ONE);
    final BeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);
    Bytes serialized = SimpleOffsetSerializer.serialize(block);

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(true);
  }

  @Test
  public void handleMessage_invalidBlock_random() {
    BeaconBlock block = DataStructureUtil.randomBeaconBlock(1, 100);
    Bytes serialized = SimpleOffsetSerializer.serialize(block);

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(false);
  }

  @Test
  public void handleMessage_invalidBlock_badData() {
    Bytes serialized = Bytes.fromHexString("0x1234");

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(false);
  }

  @Test
  public void handleMessage_invalidBlock_wrongProposer() throws Exception {
    final UnsignedLong nextSlot = storageClient.getBestSlot().plus(UnsignedLong.ONE);
    final BeaconBlock block = beaconChainUtil.createBlockAtSlotFromInvalidProposer(nextSlot);
    Bytes serialized = SimpleOffsetSerializer.serialize(block);

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(false);
  }
}
