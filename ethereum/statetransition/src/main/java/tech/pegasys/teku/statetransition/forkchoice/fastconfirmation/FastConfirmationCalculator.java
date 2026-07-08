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
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteSnapshot;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Validator;
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
  private final FastConfirmationStore fcrStore;
  private final ReadOnlyStore store;
  private final ReadOnlyForkChoiceStrategy forkChoice;
  private final VoteSnapshot votes;
  private final FastConfirmationStates states;
  private final Bytes32 head;
  private final UInt64 currentSlot;
  private final UInt64 currentEpoch;

  // Lazily computed once per instance (single-threaded per slot); see getPulledUpHeadState.
  private BeaconState pulledUpHeadState;

  FastConfirmationCalculator(
      final Spec spec,
      final FastConfirmationStore fcrStore,
      final FastConfirmationStates states,
      final UInt64 currentSlot) {
    this.spec = spec;
    this.forkChoiceUtil = spec.atSlot(currentSlot).getForkChoiceUtil();
    this.fcrStore = fcrStore;
    this.store = fcrStore.store();
    this.forkChoice = store.getForkChoiceStrategy();
    this.votes = store.getVoteSnapshot();
    this.states = states;
    // After update_fast_confirmation_variables, current_slot_head == get_head(store).root.
    this.head = fcrStore.currentSlotHead();
    this.currentSlot = currentSlot;
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

  /**
   * Implements {@code get_attestation_score}: the total effective balance (per {@code
   * balanceSource}) of unslashed, active, non-equivocating validators whose latest vote supports a
   * descendant of {@code nodeRoot}.
   */
  UInt64 getAttestationScore(final Bytes32 nodeRoot, final BeaconState balanceSource) {
    final UInt64 balanceSourceEpoch = spec.getCurrentEpoch(balanceSource);
    final SszList<Validator> validators = balanceSource.getValidators();
    UInt64 score = UInt64.ZERO;
    for (final int index : spec.getActiveValidatorIndices(balanceSource, balanceSourceEpoch)) {
      final Validator validator = validators.get(index);
      if (validator.isSlashed()) {
        continue;
      }
      final VoteTracker vote = votes.getVote(index);
      if (vote.isEquivocating()) {
        continue;
      }
      final Bytes32 votedRoot = vote.getNextRoot();
      // A zero root means the validator is not in store.latest_messages.
      if (!votedRoot.isZero() && isAncestor(votedRoot, nodeRoot)) {
        score = score.plus(validator.getEffectiveBalance());
      }
    }
    return score;
  }

  /**
   * Implements {@code get_block_support_between_slots}: the total effective balance (per {@code
   * balanceSource}) of unslashed, active, non-equivocating validators assigned to the inclusive
   * slot range whose latest vote is exactly {@code blockRoot}.
   */
  UInt64 getBlockSupportBetweenSlots(
      final BeaconState balanceSource,
      final Bytes32 blockRoot,
      final UInt64 startSlot,
      final UInt64 endSlot) {
    final UInt64 balanceSourceEpoch = spec.getCurrentEpoch(balanceSource);
    final SszList<Validator> validators = balanceSource.getValidators();
    UInt64 support = UInt64.ZERO;
    for (final int index : getCommitteeBetweenSlots(startSlot, endSlot)) {
      final Validator validator = validators.get(index);
      if (validator.isSlashed() || !isActiveValidator(validator, balanceSourceEpoch)) {
        continue;
      }
      final VoteTracker vote = votes.getVote(index);
      if (!vote.isEquivocating() && vote.getNextRoot().equals(blockRoot)) {
        support = support.plus(validator.getEffectiveBalance());
      }
    }
    return support;
  }

  /**
   * Implements {@code get_equivocation_score}: the total effective balance (per {@code
   * balanceSource}) of active, equivocating validators assigned to the inclusive slot range. Per
   * spec, slashed validators are not filtered out here (they are very likely already equivocating).
   */
  UInt64 getEquivocationScore(
      final BeaconState balanceSource, final UInt64 startSlot, final UInt64 endSlot) {
    final UInt64 balanceSourceEpoch = spec.getCurrentEpoch(balanceSource);
    final SszList<Validator> validators = balanceSource.getValidators();
    UInt64 score = UInt64.ZERO;
    for (final int index : getCommitteeBetweenSlots(startSlot, endSlot)) {
      if (!votes.getVote(index).isEquivocating()) {
        continue;
      }
      final Validator validator = validators.get(index);
      if (isActiveValidator(validator, balanceSourceEpoch)) {
        score = score.plus(validator.getEffectiveBalance());
      }
    }
    return score;
  }

  /**
   * Implements {@code compute_adversarial_weight}: the maximum weight that could be adversarial in
   * the committees of the slot range, assuming {@code CONFIRMATION_BYZANTINE_THRESHOLD} and
   * discounting validators already known to be equivocating.
   */
  UInt64 computeAdversarialWeight(
      final BeaconState balanceSource, final UInt64 startSlot, final UInt64 endSlot) {
    final UInt64 totalActiveBalance = spec.getTotalActiveBalance(balanceSource);
    final UInt64 maximumWeight =
        FastConfirmationRuleUtil.estimateCommitteeWeightBetweenSlots(
            spec, totalActiveBalance, startSlot, endSlot);
    final UInt64 maxAdversarialWeight =
        maximumWeight
            .dividedBy(100)
            .times(FastConfirmationRuleUtil.CONFIRMATION_BYZANTINE_THRESHOLD);
    final UInt64 equivocationScore = getEquivocationScore(balanceSource, startSlot, endSlot);
    return maxAdversarialWeight.isGreaterThan(equivocationScore)
        ? maxAdversarialWeight.minus(equivocationScore)
        : UInt64.ZERO;
  }

  /**
   * Implements {@code get_adversarial_weight}: the maximum adversarial weight that can support the
   * block, from the block's first relevant slot up to the slot before the current one.
   */
  UInt64 getAdversarialWeight(final BeaconState balanceSource, final Bytes32 blockRoot) {
    final UInt64 lastSlot = currentSlot.minus(1);
    final UInt64 blockEpoch = getBlockEpoch(blockRoot);
    if (blockEpoch.isGreaterThan(getBlockEpoch(getBlockParentRoot(blockRoot)))) {
      // Use the first epoch slot as the start slot when crossing an epoch boundary.
      return computeAdversarialWeight(
          balanceSource, spec.computeStartSlotAtEpoch(blockEpoch), lastSlot);
    }
    return computeAdversarialWeight(balanceSource, getBlockSlot(blockRoot), lastSlot);
  }

  /**
   * Implements {@code compute_empty_slot_support_discount}: weight discountable from the safety
   * threshold when empty slots precede the block, i.e. parent support from the empty slots'
   * committees beyond what could be adversarial.
   */
  UInt64 computeEmptySlotSupportDiscount(final BeaconState balanceSource, final Bytes32 blockRoot) {
    final Bytes32 parentRoot = getBlockParentRoot(blockRoot);
    final UInt64 blockSlot = getBlockSlot(blockRoot);
    final UInt64 parentSlot = getBlockSlot(parentRoot);
    if (parentSlot.plus(1).equals(blockSlot)) {
      // No empty slot.
      return UInt64.ZERO;
    }
    final UInt64 firstEmptySlot = parentSlot.plus(1);
    final UInt64 lastEmptySlot = blockSlot.minus(1);
    final UInt64 parentSupportInEmptySlots =
        getBlockSupportBetweenSlots(balanceSource, parentRoot, firstEmptySlot, lastEmptySlot);
    final UInt64 adversarialWeight =
        computeAdversarialWeight(balanceSource, firstEmptySlot, lastEmptySlot);
    return parentSupportInEmptySlots.isGreaterThan(adversarialWeight)
        ? parentSupportInEmptySlots.minus(adversarialWeight)
        : UInt64.ZERO;
  }

  /** Implements {@code get_support_discount}. */
  UInt64 getSupportDiscount(final BeaconState balanceSource, final Bytes32 blockRoot) {
    return computeEmptySlotSupportDiscount(balanceSource, blockRoot);
  }

  /** Implements {@code compute_safety_threshold}: the LMD-GHOST safety threshold for the block. */
  UInt64 computeSafetyThreshold(final Bytes32 blockRoot, final BeaconState balanceSource) {
    final UInt64 parentSlot = getBlockSlot(getBlockParentRoot(blockRoot));
    final UInt64 totalActiveBalance = spec.getTotalActiveBalance(balanceSource);
    final UInt64 proposerScore = spec.getProposerBoostAmount(balanceSource);
    final UInt64 maximumSupport =
        FastConfirmationRuleUtil.estimateCommitteeWeightBetweenSlots(
            spec, totalActiveBalance, parentSlot.plus(1), currentSlot.minus(1));
    final UInt64 supportDiscount = getSupportDiscount(balanceSource, blockRoot);
    final UInt64 adversarialWeight = getAdversarialWeight(balanceSource, blockRoot);

    // (maximumSupport + proposerScore + 2 * adversarialWeight - supportDiscount) // 2, guarded
    // against underflow.
    final UInt64 gross = maximumSupport.plus(proposerScore).plus(adversarialWeight.times(2));
    return supportDiscount.isLessThan(gross)
        ? gross.minus(supportDiscount).dividedBy(2)
        : UInt64.ZERO;
  }

  /**
   * Implements {@code is_one_confirmed}: whether the block is LMD-GHOST safe (its support exceeds
   * the safety threshold). Returns {@code false} for a block that is not fully validated (not
   * {@code VALID} per optimistic sync).
   */
  boolean isOneConfirmed(final BeaconState balanceSource, final Bytes32 blockRoot) {
    if (!forkChoice.isFullyValidated(blockRoot)) {
      return false;
    }
    final UInt64 support = getAttestationScore(blockRoot, balanceSource);
    final UInt64 safetyThreshold = computeSafetyThreshold(blockRoot, balanceSource);
    return support.isGreaterThan(safetyThreshold);
  }

  /**
   * Implements {@code get_current_target_score}: the estimated FFG support of the current-epoch
   * target, using the pulled-up head state's validator set and the LMD votes received so far.
   */
  UInt64 getCurrentTargetScore() {
    final Checkpoint target = getCurrentTarget();
    final BeaconState state = getPulledUpHeadState();
    final UInt64 epoch = spec.getCurrentEpoch(state);
    final SszList<Validator> validators = state.getValidators();
    UInt64 score = UInt64.ZERO;
    for (final int index : spec.getActiveValidatorIndices(state, epoch)) {
      final Validator validator = validators.get(index);
      if (validator.isSlashed()) {
        continue;
      }
      final VoteTracker vote = votes.getVote(index);
      if (vote.isEquivocating()) {
        continue;
      }
      final Bytes32 votedRoot = vote.getNextRoot();
      if (votedRoot.isZero() || !forkChoice.contains(votedRoot)) {
        continue;
      }
      final UInt64 messageEpoch = spec.computeEpochAtSlot(vote.getNextSlot());
      if (target.equals(getCheckpointForBlock(votedRoot, messageEpoch))) {
        score = score.plus(validator.getEffectiveBalance());
      }
    }
    return score;
  }

  /**
   * Implements {@code compute_honest_ffg_support_for_current_target}: the minimum honest FFG
   * support the current-epoch target can be assured of, assuming synchrony and {@code
   * CONFIRMATION_BYZANTINE_THRESHOLD}.
   */
  UInt64 computeHonestFfgSupportForCurrentTarget() {
    final BeaconState balanceSource = getPulledUpHeadState();
    final UInt64 totalActiveBalance = spec.getTotalActiveBalance(balanceSource);
    final UInt64 ffgSupportForCheckpoint = getCurrentTargetScore();
    final UInt64 epochStart = spec.computeStartSlotAtEpoch(currentEpoch);
    final UInt64 lastSlot = currentSlot.minus(1);

    // Total FFG weight already assigned up to, but excluding, the current slot.
    final UInt64 ffgWeightTillNow =
        FastConfirmationRuleUtil.estimateCommitteeWeightBetweenSlots(
            spec, totalActiveBalance, epochStart, lastSlot);
    final UInt64 remainingFfgWeight = totalActiveBalance.minusMinZero(ffgWeightTillNow);
    final UInt64 remainingHonestFfgWeight =
        remainingFfgWeight
            .dividedBy(100)
            .times(100 - FastConfirmationRuleUtil.CONFIRMATION_BYZANTINE_THRESHOLD);

    final UInt64 adversarialWeight = computeAdversarialWeight(balanceSource, epochStart, lastSlot);
    // ffg_support - min(adversarial_weight, ffg_support)
    final UInt64 minHonestFfgSupport = ffgSupportForCheckpoint.minusMinZero(adversarialWeight);

    return minHonestFfgSupport.plus(remainingHonestFfgWeight);
  }

  /**
   * Implements {@code will_no_conflicting_checkpoint_be_justified}: whether no checkpoint
   * conflicting with the current target can ever be justified.
   */
  boolean willNoConflictingCheckpointBeJustified() {
    // If the target is already the greatest unrealized justified checkpoint, nothing can conflict.
    if (getCurrentTarget().equals(getStoreUnrealizedJustifiedCheckpoint())) {
      return true;
    }
    final UInt64 totalActiveBalance = spec.getTotalActiveBalance(getPulledUpHeadState());
    return computeHonestFfgSupportForCurrentTarget().times(3).isGreaterThan(totalActiveBalance);
  }

  /**
   * Implements {@code will_current_target_be_justified}: whether the current target will eventually
   * be justified.
   */
  boolean willCurrentTargetBeJustified() {
    final UInt64 totalActiveBalance = spec.getTotalActiveBalance(getPulledUpHeadState());
    return computeHonestFfgSupportForCurrentTarget()
        .times(3)
        .isGreaterThanOrEqualTo(totalActiveBalance.times(2));
  }

  /**
   * Implements {@code is_confirmed_chain_safe}: whether every block of the confirmed chain, from
   * the current-epoch observed justified checkpoint up to {@code confirmedRoot}, is LMD-GHOST safe
   * against the previous balance source. Run at the start of each epoch to relax the synchrony
   * assumption (reconfirmation).
   */
  boolean isConfirmedChainSafe(final Bytes32 confirmedRoot) {
    final Checkpoint observedJustified = fcrStore.currentEpochObservedJustifiedCheckpoint();
    // The observed justified checkpoint must be on the confirmed chain.
    if (!observedJustified.equals(
        getCheckpointForBlock(confirmedRoot, observedJustified.getEpoch()))) {
      return false;
    }

    final Bytes32 startRootExclusive;
    if (observedJustified.getEpoch().plus(1).isGreaterThanOrEqualTo(currentEpoch)) {
      // Exclude the justified checkpoint block: from the previous epoch it is always canonical.
      startRootExclusive = observedJustified.getRoot();
    } else {
      // Limit reconfirmation to the first block of the previous epoch; confirming it implies its
      // ancestors.
      final Bytes32 ancestorAtPreviousEpochStart =
          getAncestorRoot(confirmedRoot, spec.computeStartSlotAtEpoch(currentEpoch.minus(1)));
      startRootExclusive =
          getBlockEpoch(ancestorAtPreviousEpochStart).plus(1).equals(currentEpoch)
              ? getBlockParentRoot(ancestorAtPreviousEpochStart)
              : ancestorAtPreviousEpochStart;
    }

    final BeaconState previousBalanceSource =
        states
            .previousBalanceSource()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Previous balance source is required for reconfirmation"));
    return getAncestorRoots(confirmedRoot, startRootExclusive).stream()
        .allMatch(root -> isOneConfirmed(previousBalanceSource, root));
  }

  /**
   * Implements {@code find_latest_confirmed_descendant}: the most recent confirmed block in the
   * canonical suffix starting from {@code latestConfirmedRoot}. It first tries to advance across
   * previous-epoch blocks (relying on the previous slot head), then further into the current epoch
   * (gated on the current target being justifiable), keeping the result only if it cannot be
   * reorged out in the current or next epoch.
   */
  Bytes32 findLatestConfirmedDescendant(final Bytes32 latestConfirmedRoot) {
    final boolean atEpochStart = FastConfirmationRuleUtil.isStartSlotAtEpoch(spec, currentSlot);
    final Bytes32 previousSlotHead = fcrStore.previousSlotHead();
    final BeaconState currentBalanceSource = states.currentBalanceSource();
    Bytes32 confirmedRoot = latestConfirmedRoot;

    if (getBlockEpoch(confirmedRoot).plus(1).equals(currentEpoch)
        && epochPlusIsAtLeastCurrent(getVotingSource(previousSlotHead).getEpoch(), 2)
        && (atEpochStart
            || (willNoConflictingCheckpointBeJustified()
                && (epochPlusIsAtLeastCurrent(
                        getUnrealizedJustification(previousSlotHead).getEpoch(), 1)
                    || epochPlusIsAtLeastCurrent(
                        getUnrealizedJustification(head).getEpoch(), 1))))) {
      // Advance towards the head over previous-epoch blocks; stop at the first unconfirmed one.
      for (final Bytes32 blockRoot : getAncestorRoots(head, confirmedRoot)) {
        // Only meant to confirm previous-epoch blocks.
        if (getBlockEpoch(blockRoot).equals(currentEpoch)) {
          break;
        }
        // Can only rely on the previous head if it descends from the block being confirmed.
        if (!isAncestor(previousSlotHead, blockRoot)) {
          break;
        }
        if (!isOneConfirmed(currentBalanceSource, blockRoot)) {
          break;
        }
        confirmedRoot = blockRoot;
      }
    }

    if (atEpochStart || epochPlusIsAtLeastCurrent(getUnrealizedJustification(head).getEpoch(), 1)) {
      Bytes32 tentativeConfirmedRoot = confirmedRoot;
      for (final Bytes32 blockRoot : getAncestorRoots(head, confirmedRoot)) {
        // Only true the first time the walk advances into the current epoch.
        if (getBlockEpoch(blockRoot).isGreaterThan(getBlockEpoch(tentativeConfirmedRoot))
            && !willCurrentTargetBeJustified()) {
          break;
        }
        if (!isOneConfirmed(currentBalanceSource, blockRoot)) {
          break;
        }
        tentativeConfirmedRoot = blockRoot;
      }

      // Only keep the advance if the block cannot be reorged out this epoch or the next.
      if (getBlockEpoch(tentativeConfirmedRoot).equals(currentEpoch)
          || (epochPlusIsAtLeastCurrent(getVotingSource(tentativeConfirmedRoot).getEpoch(), 2)
              && (atEpochStart || willNoConflictingCheckpointBeJustified()))) {
        confirmedRoot = tentativeConfirmedRoot;
      }
    }

    return confirmedRoot;
  }

  /**
   * Returns {@code epoch + offset >= currentEpoch} (spec pattern {@code checkpoint.epoch + n >=
   * current_epoch}).
   */
  private boolean epochPlusIsAtLeastCurrent(final UInt64 epoch, final long offset) {
    return epoch.plus(offset).isGreaterThanOrEqualTo(currentEpoch);
  }

  /** Reconstructs {@code store.unrealized_justified_checkpoint} (the store-level greatest). */
  private Checkpoint getStoreUnrealizedJustifiedCheckpoint() {
    return FastConfirmationRuleUtil.getGreatestUnrealizedJustifiedCheckpoint(store);
  }

  /**
   * Union of {@code get_slot_committee} over the inclusive slot range {@code [startSlot, endSlot]}.
   */
  private IntSet getCommitteeBetweenSlots(final UInt64 startSlot, final UInt64 endSlot) {
    final IntSet participants = new IntOpenHashSet();
    for (UInt64 slot = startSlot; slot.isLessThanOrEqualTo(endSlot); slot = slot.increment()) {
      participants.addAll(getSlotCommittee(slot));
    }
    return participants;
  }

  private boolean isActiveValidator(final Validator validator, final UInt64 epoch) {
    return spec.atEpoch(epoch).predicates().isActiveValidator(validator, epoch);
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
