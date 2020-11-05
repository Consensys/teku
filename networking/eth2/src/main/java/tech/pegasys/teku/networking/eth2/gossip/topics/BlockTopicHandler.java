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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.BlockValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class BlockTopicHandler extends Eth2TopicHandler.SimpleEth2TopicHandler<SignedBeaconBlock> {
  private static final Logger LOG = LogManager.getLogger();
  public static String TOPIC_NAME = "beacon_block";

  private final GossipEncoding gossipEncoding;
  private final Bytes4 forkDigest;
  private final BlockValidator blockValidator;
  private final GossipedItemConsumer<SignedBeaconBlock> gossipedBlockConsumer;

  public BlockTopicHandler(
      final AsyncRunner asyncRunner,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final BlockValidator blockValidator,
      final GossipedItemConsumer<SignedBeaconBlock> gossipedBlockConsumer) {
    super(asyncRunner);
    this.gossipEncoding = gossipEncoding;
    this.forkDigest = forkInfo.getForkDigest();
    this.blockValidator = blockValidator;
    this.gossipedBlockConsumer = gossipedBlockConsumer;
  }

  @Override
  public void processMessage(
      final SignedBeaconBlock block, InternalValidationResult internalValidationResult) {
    switch (internalValidationResult) {
      case REJECT:
      case IGNORE:
        LOG.trace("Received invalid message for topic: {}", this::getTopic);
        break;
      case SAVE_FOR_FUTURE:
        LOG.trace("Deferring message for topic: {}", this::getTopic);
        gossipedBlockConsumer.forward(block);
        break;
      case ACCEPT:
        gossipedBlockConsumer.forward(block);
        break;
      default:
        throw new UnsupportedOperationException(
            "Unexpected validation result: " + internalValidationResult);
    }
  }

  @Override
  public GossipEncoding getGossipEncoding() {
    return gossipEncoding;
  }

  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }

  @Override
  public Class<SignedBeaconBlock> getValueType() {
    return SignedBeaconBlock.class;
  }

  @Override
  public Bytes4 getForkDigest() {
    return forkDigest;
  }

  @Override
  protected SafeFuture<InternalValidationResult> validateData(final SignedBeaconBlock block) {
    return blockValidator.validate(block);
  }
}
