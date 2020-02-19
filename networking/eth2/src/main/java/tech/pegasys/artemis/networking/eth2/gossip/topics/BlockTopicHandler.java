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

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.get_current_slot;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_PROPOSER;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZException;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconStateRead;
import tech.pegasys.artemis.datastructures.state.BeaconStateWrite;
import tech.pegasys.artemis.datastructures.state.ValidatorRead;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.networking.eth2.gossip.events.GossipedBlockEvent;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.bls.BLSVerify;

public class BlockTopicHandler extends Eth2TopicHandler<SignedBeaconBlock> {
  public static final String BLOCKS_TOPIC = "/eth2/beacon_block/ssz";
  private static final Logger LOG = LogManager.getLogger();
  private final ChainStorageClient chainStorageClient;
  private final EventBus eventBus;

  public BlockTopicHandler(final EventBus eventBus, final ChainStorageClient chainStorageClient) {
    super(eventBus);
    this.eventBus = eventBus;
    this.chainStorageClient = chainStorageClient;
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
    if (chainStorageClient.isPreGenesis()) {
      // We can't process blocks pre-genesis
      return false;
    }

    final BeaconStateRead preState =
        chainStorageClient.getStore().getBlockState(block.getMessage().getParent_root());
    if (preState == null) {
      // Post event even if we don't have the prestate
      eventBus.post(createEvent(block));
      return false;
    }

    if (!isBlockSignatureValid(block, preState)) {
      LOG.trace("Dropping gossiped block with invalid signature: {}", block);
      return false;
    }

    final UnsignedLong currentSlot = get_current_slot(chainStorageClient.getStore());
    if (block.getSlot().compareTo(currentSlot) > 0) {
      // Don't gossip future blocks
      eventBus.post(createEvent(block));
      return false;
    }

    return true;
  }

  private boolean isBlockSignatureValid(final SignedBeaconBlock block, final BeaconStateRead preState) {
    final StateTransition stateTransition = new StateTransition(false);
    final BeaconStateWrite postState = preState.createWritableCopy();

    try {
      stateTransition.process_slots(postState, block.getMessage().getSlot(), false);
    } catch (EpochProcessingException | SlotProcessingException e) {
      LOG.error("Unable to process block state.", e);
      return false;
    }

    final int proposerIndex = get_beacon_proposer_index(postState);
    final ValidatorRead proposer = postState.getValidators().get(proposerIndex);
    final Bytes domain = get_domain(preState, DOMAIN_BEACON_PROPOSER);
    final BLSSignature signature = block.getSignature();
    return BLSVerify.bls_verify(
        proposer.getPubkey(), block.getMessage().hash_tree_root(), signature, domain);
  }
}
