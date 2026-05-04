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

package tech.pegasys.teku.spec.logic.versions.gloas.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.versions.fulu.util.ForkChoiceUtilFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.statetransition.epoch.EpochProcessorGloas;

public class ForkChoiceUtilGloas extends ForkChoiceUtilFulu {
  public ForkChoiceUtilGloas(
      final SpecConfigGloas specConfig,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final EpochProcessorGloas epochProcessor,
      final AttestationUtilGloas attestationUtil,
      final MiscHelpersGloas miscHelpers) {
    super(specConfig, beaconStateAccessors, epochProcessor, attestationUtil, miscHelpers);
  }

  @Override
  public boolean shouldUpdateVote(
      final VoteTracker vote, final UInt64 targetEpoch, final UInt64 slot) {
    return slot.isGreaterThan(vote.getNextSlot()) || vote.equals(VoteTracker.DEFAULT);
  }

  public static ForkChoiceUtilGloas required(final ForkChoiceUtil forkChoiceUtil) {
    checkArgument(
        forkChoiceUtil instanceof ForkChoiceUtilGloas,
        "Expected a ForkChoiceUtilGloas but was %s",
        forkChoiceUtil.getClass());
    return (ForkChoiceUtilGloas) forkChoiceUtil;
  }

  @Override
  public void applyExecutionPayloadToStore(
      final MutableStore store, final SignedExecutionPayloadEnvelope signedEnvelope) {
    // Add new execution payload to store
    store.putExecutionPayload(signedEnvelope);
  }

  @Override
  public Optional<Integer> getPayloadAttestationDueMillis() {
    final SpecConfigGloas configGloas = SpecConfigGloas.required(specConfig);
    return Optional.of(getSlotComponentDurationMillis(configGloas.getPayloadAttestationDueBps()));
  }

  /**
   * Computes dual block timeliness for Gloas: attestation deadline and PTC deadline.
   *
   * <p>Spec reference: modified {@code record_block_timeliness(store, root)} plus new {@code
   * get_payload_attestation_due_ms()}.
   */
  @Override
  public BlockTimeliness computeBlockTimeliness(
      final UInt64 blockSlot, final UInt64 currentSlot, final int millisIntoSlot) {
    final int attestationTimelinessLimit = getAttestationDueMillis();
    final int ptcTimelinessLimit = getPayloadAttestationDueMillis().orElseThrow();
    final boolean isTimelyAttestation =
        blockSlot.equals(currentSlot) && millisIntoSlot < attestationTimelinessLimit;
    final boolean isTimelyPtc =
        blockSlot.equals(currentSlot) && millisIntoSlot < ptcTimelinessLimit;
    return new BlockTimeliness(isTimelyAttestation, isTimelyPtc);
  }

  @Override
  public boolean getFullPayloadVoteHint(final UInt64 attestationIndex) {
    return attestationIndex.equals(UInt64.ONE);
  }

  @Override
  public AvailabilityChecker<?> createAvailabilityCheckerOnBlock(final SignedBeaconBlock block) {
    return AvailabilityChecker.NOOP;
  }

  @Override
  public AvailabilityChecker<?> createAvailabilityCheckerOnExecutionPayloadEnvelope(
      final SignedBeaconBlock block) {
    final AvailabilityCheckerFactory<UInt64> factory =
        this.dataColumnSidecarAvailabilityCheckerFactory;
    if (factory == null) {
      throw new IllegalStateException(
          "DataColumnSidecarAvailabilityCheckerFactory not initialized");
    }
    return factory.createAvailabilityChecker(block);
  }

  @Override
  public int computeCommitteeIndexForAttestation(
      final UInt64 slot,
      final BeaconBlock block,
      final int committeeIndex,
      final ReadOnlyStore store) {
    if (slot.equals(block.getSlot())) {
      return 0;
    }
    return isPayloadVerified(store, block.getRoot()) ? 1 : 0;
  }

  @Override
  public boolean shouldNotifyForkChoiceUpdatedOnBlock() {
    return false;
  }

  /**
   * Return whether the execution payload envelope for the beacon block with root ``root`` has been
   * locally delivered and verified via ``on_execution_payload_envelope``.
   */
  public boolean isPayloadVerified(final ReadOnlyStore store, final Bytes32 root) {
    return store.getExecutionPayloadIfAvailable(root).isPresent();
  }

  @Override
  public Optional<ForkChoiceUtilGloas> toVersionGloas() {
    return Optional.of(this);
  }

  /**
   * Determines whether proposer boost should be applied during weight computation.
   *
   * <p>In Gloas, proposer boost is conditionally suppressed to prevent equivocation-based reorgs.
   * If the boosted block's parent was weak and from the previous slot, boost only applies if there
   * are no timely equivocations from the same proposer.
   *
   * <p>Implementation note: the proposer-equivocation branch is intentionally not implemented yet.
   * The current code records both block timeliness flags, but it does not yet consume the PTC
   * timeliness bit here to suppress proposer boost on same-proposer equivocations. Because that
   * branch is still deferred, the weak-parent check has no effect on the return value and is
   * intentionally skipped here.
   *
   * @param proposerBoostRoot the current proposer boost root, empty if none
   * @param forkChoiceStrategy the fork choice strategy for looking up block data
   * @param reorgThreshold the threshold for the head weakness check
   * @param justifiedState unused until the proposer-equivocation branch is implemented
   * @return true if proposer boost should be applied
   */
  @Override
  public boolean shouldApplyProposerBoost(
      final Bytes32 proposerBoostRoot,
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final UInt64 reorgThreshold,
      final BeaconState justifiedState) {
    final Optional<Bytes32> maybeParentRoot = forkChoiceStrategy.blockParentRoot(proposerBoostRoot);
    final Optional<UInt64> maybeBlockSlot = forkChoiceStrategy.blockSlot(proposerBoostRoot);
    if (maybeParentRoot.isEmpty() || maybeBlockSlot.isEmpty()) {
      return true;
    }
    final Bytes32 parentRoot = maybeParentRoot.get();
    final UInt64 blockSlot = maybeBlockSlot.get();
    final Optional<UInt64> maybeParentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    if (maybeParentSlot.isEmpty()) {
      return true;
    }
    // Apply proposer boost if parent is not from the previous slot
    if (maybeParentSlot.get().increment().isLessThan(blockSlot)) {
      return true;
    }
    // TODO: implement the Gloas equivocation suppression branch from should_apply_proposer_boost
    // using recorded PTC timeliness instead of routing a predicate through ForkChoice.
    // The complication is that we need to have a good interaction with gossip datastructures to
    // detect equivocations. Spec should probably be updated.
    // NOTE: there is no point in implementing the following check without implementing
    // equivocation.
    // # Apply proposer boost if `parent` is not weak
    //    if not is_head_weak(store, parent_root):
    //        return True
    return true;
  }

  @Override
  public Optional<ForkChoiceNode> getAncestorNode(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy, final Bytes32 root, final UInt64 slot) {
    return forkChoiceStrategy.getAncestorNode(root, slot);
  }

  /**
   * Returns the node's attestation weight for the Gloas late-reorg checks.
   *
   * <p>This helper is used by {@code isHeadWeak(...)} and {@code isParentStrong(...)}. Those spec
   * helpers are defined in terms of unboosted attestation score, while protoarray stores the
   * boosted fork-choice weight used for head selection.
   *
   * <p>To avoid rescanning all validator votes on each query, we start from the node weight already
   * maintained in protoarray and, when the queried node is on the boosted chain, subtract the
   * proposer-boost component back out. The result is the effective attestation-only weight for the
   * specific node identity.
   */
  private UInt64 getNodeAttestationWeight(
      final ReadOnlyStore store,
      final Bytes32 nodeRoot,
      final ForkChoicePayloadStatus nodePayloadStatus,
      final BeaconState justifiedState) {
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();
    final UInt64 nodeWeight =
        forkChoiceStrategy
            .getBlockData(nodeRoot, nodePayloadStatus)
            .map(ProtoNodeData::getWeight)
            .orElse(UInt64.ZERO);
    final Optional<Bytes32> maybeBoostRoot = store.getProposerBoostRoot();
    if (maybeBoostRoot.isEmpty()) {
      return nodeWeight;
    }

    final boolean receivesProposerBoost =
        protoArrayWeightIncludesProposerBoost(
            forkChoiceStrategy, nodeRoot, nodePayloadStatus, maybeBoostRoot.get());
    if (!receivesProposerBoost) {
      return nodeWeight;
    }

    final UInt64 proposerBoostAmount = beaconStateAccessors.getProposerBoostAmount(justifiedState);
    return nodeWeight.minusMinZero(proposerBoostAmount);
  }

  private boolean protoArrayWeightIncludesProposerBoost(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 nodeRoot,
      final ForkChoicePayloadStatus nodePayloadStatus,
      final Bytes32 proposerBoostRoot) {
    return forkChoiceStrategy
        .blockSlot(nodeRoot)
        .flatMap(nodeSlot -> getAncestorNode(forkChoiceStrategy, proposerBoostRoot, nodeSlot))
        .filter(
            ancestorNode -> ancestorNode.equals(new ForkChoiceNode(nodeRoot, nodePayloadStatus)))
        .isPresent();
  }

  /**
   * Computes the weight of equivocating validators in the head block's committees.
   *
   * <p>In Gloas, equivocating validators' effective balance is ADDED to the head weight, making it
   * harder to reorg. This ensures is_head_weak is monotonic: more attestations can only change the
   * output from true to false.
   *
   * <p>This helper is the local extraction of the extra equivocating-committee term used by the
   * Gloas `is_head_weak(...)` override.
   *
   * @param headSlot the slot of the head block
   * @param store the fork choice store for reading validator votes
   * @param headState the head block's state (for committee computation)
   * @param justifiedState for effective balances
   * @return the total equivocating weight in head slot committees
   */
  UInt64 computeEquivocatingCommitteeWeight(
      final UInt64 headSlot,
      final ReadOnlyStore store,
      final BeaconState headState,
      final BeaconState justifiedState) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(headSlot);
    final UInt64 committeesPerSlot =
        beaconStateAccessors.getCommitteeCountPerSlot(headState, epoch);

    // TODO: we could optimize this by tracking a cumulative equivocating weight per slot,
    //  so we can lookup this fast without recompute the sum all the time.

    long equivocatingWeight = 0;
    for (UInt64 index = UInt64.ZERO;
        index.isLessThan(committeesPerSlot);
        index = index.increment()) {
      final IntList committee = beaconStateAccessors.getBeaconCommittee(headState, headSlot, index);
      for (final int validatorIndex : committee) {
        final VoteTracker vote = store.getVote(UInt64.valueOf(validatorIndex));
        if (vote.isEquivocating()) {
          equivocatingWeight +=
              justifiedState.getValidators().get(validatorIndex).getEffectiveBalance().longValue();
        }
      }
    }
    return UInt64.valueOf(equivocatingWeight);
  }

  /**
   * Extended isHeadWeak for Gloas with full attestation score and equivocating committee weight.
   *
   * <p>Spec reference: is_head_weak (Gloas override)
   *
   * <p>Implementation note: the equivocating-committee term is computed by {@link
   * #computeEquivocatingCommitteeWeight(UInt64, ReadOnlyStore, BeaconState, BeaconState)} so the
   * spec function is split across two Java helpers.
   *
   * @param root the head block root
   * @param reorgThreshold the threshold for weak head detection
   * @param headState the head block's state (for committee computation)
   * @param justifiedState for effective balances and attestation score
   * @return true if the head is weak
   */
  private boolean isHeadWeak(
      final ReadOnlyStore store,
      final Bytes32 root,
      final UInt64 reorgThreshold,
      final BeaconState headState,
      final BeaconState justifiedState) {
    UInt64 headWeight =
        getNodeAttestationWeight(store, root, PAYLOAD_STATUS_PENDING, justifiedState);

    // Add weight from equivocating validators in head slot committees
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();
    final Optional<UInt64> maybeHeadSlot = forkChoiceStrategy.blockSlot(root);
    if (maybeHeadSlot.isPresent()) {
      final UInt64 equivocatingWeight =
          computeEquivocatingCommitteeWeight(maybeHeadSlot.get(), store, headState, justifiedState);
      headWeight = headWeight.plus(equivocatingWeight);
    }

    return headWeight.isLessThan(reorgThreshold);
  }

  /**
   * Returns whether the current head is weak using only immediately-available Gloas inputs.
   *
   * <p>The spec score needs both the justified state and the head state. If either state is
   * missing, Teku conservatively returns {@code false} instead of approximating with protoarray
   * base weight, because that value may still include proposer boost and omits the extra
   * equivocating-committee term.
   *
   * <p>Returning {@code false} here suppresses the late-reorg override, so transient internal state
   * unavailability keeps the current head instead of encouraging a reorg to the parent.
   */
  @Override
  public boolean isHeadWeak(
      final ReadOnlyStore store, final Bytes32 root, final UInt64 reorgThreshold) {
    final Optional<BeaconState> maybeJustifiedState = store.getJustifiedStateIfAvailable();
    final Optional<BeaconState> maybeHeadState = store.getBlockStateIfAvailable(root);
    if (maybeJustifiedState.isPresent() && maybeHeadState.isPresent()) {
      return isHeadWeak(
          store, root, reorgThreshold, maybeHeadState.get(), maybeJustifiedState.get());
    }
    // Fail closed for late-reorg decisions: missing state means "do not treat the head as weak".
    return false;
  }

  /**
   * Determines whether the parent selected by {@code head} is strong using the child-aware Gloas
   * payload-status rules.
   *
   * <p>If the justified state or the parent payload status is not immediately available, Teku
   * returns {@code false}. That suppresses the late-reorg override rather than risking a false
   * positive that would incorrectly prefer the parent.
   */
  @Override
  public boolean isParentStrong(
      final ReadOnlyStore store, final SignedBeaconBlock head, final UInt64 parentThreshold) {
    final Optional<BeaconState> maybeJustifiedState = store.getJustifiedStateIfAvailable();
    final Optional<ForkChoicePayloadStatus> maybeParentPayloadStatus =
        getParentPayloadStatusIfAvailable(store, head.getMessage().getBlock());
    if (maybeJustifiedState.isPresent() && maybeParentPayloadStatus.isPresent()) {
      return isParentStrong(
          store,
          head.getParentRoot(),
          parentThreshold,
          maybeParentPayloadStatus.get(),
          maybeJustifiedState.get());
    }
    // Fail closed for late-reorg decisions: missing inputs mean "do not treat the parent as
    // strong".
    return false;
  }

  /**
   * Extended isParentStrong for Gloas with full attestation score using payload status.
   *
   * <p>Spec reference: is_parent_strong (Gloas override)
   *
   * <p>The Java signature carries `parentPayloadStatus` explicitly because the protoarray stores
   * the EMPTY/FULL/PENDING split as node identity rather than recomputing it inside the helper.
   */
  private boolean isParentStrong(
      final ReadOnlyStore store,
      final Bytes32 parentRoot,
      final UInt64 parentThreshold,
      final ForkChoicePayloadStatus parentPayloadStatus,
      final BeaconState justifiedState) {
    final UInt64 attestationScore =
        getNodeAttestationWeight(store, parentRoot, parentPayloadStatus, justifiedState);
    return attestationScore.isGreaterThan(parentThreshold);
  }

  /**
   * Determines the payload status of the parent block.
   *
   * <p>Spec reference:
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-get_parent_payload_status
   *
   * @param store the fork choice store
   * @param block the current block
   * @return PAYLOAD_STATUS_FULL if parent has full payload, PAYLOAD_STATUS_EMPTY otherwise
   */
  // get_parent_payload_status
  public SafeFuture<ForkChoicePayloadStatus> getParentPayloadStatus(
      final ReadOnlyStore store, final BeaconBlock block) {
    return store
        .retrieveBlock(block.getParentRoot())
        .thenApply(
            parentBlock -> {
              if (parentBlock.isEmpty()) {
                throw new IllegalStateException("Parent block not found: " + block.getParentRoot());
              }
              return getParentPayloadStatus(block, parentBlock.get());
            });
  }

  private Optional<ForkChoicePayloadStatus> getParentPayloadStatusIfAvailable(
      final ReadOnlyStore store, final BeaconBlock block) {
    return store
        .getBlockIfAvailable(block.getParentRoot())
        .map(parentBlock -> getParentPayloadStatus(block, parentBlock.getMessage().getBlock()));
  }

  private ForkChoicePayloadStatus getParentPayloadStatus(
      final BeaconBlock block, final BeaconBlock parentBlock) {
    final Optional<Bytes32> messageBlockHash =
        parentBlock
            .getBody()
            .toVersionGloas()
            .map(bodyGloas -> bodyGloas.getSignedExecutionPayloadBid().getMessage().getBlockHash());
    // If the parent is pre-Gloas there is no execution-state branch, so the child builds on EMPTY.
    if (messageBlockHash.isEmpty()) {
      return PAYLOAD_STATUS_EMPTY;
    }
    final Bytes32 parentBlockHash =
        BeaconBlockBodyGloas.required(block.getBody())
            .getSignedExecutionPayloadBid()
            .getMessage()
            .getParentBlockHash();
    return parentBlockHash.equals(messageBlockHash.get())
        ? PAYLOAD_STATUS_FULL
        : PAYLOAD_STATUS_EMPTY;
  }

  /**
   * Checks if the parent node has a full payload.
   *
   * <p>Spec reference:
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-is_parent_node_full
   *
   * @param store the fork choice store
   * @param block the current block
   * @return true if parent has full payload status
   */
  // is_parent_node_full
  SafeFuture<Boolean> isParentNodeFull(final ReadOnlyStore store, final BeaconBlock block) {
    return getParentPayloadStatus(store, block)
        .thenApply(parentPayloadStatus -> parentPayloadStatus == PAYLOAD_STATUS_FULL);
  }
}
