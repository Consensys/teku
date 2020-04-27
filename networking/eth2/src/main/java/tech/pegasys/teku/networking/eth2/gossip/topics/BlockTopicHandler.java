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

import com.google.common.eventbus.EventBus;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZException;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.networking.eth2.gossip.events.GossipedBlockEvent;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.BlockValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult;

public class BlockTopicHandler extends Eth2TopicHandler<SignedBeaconBlock> {
  public static final String BLOCKS_TOPIC = "/eth2/beacon_block/ssz";
  private final EventBus eventBus;
  private final BlockValidator blockValidator;

  public BlockTopicHandler(final EventBus eventBus, final BlockValidator blockValidator) {
    super(eventBus);
    this.eventBus = eventBus;
    this.blockValidator = blockValidator;
  }

  @Override
  protected Object createEvent(final SignedBeaconBlock block) {
    return new GossipedBlockEvent(block);
  }

  @Override
  public String getTopic() {
    return BLOCKS_TOPIC;
  }

  @Override
  protected SignedBeaconBlock deserialize(final Bytes bytes) throws SSZException {
    return SimpleOffsetSerializer.deserialize(bytes, SignedBeaconBlock.class);
  }

  @Override
  protected boolean validateData(final SignedBeaconBlock block) {
    ValidationResult validationResult = blockValidator.validate(block);
    switch (validationResult) {
      case INVALID:
        return false;
      case SAVED_FOR_FUTURE:
        eventBus.post(createEvent(block));
        return false;
      case VALID:
        return true;
      default:
        throw new UnsupportedOperationException(
            "BlockTopicHandler: Unexpected block validation result: " + validationResult);
    }
  }
}
