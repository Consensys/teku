/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.networking.eth2.gossip.topics.validation;

import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.core.ForkChoiceUtil.getCurrentSlot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_PROPOSER;
import static tech.pegasys.artemis.util.config.Constants.MAXIMUM_GOSSIP_CLOCK_DISPARITY;
import static tech.pegasys.artemis.util.config.Constants.VALID_BLOCK_SET_SIZE;

import com.google.common.base.Objects;
import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.bls.BLS;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.core.StateTransition;
import tech.pegasys.artemis.core.exceptions.EpochProcessingException;
import tech.pegasys.artemis.core.exceptions.SlotProcessingException;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.util.collections.ConcurrentLimitedSet;
import tech.pegasys.artemis.util.collections.LimitStrategy;

public class BlockValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final StateTransition stateTransition;
  private final Set<SlotAndProposer> receivedValidBlockInfoSet =
      ConcurrentLimitedSet.create(VALID_BLOCK_SET_SIZE, LimitStrategy.DROP_OLDEST_ELEMENT);

  public BlockValidator(RecentChainData recentChainData, StateTransition stateTransition) {
    this.recentChainData = recentChainData;
    this.stateTransition = stateTransition;
  }

  public BlockValidationResult validate(SignedBeaconBlock block) {

    if (!blockSlotIsGreaterThanLatestFinalizedSlot(block)
        || !blockIsFirstBlockWithValidSignatureForSlot(block)) {
      LOG.trace(
          "BlockValidator: Block is either too old or is not the first block with valid signature for "
              + "its slot. It will be dropped");
      return BlockValidationResult.INVALID;
    }

    final Optional<BeaconState> preState =
        Optional.ofNullable(
            recentChainData.getStore().getBlockState(block.getMessage().getParent_root()));
    if (preState.isEmpty() || blockIsFromFutureSlot(block)) {
      LOG.trace(
          "BlockValidator: Either block pre state does not exist or block is from the future. "
              + "It will be saved for future processing");
      return BlockValidationResult.SAVED_FOR_FUTURE;
    }

    try {
      BeaconState postState =
          stateTransition.process_slots(preState.get(), block.getMessage().getSlot());
      if (blockIsProposedByTheExpectedProposer(block, postState)
          && blockSignatureIsValidWithRespectToProposerIndex(block, preState.get(), postState)) {
        return BlockValidationResult.VALID;
      }
    } catch (EpochProcessingException | SlotProcessingException e) {
      LOG.error("BlockValidator: Unable to process block state.", e);
      return BlockValidationResult.INVALID;
    }

    return BlockValidationResult.INVALID;
  }

  private boolean blockIsFromFutureSlot(SignedBeaconBlock block) {
    ReadOnlyStore store = recentChainData.getStore();
    final long disparityInSeconds = Math.round((float) MAXIMUM_GOSSIP_CLOCK_DISPARITY / 1000.0);
    final UnsignedLong maxOffset = UnsignedLong.valueOf(disparityInSeconds);
    final UnsignedLong maxTime = store.getTime().plus(maxOffset);
    UnsignedLong maxCurrSlot = getCurrentSlot(maxTime, store.getGenesisTime());
    return block.getSlot().compareTo(maxCurrSlot) > 0;
  }

  private boolean blockSlotIsGreaterThanLatestFinalizedSlot(SignedBeaconBlock block) {
    UnsignedLong finalizedSlot =
        recentChainData.getStore().getFinalizedCheckpoint().getEpochStartSlot();
    return block.getSlot().compareTo(finalizedSlot) > 0;
  }

  private boolean blockIsFirstBlockWithValidSignatureForSlot(SignedBeaconBlock block) {
    return !receivedValidBlockInfoSet.contains(new SlotAndProposer(block));
  }

  private boolean blockSignatureIsValidWithRespectToProposerIndex(
      SignedBeaconBlock block, BeaconState preState, BeaconState postState) {
    Validator proposer =
        postState
            .getValidators()
            .get(toIntExact(block.getMessage().getProposer_index().longValue()));

    final Bytes domain = get_domain(preState, DOMAIN_BEACON_PROPOSER);
    final Bytes signing_root = compute_signing_root(block.getMessage(), domain);
    final BLSSignature signature = block.getSignature();
    boolean signatureValid = BLS.verify(proposer.getPubkey(), signing_root, signature);

    return signatureValid && receivedValidBlockInfoSet.add(new SlotAndProposer(block));
  }

  private boolean blockIsProposedByTheExpectedProposer(
      SignedBeaconBlock block, BeaconState postState) {
    final int proposerIndex = get_beacon_proposer_index(postState);
    return proposerIndex == toIntExact(block.getMessage().getProposer_index().longValue());
  }

  private static class SlotAndProposer {
    private final UnsignedLong slot;
    private final UnsignedLong proposer_index;

    public SlotAndProposer(SignedBeaconBlock block) {
      this.slot = block.getSlot();
      this.proposer_index = block.getMessage().getProposer_index();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SlotAndProposer)) return false;
      SlotAndProposer that = (SlotAndProposer) o;
      return Objects.equal(slot, that.slot) && Objects.equal(proposer_index, that.proposer_index);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(slot, proposer_index);
    }
  }
}
