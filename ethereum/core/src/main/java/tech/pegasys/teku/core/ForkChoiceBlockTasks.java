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

package tech.pegasys.teku.core;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.core.ForkChoiceUtil.compute_slots_since_epoch_start;
import static tech.pegasys.teku.core.ForkChoiceUtil.get_ancestor;
import static tech.pegasys.teku.core.ForkChoiceUtil.get_current_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root_at_slot;
import static tech.pegasys.teku.util.config.Constants.GENESIS_SLOT;
import static tech.pegasys.teku.util.config.Constants.SAFE_SLOTS_TO_UPDATE_JUSTIFIED;

import java.util.Optional;
import javax.annotation.CheckReturnValue;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.lookup.IndexedAttestationProvider;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ForkChoiceBlockTasks {

  /**
   * Perform block processing. The supplied blockSlotState must already have empty slots processed
   * to the same slot as the block.
   */
  @CheckReturnValue
  public BlockImportResult on_block(
      final MutableStore store,
      final SignedBeaconBlock signed_block,
      BeaconState blockSlotState,
      final StateTransition st,
      final IndexedAttestationProvider indexedAttestationProvider) {
    checkArgument(
        blockSlotState.getSlot().equals(signed_block.getSlot()),
        "State must have slots processed up to the block slot");
    final BeaconBlock block = signed_block.getMessage();

    // Return early if precondition checks fail;
    final Optional<BlockImportResult> maybeFailure =
        checkOnBlockConditions(block, blockSlotState, store);
    if (maybeFailure.isPresent()) {
      return maybeFailure.get();
    }

    // Make a copy of the state to avoid mutability issues
    BeaconState state;

    // Check the block is valid and compute the post-state
    try {
      state =
          st.processAndValidateBlock(
              signed_block, indexedAttestationProvider, blockSlotState, true);
    } catch (StateTransitionException e) {
      return BlockImportResult.failedStateTransition(e);
    }

    // Add new block to store
    store.putBlockAndState(signed_block, state);

    // Update justified checkpoint
    final Checkpoint justifiedCheckpoint = state.getCurrent_justified_checkpoint();
    if (justifiedCheckpoint.getEpoch().compareTo(store.getJustifiedCheckpoint().getEpoch()) > 0) {
      if (justifiedCheckpoint.getEpoch().compareTo(store.getBestJustifiedCheckpoint().getEpoch())
          > 0) {
        store.setBestJustifiedCheckpoint(justifiedCheckpoint);
      }
      if (should_update_justified_checkpoint(
          store, justifiedCheckpoint, store.getForkChoiceStrategy())) {
        store.setJustifiedCheckpoint(justifiedCheckpoint);
      }
    }

    // Update finalized checkpoint
    final Checkpoint finalizedCheckpoint = state.getFinalized_checkpoint();
    if (finalizedCheckpoint.getEpoch().compareTo(store.getFinalizedCheckpoint().getEpoch()) > 0) {
      store.setFinalizedCheckpoint(finalizedCheckpoint);

      // Potentially update justified if different from store
      if (!store.getJustifiedCheckpoint().equals(state.getCurrent_justified_checkpoint())) {
        // Update justified if new justified is later than store justified
        // or if store justified is not in chain with finalized checkpoint
        if (state
                    .getCurrent_justified_checkpoint()
                    .getEpoch()
                    .compareTo(store.getJustifiedCheckpoint().getEpoch())
                > 0
            || !isFinalizedAncestorOfJustified(store)) {
          store.setJustifiedCheckpoint(state.getCurrent_justified_checkpoint());
        }
      }
    }

    return BlockImportResult.successful(signed_block);
  }

  private static boolean isFinalizedAncestorOfJustified(ReadOnlyStore store) {
    UInt64 finalizedSlot = store.getFinalizedCheckpoint().getEpochStartSlot();
    return hasAncestorAtSlot(
        store.getForkChoiceStrategy(),
        store.getJustifiedCheckpoint().getRoot(),
        finalizedSlot,
        store.getFinalizedCheckpoint().getRoot());
  }

  private static Optional<BlockImportResult> checkOnBlockConditions(
      final BeaconBlock block, final BeaconState blockSlotState, final ReadOnlyStore store) {
    final UInt64 blockSlot = block.getSlot();
    if (blockSlotState == null) {
      return Optional.of(BlockImportResult.FAILED_UNKNOWN_PARENT);
    }
    if (!blockSlotState.getSlot().equals(blockSlot)) {
      return Optional.of(BlockImportResult.FAILED_INVALID_ANCESTRY);
    }
    if (blockSlot.isGreaterThan(UInt64.valueOf(GENESIS_SLOT))
        && !get_block_root_at_slot(blockSlotState, blockSlot.minus(1))
            .equals(block.getParentRoot())) {
      // Block is at same slot as its parent or the parent root doesn't match the state
      return Optional.of(BlockImportResult.FAILED_INVALID_ANCESTRY);
    }
    if (blockIsFromFuture(store, blockSlot)) {
      return Optional.of(BlockImportResult.FAILED_BLOCK_IS_FROM_FUTURE);
    }
    if (!blockDescendsFromLatestFinalizedBlock(block, store, store.getForkChoiceStrategy())) {
      return Optional.of(BlockImportResult.FAILED_INVALID_ANCESTRY);
    }
    return Optional.empty();
  }

  private static boolean blockIsFromFuture(ReadOnlyStore store, final UInt64 blockSlot) {
    return get_current_slot(store).compareTo(blockSlot) < 0;
  }

  public static boolean blockDescendsFromLatestFinalizedBlock(
      final BeaconBlock block,
      final ReadOnlyStore store,
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy) {
    final Checkpoint finalizedCheckpoint = store.getFinalizedCheckpoint();
    final UInt64 blockSlot = block.getSlot();

    // Make sure this block's slot is after the latest finalized slot
    return blockIsAfterLatestFinalizedSlot(blockSlot, finalizedCheckpoint.getEpochStartSlot())
        && hasAncestorAtSlot(
            forkChoiceStrategy,
            block.getParentRoot(),
            finalizedCheckpoint.getEpochStartSlot(),
            finalizedCheckpoint.getRoot());
  }

  private static boolean blockIsAfterLatestFinalizedSlot(
      final UInt64 blockSlot, final UInt64 finalizedEpochStartSlot) {
    return blockSlot.compareTo(finalizedEpochStartSlot) > 0;
  }

  /*
  To address the bouncing attack, only update conflicting justified
  checkpoints in the fork choice if in the early slots of the epoch.
  Otherwise, delay incorporation of new justified checkpoint until next epoch boundary.
  See https://ethresear.ch/t/prevention-of-bouncing-attack-on-ffg/6114 for more detailed analysis and discussion.
  */

  private static boolean should_update_justified_checkpoint(
      ReadOnlyStore store,
      Checkpoint new_justified_checkpoint,
      ReadOnlyForkChoiceStrategy forkChoiceStrategy) {
    if (compute_slots_since_epoch_start(get_current_slot(store, true))
            .compareTo(UInt64.valueOf(SAFE_SLOTS_TO_UPDATE_JUSTIFIED))
        < 0) {
      return true;
    }

    UInt64 justifiedSlot = compute_start_slot_at_epoch(store.getJustifiedCheckpoint().getEpoch());
    return hasAncestorAtSlot(
        forkChoiceStrategy,
        new_justified_checkpoint.getRoot(),
        justifiedSlot,
        store.getJustifiedCheckpoint().getRoot());
  }

  private static boolean hasAncestorAtSlot(
      ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      Bytes32 root,
      UInt64 slot,
      Bytes32 ancestorRoot) {
    return get_ancestor(forkChoiceStrategy, root, slot)
        .map(ancestorAtSlot -> ancestorAtSlot.equals(ancestorRoot))
        .orElse(false);
  }
}
