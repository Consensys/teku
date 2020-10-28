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

package tech.pegasys.teku.networking.eth2.gossip.topics.validation;

import static tech.pegasys.teku.core.ForkChoiceUtil.getCurrentSlot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_BEACON_PROPOSER;
import static tech.pegasys.teku.util.config.Constants.MAXIMUM_GOSSIP_CLOCK_DISPARITY;
import static tech.pegasys.teku.util.config.Constants.VALID_BLOCK_SET_SIZE;

import com.google.common.base.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.ForkChoiceUtil;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.ValidatorsUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final StateTransition stateTransition;
  private final Set<SlotAndProposer> receivedValidBlockInfoSet =
      LimitedSet.create(VALID_BLOCK_SET_SIZE);

  public BlockValidator(RecentChainData recentChainData, StateTransition stateTransition) {
    this.recentChainData = recentChainData;
    this.stateTransition = stateTransition;
  }

  public SafeFuture<InternalValidationResult> validate(SignedBeaconBlock block) {

    if (!blockSlotIsGreaterThanLatestFinalizedSlot(block)
        || !blockIsFirstBlockWithValidSignatureForSlot(block)) {
      LOG.trace(
          "BlockValidator: Block is either too old or is not the first block with valid signature for "
              + "its slot. It will be dropped");
      return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
    }

    if (blockIsFromFutureSlot(block)) {
      LOG.trace("BlockValidator: Block is from the future. It will be saved for future processing");
      return SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }

    if (recentChainData.containsBlock(block.getRoot())) {
      LOG.trace("Block is already imported");
      return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
    }

    if (!recentChainData.containsBlock(block.getParent_root())) {
      LOG.trace("Block parent is not availalbe. It will be saved for future processing");
      return SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }

    if (!currentFinalizedCheckpointIsAncestorOfBlock(block)) {
      LOG.trace("Block does not descend from finalized checkpoint");
      return SafeFuture.completedFuture(InternalValidationResult.REJECT);
    }

    return recentChainData
        .retrieveBlockState(block.getMessage().getParent_root())
        .thenApply(
            preState -> {
              if (preState.isEmpty()) {
                LOG.trace(
                    "BlockValidator: Block pre state does not exist. It will be saved for future processing");
                return InternalValidationResult.SAVE_FOR_FUTURE;
              }

              if (preState.get().getSlot().isGreaterThanOrEqualTo(block.getSlot())) {
                LOG.trace("Parent block is after child block.");
                return InternalValidationResult.REJECT;
              }

              try {
                BeaconState postState = getStateInBlockEpoch(block, preState.get());
                if (blockIsProposedByTheExpectedProposer(block, postState)
                    && blockSignatureIsValidWithRespectToProposerIndex(
                        block, preState.get(), postState)) {
                  return InternalValidationResult.ACCEPT;
                }
              } catch (EpochProcessingException | SlotProcessingException e) {
                LOG.error("BlockValidator: Unable to process block state.", e);
                return InternalValidationResult.REJECT;
              }

              return InternalValidationResult.REJECT;
            });
  }

  private BeaconState getStateInBlockEpoch(
      final SignedBeaconBlock block, final BeaconState preState)
      throws EpochProcessingException, SlotProcessingException {
    final UInt64 firstSlotInBlockEpoch =
        compute_start_slot_at_epoch(compute_epoch_at_slot(block.getSlot()));
    if (preState.getSlot().isGreaterThanOrEqualTo(firstSlotInBlockEpoch)) {
      return preState;
    }
    return stateTransition.process_slots(preState, firstSlotInBlockEpoch);
  }

  private boolean blockIsFromFutureSlot(SignedBeaconBlock block) {
    ReadOnlyStore store = recentChainData.getStore();
    final long disparityInSeconds = Math.round((float) MAXIMUM_GOSSIP_CLOCK_DISPARITY / 1000.0);
    final UInt64 maxOffset = UInt64.valueOf(disparityInSeconds);
    final UInt64 maxTime = store.getTime().plus(maxOffset);
    UInt64 maxCurrSlot = getCurrentSlot(maxTime, store.getGenesisTime());
    return block.getSlot().compareTo(maxCurrSlot) > 0;
  }

  private boolean blockSlotIsGreaterThanLatestFinalizedSlot(SignedBeaconBlock block) {
    UInt64 finalizedSlot = recentChainData.getStore().getFinalizedCheckpoint().getEpochStartSlot();
    return block.getSlot().compareTo(finalizedSlot) > 0;
  }

  private boolean blockIsFirstBlockWithValidSignatureForSlot(SignedBeaconBlock block) {
    return !receivedValidBlockInfoSet.contains(new SlotAndProposer(block));
  }

  private boolean blockSignatureIsValidWithRespectToProposerIndex(
      SignedBeaconBlock block, BeaconState preState, BeaconState postState) {
    final Bytes32 domain = get_domain(preState, DOMAIN_BEACON_PROPOSER);
    final Bytes signing_root = compute_signing_root(block.getMessage(), domain);
    final BLSSignature signature = block.getSignature();

    boolean signatureValid =
        ValidatorsUtil.getValidatorPubKey(postState, block.getMessage().getProposer_index())
            .map(publicKey -> BLS.verify(publicKey, signing_root, signature))
            .orElse(false);

    return signatureValid && receivedValidBlockInfoSet.add(new SlotAndProposer(block));
  }

  private boolean blockIsProposedByTheExpectedProposer(
      SignedBeaconBlock block, BeaconState postState) {
    final int proposerIndex = get_beacon_proposer_index(postState, block.getSlot());
    return proposerIndex == block.getMessage().getProposer_index().longValue();
  }

  private boolean currentFinalizedCheckpointIsAncestorOfBlock(SignedBeaconBlock block) {
    return ForkChoiceUtil.blockDescendsFromLatestFinalizedBlock(
        block.getMessage(),
        recentChainData.getStore(),
        recentChainData.getForkChoiceStrategy().orElseThrow());
  }

  private static class SlotAndProposer {
    private final UInt64 slot;
    private final UInt64 proposer_index;

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
