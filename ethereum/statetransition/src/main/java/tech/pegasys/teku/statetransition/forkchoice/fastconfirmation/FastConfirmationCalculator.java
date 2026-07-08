/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;

/**
 * Executes the Fast Confirmation Rule against a fixed slot-start snapshot: the fork-choice {@code
 * store}, the head selected at slot start, and the current slot. Instances are short-lived and
 * single-threaded (created per slot on the fast confirmation runner).
 *
 * <p>It reads block metadata and ancestry from the protoarray fork-choice strategy and derives the
 * source-state helpers from the {@link FastConfirmationStates} loaded for the slot. Ancestry checks
 * delegate to the fork-choice {@code is_ancestor} via {@link ForkChoiceUtil}, so Gloas
 * payload-status semantics are preserved; block roots are lifted to the spec's {@code
 * ForkChoiceNode} through {@code get_node_for_root}.
 */
class FastConfirmationCalculator {

  private final Spec spec;
  private final ForkChoiceUtil forkChoiceUtil;
  private final ReadOnlyForkChoiceStrategy forkChoice;
  private final FastConfirmationStates states;
  private final Bytes32 head;
  private final UInt64 currentEpoch;

  // Lazily computed once per instance (single-threaded per slot); see getPulledUpHeadState.
  private BeaconState pulledUpHeadState;

  FastConfirmationCalculator(
      final Spec spec,
      final ReadOnlyStore store,
      final FastConfirmationStates states,
      final Bytes32 head,
      final UInt64 currentSlot) {
    this.spec = spec;
    this.forkChoiceUtil = spec.atSlot(currentSlot).getForkChoiceUtil();
    this.forkChoice = store.getForkChoiceStrategy();
    this.states = states;
    this.head = head;
    this.currentEpoch = spec.computeEpochAtSlot(currentSlot);
  }

  /** Implements {@code get_block_slot}. */
  UInt64 getBlockSlot(final Bytes32 blockRoot) {
    return forkChoice
        .blockSlot(blockRoot)
        .orElseThrow(() -> new IllegalStateException("Missing block slot for " + blockRoot));
  }

  /** Implements {@code get_block_epoch}. */
  UInt64 getBlockEpoch(final Bytes32 blockRoot) {
    return spec.computeEpochAtSlot(getBlockSlot(blockRoot));
  }

  Bytes32 getBlockParentRoot(final Bytes32 blockRoot) {
    return forkChoice
        .blockParentRoot(blockRoot)
        .orElseThrow(() -> new IllegalStateException("Missing parent root for " + blockRoot));
  }

  /** Implements {@code store.unrealized_justifications[block_root]}. */
  Checkpoint getUnrealizedJustification(final Bytes32 blockRoot) {
    return getCheckpoints(blockRoot).getUnrealizedJustifiedCheckpoint();
  }

  /** Implements {@code get_checkpoint_for_block}. */
  Checkpoint getCheckpointForBlock(final Bytes32 blockRoot, final UInt64 epoch) {
    return new Checkpoint(epoch, getCheckpointBlock(blockRoot, epoch));
  }

  /** Implements {@code get_current_target}. */
  Checkpoint getCurrentTarget() {
    return getCheckpointForBlock(head, currentEpoch);
  }

  /**
   * Implements {@code get_pulled_up_head_state}: the head state advanced to the start of the
   * current epoch when it lags behind, otherwise the head state as-is. Memoized because the FFG
   * helpers read it repeatedly within a single slot.
   */
  BeaconState getPulledUpHeadState() {
    if (pulledUpHeadState == null) {
      pulledUpHeadState = computePulledUpHeadState();
    }
    return pulledUpHeadState;
  }

  /**
   * Implements {@code get_slot_committee}: all validators assigned to any committee in {@code
   * slot}, using the raw head state as the shuffling source.
   */
  IntSet getSlotCommittee(final UInt64 slot) {
    final BeaconState shufflingSource = states.headBlockState();
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    final int committeesCount = spec.getCommitteeCountPerSlot(shufflingSource, epoch).intValue();
    final IntSet participants = new IntOpenHashSet();
    for (int committeeIndex = 0; committeeIndex < committeesCount; committeeIndex++) {
      participants.addAll(
          spec.getBeaconCommittee(shufflingSource, slot, UInt64.valueOf(committeeIndex)));
    }
    return participants;
  }

  private BeaconState computePulledUpHeadState() {
    final BeaconState headState = states.headBlockState();
    if (spec.getCurrentEpoch(headState).isLessThan(currentEpoch)) {
      try {
        return spec.processSlots(headState, spec.computeStartSlotAtEpoch(currentEpoch));
      } catch (final SlotProcessingException | EpochProcessingException e) {
        throw new IllegalStateException("Failed to pull up head state for fast confirmation", e);
      }
    }
    return headState;
  }

  /**
   * Implements {@code get_voting_source}: the voting source of a block is its unrealized justified
   * checkpoint when the block is from a prior epoch (pulled up), otherwise its realized justified
   * checkpoint. Teku's protoarray stores the block's post-state realized justified checkpoint,
   * which equals the spec's {@code store.block_states[block_root].current_justified_checkpoint}, so
   * no block state needs to be loaded here.
   */
  Checkpoint getVotingSource(final Bytes32 blockRoot) {
    final UInt64 blockEpoch = getBlockEpoch(blockRoot);
    if (currentEpoch.isGreaterThan(blockEpoch)) {
      return getUnrealizedJustification(blockRoot);
    }
    return getCheckpoints(blockRoot).getJustifiedCheckpoint();
  }

  /**
   * Implements {@code is_ancestor(store, get_node_for_root(descendantRoot),
   * get_node_for_root(ancestorRoot))}: {@code true} if {@code ancestorRoot} is an ancestor of
   * {@code descendantRoot} (a block is considered its own ancestor).
   *
   * <p>Delegates to the fork-choice {@code is_ancestor} rather than re-deriving ancestry, so the
   * Gloas payload-status semantics are honoured.
   */
  boolean isAncestor(final Bytes32 descendantRoot, final Bytes32 ancestorRoot) {
    return forkChoiceUtil.isAncestor(
        forkChoice, getNodeForRoot(descendantRoot), getNodeForRoot(ancestorRoot));
  }

  /**
   * Implements {@code get_node_for_root}: pre-Gloas {@code ForkChoiceNode(root)}, Gloas {@code
   * ForkChoiceNode(root, PAYLOAD_STATUS_PENDING)}. Both map to the {@linkplain
   * ForkChoiceNode#createBase(Bytes32) base (PENDING)} node.
   */
  private ForkChoiceNode getNodeForRoot(final Bytes32 blockRoot) {
    return ForkChoiceNode.createBase(blockRoot);
  }

  /**
   * Implements {@code get_ancestor_roots}: the ancestors of {@code blockRoot} (inclusive) down to,
   * but excluding, {@code terminalRoot}, ordered oldest first. Returns an empty list when {@code
   * terminalRoot} is not in the chain of {@code blockRoot}.
   */
  List<Bytes32> getAncestorRoots(final Bytes32 blockRoot, final Bytes32 terminalRoot) {
    final Deque<Bytes32> ancestorRoots = new ArrayDeque<>();
    final UInt64 terminalSlot = getBlockSlot(terminalRoot);
    Bytes32 root = blockRoot;
    while (getBlockSlot(root).isGreaterThan(terminalSlot)) {
      ancestorRoots.addFirst(root);
      root = getBlockParentRoot(root);
      // Return when terminalRoot is reached
      if (root.equals(terminalRoot)) {
        return new ArrayList<>(ancestorRoots);
      }
    }
    // Return empty list if terminalRoot is not in the chain of blockRoot
    return List.of();
  }

  /** Implements {@code get_checkpoint_block}. */
  private Bytes32 getCheckpointBlock(final Bytes32 blockRoot, final UInt64 epoch) {
    return getAncestorRoot(blockRoot, spec.computeStartSlotAtEpoch(epoch));
  }

  /** Implements {@code get_ancestor(store, node, slot).root}. */
  private Bytes32 getAncestorRoot(final Bytes32 blockRoot, final UInt64 slot) {
    return forkChoice
        .getAncestor(blockRoot, slot)
        .orElseThrow(
            () ->
                new IllegalStateException("Missing ancestor of " + blockRoot + " at slot " + slot));
  }

  private BlockCheckpoints getCheckpoints(final Bytes32 blockRoot) {
    return forkChoice
        .getBlockData(blockRoot)
        .map(ProtoNodeData::getCheckpoints)
        .orElseThrow(() -> new IllegalStateException("Missing checkpoints for " + blockRoot));
  }
}
