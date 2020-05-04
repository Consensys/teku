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

import com.google.common.eventbus.EventBus;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZException;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.ForkInfo;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.networking.eth2.gossip.events.GossipedBlockEvent;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.BlockValidator;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.ValidationResult;

public class BlockTopicHandler extends Eth2TopicHandler<SignedBeaconBlock> {
  public static String TOPIC_NAME = "beacon_block";
  private final BlockValidator blockValidator;

  public BlockTopicHandler(
      final EventBus eventBus, final BlockValidator blockValidator, final ForkInfo forkInfo) {
    super(eventBus, forkInfo);
    this.blockValidator = blockValidator;
  }

  @Override
  protected Object createEvent(final SignedBeaconBlock block) {
    return new GossipedBlockEvent(block);
  }

  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }

  @Override
  protected SignedBeaconBlock deserialize(final Bytes bytes) throws SSZException {
    return SimpleOffsetSerializer.deserialize(bytes, SignedBeaconBlock.class);
  }

  @Override
  protected ValidationResult validateData(final SignedBeaconBlock block) {
    return blockValidator.validate(block);
  }
}
