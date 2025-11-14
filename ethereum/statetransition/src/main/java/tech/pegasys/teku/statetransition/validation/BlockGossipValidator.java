/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.config.Constants.VALID_BLOCK_SET_SIZE;
import static tech.pegasys.teku.statetransition.validation.BlockGossipValidator.EquivocationCheckResult.BLOCK_ALREADY_SEEN_FOR_SLOT_PROPOSER;
import static tech.pegasys.teku.statetransition.validation.BlockGossipValidator.EquivocationCheckResult.EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER;
import static tech.pegasys.teku.statetransition.validation.BlockGossipValidator.EquivocationCheckResult.FIRST_BLOCK_FOR_SLOT_PROPOSER;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ValidationResultSubCode.IGNORE_ALREADY_SEEN;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ValidationResultSubCode.IGNORE_EQUIVOCATION_DETECTED;

import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;

public class BlockGossipValidator {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final GossipValidationHelper gossipValidationHelper;
  private final ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher;
  private final Map<SlotAndProposer, Bytes32> receivedValidBlockRoots =
      LimitedMap.createNonSynchronized(VALID_BLOCK_SET_SIZE);

  public BlockGossipValidator(
      final Spec spec,
      final GossipValidationHelper gossipValidationHelper,
      final ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher) {
    this.spec = spec;
    this.gossipValidationHelper = gossipValidationHelper;
    this.receivedBlockEventsChannelPublisher = receivedBlockEventsChannelPublisher;
  }

  /**
   * Perform gossip validation on a block.
   *
   * @param block the block to validate
   * @param markAsReceived whether to mark the block as received if it is valid (required for the
   *     equivocation check)
   */
  public SafeFuture<InternalValidationResult> validate(
      final SignedBeaconBlock block, final boolean markAsReceived) {
    return internalValidate(block, markAsReceived)
        .thenPeek(
            result -> {
              if (result.isAccept()) {
                receivedBlockEventsChannelPublisher.onBlockValidated(block);
              }
            });
  }

  private SafeFuture<InternalValidationResult> internalValidate(
      final SignedBeaconBlock block, final boolean markAsReceived) {

    // Phase 1: Perform cheap, stateless checks
    final Optional<InternalValidationResult> statelessValidationResult =
        performStatelessValidation(block);
    if (statelessValidationResult.isPresent()) {
      return completedFuture(statelessValidationResult.get());
    }

    // Phase 2: Load parent state and perform stateful checks
    final UInt64 parentSlot =
        gossipValidationHelper.getSlotForBlockRoot(block.getParentRoot()).orElseThrow();
    return gossipValidationHelper
        .getParentStateInBlockEpoch(parentSlot, block.getParentRoot(), block.getSlot())
        .thenApply(
            maybeParentState -> performStatefulValidation(block, maybeParentState, markAsReceived));
  }

  private Optional<InternalValidationResult> performStatelessValidation(
      final SignedBeaconBlock block) {
    return validateSlotIsAfterFinalized(block)
        .or(
            () ->
                convertEquivocationResultToValidationResult(
                    performBlockEquivocationCheck(false, block)))
        .or(() -> validateSlotIsNotFromFuture(block))
        .or(() -> validateBlockIsNew(block))
        .or(() -> validateParentIsAvailable(block))
        .or(() -> validateFinalizedCheckpointIsAncestor(block))
        .or(() -> validateParentSlot(block))
        .or(() -> validateKzgCommitments(block));
  }

  private InternalValidationResult performStatefulValidation(
      final SignedBeaconBlock block,
      final Optional<BeaconState> maybeParentState,
      final boolean markAsReceived) {

    if (maybeParentState.isEmpty()) {
      LOG.trace("Block was available but state wasn't. Must have been pruned by finalized.");
      return InternalValidationResult.IGNORE;
    }
    final BeaconState parentState = maybeParentState.get();

    return validateProposer(block, parentState)
        .or(() -> validatePayload(block, parentState))
        .or(() -> validateSignature(block, parentState))
        .or(
            () ->
                convertEquivocationResultToValidationResult(
                    performBlockEquivocationCheck(markAsReceived, block)))
        .orElse(InternalValidationResult.ACCEPT);
  }

  private Optional<InternalValidationResult> validateSlotIsAfterFinalized(
      final SignedBeaconBlock block) {
    /*
     * [IGNORE] The block is from a slot greater than the latest finalized slot -- i.e. validate that
     * signed_beacon_block.message.slot > compute_start_slot_at_epoch(store.finalized_checkpoint.epoch)
     * (a client MAY choose to validate and store such blocks for additional purposes
     * -- e.g. slashing detection, archive nodes, etc).
     */
    if (gossipValidationHelper.isSlotFinalized(block.getSlot())) {
      LOG.trace("BlockValidator: Block slot {} is finalized. Dropping.", block.getSlot());
      return Optional.of(InternalValidationResult.IGNORE);
    }
    return Optional.empty();
  }

  private Optional<InternalValidationResult> validateSlotIsNotFromFuture(
      final SignedBeaconBlock block) {
    /*
     * [IGNORE] The block is not from a future slot (with a MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance)
     * -- i.e. validate that signed_beacon_block.message.slot <= current_slot
     * (a client MAY queue future blocks for processing at the appropriate slot).
     */
    if (gossipValidationHelper.isSlotFromFuture(block.getSlot())) {
      LOG.trace("BlockValidator: Block is from the future. Saving for future processing.");
      return Optional.of(InternalValidationResult.SAVE_FOR_FUTURE);
    }
    return Optional.empty();
  }

  private Optional<InternalValidationResult> validateBlockIsNew(final SignedBeaconBlock block) {
    if (gossipValidationHelper.isBlockAvailable(block.getRoot())) {
      LOG.trace("Block is already imported.");
      return Optional.of(InternalValidationResult.IGNORE);
    }
    return Optional.empty();
  }

  private Optional<InternalValidationResult> validateParentIsAvailable(
      final SignedBeaconBlock block) {
    /*
     * [REJECT] The block's parent (defined by block.parent_root) passes validation.
     */
    if (!gossipValidationHelper.isBlockAvailable(block.getParentRoot())) {
      LOG.trace("Block parent is not available. Saving for future processing.");
      return Optional.of(InternalValidationResult.SAVE_FOR_FUTURE);
    }
    return Optional.empty();
  }

  private Optional<InternalValidationResult> validateFinalizedCheckpointIsAncestor(
      final SignedBeaconBlock block) {
    /*
     * [REJECT] The current finalized_checkpoint is an ancestor of block
     * -- i.e. get_checkpoint_block(store, block.parent_root, store.finalized_checkpoint.epoch) == store.finalized_checkpoint.root
     */
    if (!gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
        block.getSlot(), block.getParentRoot())) {
      return Optional.of(reject("Block does not descend from finalized checkpoint"));
    }
    return Optional.empty();
  }

  private Optional<InternalValidationResult> validateParentSlot(final SignedBeaconBlock block) {
    /*
     * [IGNORE] The block's parent (defined by block.parent_root) has been seen (via gossip or non-gossip sources)
     * (a client MAY queue blocks for processing once the parent block is retrieved).
     */
    final Optional<UInt64> maybeParentBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(block.getParentRoot());
    if (maybeParentBlockSlot.isEmpty()) {
      LOG.trace("BlockValidator: Parent block does not exist. Saving for future processing.");
      return Optional.of(InternalValidationResult.SAVE_FOR_FUTURE);
    }
    /*
     * [REJECT] The block is from a higher slot than its parent.
     */
    if (maybeParentBlockSlot.get().isGreaterThanOrEqualTo(block.getSlot())) {
      return Optional.of(reject("Parent block is after child block."));
    }
    return Optional.empty();
  }

  private Optional<InternalValidationResult> validateKzgCommitments(final SignedBeaconBlock block) {
    /*
     * [REJECT] The length of KZG commitments is less than or equal to the limitation defined in Consensus Layer
     */
    final Optional<SszList<SszKZGCommitment>> blobKzgCommitmentsCount =
        block.getMessage().getBody().getOptionalBlobKzgCommitments();
    if (blobKzgCommitmentsCount.isPresent()) {
      final Integer maxBlobsPerBlock =
          spec.getMaxBlobsPerBlockAtSlot(block.getSlot()).orElseThrow();
      if (blobKzgCommitmentsCount.get().size() > maxBlobsPerBlock) {
        LOG.trace(
            "BlockValidator: Block has {} kzg commitments, max allowed {}",
            blobKzgCommitmentsCount,
            maxBlobsPerBlock);
        return Optional.of(
            reject(
                "Block has %d kzg commitments, max allowed %d",
                blobKzgCommitmentsCount.get().size(), maxBlobsPerBlock));
      }
    }
    return Optional.empty();
  }

  private Optional<InternalValidationResult> validateProposer(
      final SignedBeaconBlock block, final BeaconState state) {
    /*
     * [REJECT] The block is proposed by the expected proposer_index for the block's slot in the context
     * of the current shuffling (defined by parent_root/slot).
     * If the proposer_index cannot immediately be verified against the expected shuffling,
     * the block MAY be queued for later processing while proposers for the block's branch are calculated
     * -- in such a case do not REJECT, instead IGNORE this message.
     */
    if (!gossipValidationHelper.isProposerTheExpectedProposer(
        block.getProposerIndex(), block.getSlot(), state)) {
      return Optional.of(
          reject("Block proposed by incorrect proposer (%s)", block.getProposerIndex()));
    }
    return Optional.empty();
  }

  private Optional<InternalValidationResult> validatePayload(
      final SignedBeaconBlock block, final BeaconState state) {
    final MiscHelpers miscHelpers = spec.atSlot(block.getSlot()).miscHelpers();
    final Optional<SignedExecutionPayloadBid> maybeSignedExecutionPayloadBid =
        block.getMessage().getBody().getOptionalSignedExecutionPayloadBid();

    if (maybeSignedExecutionPayloadBid.isPresent()) {
      final ExecutionPayloadBid executionPayloadBid =
          maybeSignedExecutionPayloadBid.get().getMessage();
      final BeaconStateGloas parentStateGloas = BeaconStateGloas.required(state);
      /*
       * [REJECT] The block's execution payload parent (defined by bid.parent_block_hash) passes all validation
       */
      if (!executionPayloadBid.getParentBlockHash().equals(parentStateGloas.getLatestBlockHash())) {
        return Optional.of(
            reject(
                "Execution payload bid has invalid parent block hash %s, expecting %s",
                executionPayloadBid.getParentBlockHash().toHexString(),
                parentStateGloas.getLatestBlockHash().toHexString()));
      }
      /*
       * [REJECT] The bid's parent (defined by bid.parent_block_root) equals the block's parent (defined by block.parent_root)
       */
      if (!executionPayloadBid.getParentBlockRoot().equals(block.getParentRoot())) {
        return Optional.of(
            reject(
                "Execution payload has invalid parent block root %s, expecting %s",
                executionPayloadBid.getParentBlockRoot().toHexString(),
                block.getParentRoot().toHexString()));
      }
    } else if (miscHelpers.isMergeTransitionComplete(state)) {
      final Optional<ExecutionPayload> payload =
          block.getMessage().getBody().getOptionalExecutionPayload();
      if (payload.isEmpty()) {
        return Optional.of(reject("Missing execution payload"));
      }
      /*
       * [REJECT] The block's execution payload timestamp is correct with respect to the slot
       * -- i.e. execution_payload.timestamp == compute_time_at_slot(state, block.slot)
       */
      if (payload.get().getTimestamp().compareTo(spec.computeTimeAtSlot(state, block.getSlot()))
          != 0) {
        return Optional.of(
            reject("Execution Payload timestamp is not consistent with block slot time"));
      }
    }
    return Optional.empty();
  }

  private Optional<InternalValidationResult> validateSignature(
      final SignedBeaconBlock block, final BeaconState state) {
    /*
     * [REJECT] The proposer signature, signed_beacon_block.signature, is valid with respect to the proposer_index pubkey.
     */
    if (!blockSignatureIsValidWithRespectToProposerIndex(block, state)) {
      return Optional.of(reject("Block signature is invalid"));
    }
    return Optional.empty();
  }

  private Optional<InternalValidationResult> convertEquivocationResultToValidationResult(
      final EquivocationCheckResult equivocationCheckResult) {
    final InternalValidationResult result =
        equivocationCheckResultToInternalValidationResult(equivocationCheckResult);
    return result.isAccept() ? Optional.empty() : Optional.of(result);
  }

  private InternalValidationResult equivocationCheckResultToInternalValidationResult(
      final EquivocationCheckResult equivocationCheckResult) {
    /*
     * [IGNORE] The block is the first block with valid signature received for the proposer for the slot, signed_beacon_block.message.slot
     */
    return switch (equivocationCheckResult) {
      case FIRST_BLOCK_FOR_SLOT_PROPOSER -> InternalValidationResult.ACCEPT;
      case EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER ->
          ignore(IGNORE_EQUIVOCATION_DETECTED, "Equivocating block detected. It will be dropped.");
      case BLOCK_ALREADY_SEEN_FOR_SLOT_PROPOSER ->
          ignore(
              IGNORE_ALREADY_SEEN,
              "Block is not the first with valid signature for its slot. It will be dropped.");
    };
  }

  synchronized EquivocationCheckResult performBlockEquivocationCheck(
      final boolean markAsReceived, final SignedBeaconBlock block) {
    final SlotAndProposer slotAndProposer = new SlotAndProposer(block);
    final Bytes32 blockRoot = block.getRoot();
    return Optional.ofNullable(receivedValidBlockRoots.get(slotAndProposer))
        .map(
            previouslySeenBlockRoot -> {
              if (previouslySeenBlockRoot.equals(blockRoot)) {
                return BLOCK_ALREADY_SEEN_FOR_SLOT_PROPOSER;
              }
              return EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER;
            })
        .orElseGet(
            () -> {
              if (markAsReceived) {
                receivedValidBlockRoots.put(slotAndProposer, blockRoot);
              }
              return FIRST_BLOCK_FOR_SLOT_PROPOSER;
            });
  }

  public enum EquivocationCheckResult {
    FIRST_BLOCK_FOR_SLOT_PROPOSER,
    BLOCK_ALREADY_SEEN_FOR_SLOT_PROPOSER,
    EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER
  }

  private boolean blockSignatureIsValidWithRespectToProposerIndex(
      final SignedBeaconBlock block, final BeaconState postState) {
    final Bytes32 domain =
        spec.getDomain(
            Domain.BEACON_PROPOSER,
            spec.getCurrentEpoch(postState),
            postState.getFork(),
            postState.getGenesisValidatorsRoot());
    final Bytes signingRoot = spec.computeSigningRoot(block.getMessage(), domain);
    return gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
        signingRoot, block.getProposerIndex(), block.getSignature(), postState);
  }

  private record SlotAndProposer(UInt64 slot, UInt64 proposerIndex) {
    public SlotAndProposer(final SignedBeaconBlock block) {
      this(block.getSlot(), block.getMessage().getProposerIndex());
    }
  }
}
