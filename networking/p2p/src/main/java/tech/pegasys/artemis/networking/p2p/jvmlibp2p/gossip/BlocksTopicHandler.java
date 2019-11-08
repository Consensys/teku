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

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_PROPOSER;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZException;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.bls.BLSVerify;

public class BlocksTopicHandler extends GossipTopicHandler<BeaconBlock> {
  private static final Logger LOG = LogManager.getLogger();
  private static final Topic BLOCKS_TOPIC = new Topic("/eth2/beacon_block/ssz");
  private final ChainStorageClient chainStorageClient;

  protected BlocksTopicHandler(
      final PubsubPublisherApi publisher,
      final EventBus eventBus,
      final ChainStorageClient chainStorageClient) {
    super(publisher, eventBus);
    this.chainStorageClient = chainStorageClient;
  }

  @Override
  public Topic getTopic() {
    return BLOCKS_TOPIC;
  }

  @Subscribe
  public void onNewBlock(final BeaconBlock block) {
    gossip(block);
  }

  @Override
  protected BeaconBlock deserialize(final Bytes bytes) throws SSZException {
    return SimpleOffsetSerializer.deserialize(bytes, BeaconBlock.class);
  }

  @Override
  protected boolean validateData(final BeaconBlock block) {
    final BeaconState preState =
        chainStorageClient.getStore().getBlockState(block.getParent_root());
    if (preState == null) {
      // TODO - handle future and unattached blocks
      LOG.warn(
          "Dropping block message at slot {} with unknown parent state {}",
          block.getSlot(),
          block.getParent_root());
      return false;
    }

    if (!isBlockSignatureValid(block, preState)) {
      LOG.trace("Dropping gossiped block with invalid signature: {}", block);
      return false;
    }

    return true;
  }

  private boolean isBlockSignatureValid(final BeaconBlock block, final BeaconState preState) {
    final StateTransition stateTransition = new StateTransition(false);
    final BeaconStateWithCache postState = BeaconStateWithCache.fromBeaconState(preState);

    try {
      stateTransition.process_slots(postState, block.getSlot(), false);
    } catch (EpochProcessingException | SlotProcessingException e) {
      LOG.error("Unable to process block state.", e);
      return false;
    }

    final int proposerIndex = get_beacon_proposer_index(postState);
    final Validator proposer = postState.getValidators().get(proposerIndex);
    final Bytes domain = get_domain(preState, DOMAIN_BEACON_PROPOSER);
    final BLSSignature signature = block.getSignature();
    return BLSVerify.bls_verify(
        proposer.getPubkey(), block.signing_root("signature"), signature, domain);
  }
}
