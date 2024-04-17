/*
 * Copyright Consensys Software Inc., 2022
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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;

public class BlockGossipValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final GossipValidationHelper gossipValidationHelper;
  private final ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher;

  private final Map<SlotAndProposer, Bytes32> receivedValidBlockInfoSet =
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
   * @param isLocallyProduced whether the block was produced locally or received from gossip. The
   *     locally produced flow applies only during broadcast validation.
   */
  public SafeFuture<InternalValidationResult> validate(
      final SignedBeaconBlock block, final boolean isLocallyProduced) {
    return internalValidate(block, isLocallyProduced)
        .thenPeek(
            result -> {
              if (result.isAccept()) {
                receivedBlockEventsChannelPublisher.onBlockValidated(block);
              }
            });
  }

  private SafeFuture<InternalValidationResult> internalValidate(
      final SignedBeaconBlock block, final boolean isLocallyProduced) {

    if (gossipValidationHelper.isSlotFinalized(block.getSlot())) {
      LOG.trace("BlockValidator: Block is too old. It will be dropped");
      return completedFuture(InternalValidationResult.IGNORE);
    }

    final InternalValidationResult intermediateValidationResult =
        equivocationCheckResultToInternalValidationResult(performBlockEquivocationCheck(block));

    if (!intermediateValidationResult.isAccept()) {
      return completedFuture(intermediateValidationResult);
    }

    if (gossipValidationHelper.isSlotFromFuture(block.getSlot())) {
      LOG.trace("BlockValidator: Block is from the future. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }

    if (gossipValidationHelper.isBlockAvailable(block.getRoot())) {
      LOG.trace("Block is already imported");
      return completedFuture(InternalValidationResult.IGNORE);
    }

    if (!gossipValidationHelper.isBlockAvailable(block.getParentRoot())) {
      LOG.trace("Block parent is not available. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }

    if (!gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
        block.getSlot(), block.getParentRoot())) {
      return completedFuture(reject("Block does not descend from finalized checkpoint"));
    }

    final Optional<UInt64> maybeParentBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(block.getParentRoot());
    if (maybeParentBlockSlot.isEmpty()) {
      LOG.trace(
          "BlockValidator: Parent block does not exist. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }
    final UInt64 parentBlockSlot = maybeParentBlockSlot.get();

    if (parentBlockSlot.isGreaterThanOrEqualTo(block.getSlot())) {
      return completedFuture(reject("Parent block is after child block."));
    }

    return gossipValidationHelper
        .getParentStateInBlockEpoch(parentBlockSlot, block.getParentRoot(), block.getSlot())
        .thenApply(
            maybePostState -> {
              if (maybePostState.isEmpty()) {
                LOG.trace(
                    "Block was available but state wasn't. Must have been pruned by finalized.");
                return InternalValidationResult.IGNORE;
              }
              final BeaconState postState = maybePostState.get();

              if (!gossipValidationHelper.isProposerTheExpectedProposer(
                  block.getProposerIndex(), block.getSlot(), postState)) {
                return reject(
                    "Block proposed by incorrect proposer (%s)", block.getProposerIndex());
              }
              final MiscHelpers miscHelpers = spec.atSlot(block.getSlot()).miscHelpers();

              if (miscHelpers.isMergeTransitionComplete(postState)) {
                Optional<ExecutionPayload> executionPayload =
                    block.getMessage().getBody().getOptionalExecutionPayload();

                if (executionPayload.isEmpty()) {
                  return reject("Missing execution payload");
                }

                if (executionPayload
                        .get()
                        .getTimestamp()
                        .compareTo(spec.computeTimeAtSlot(postState, block.getSlot()))
                    != 0) {
                  return reject(
                      "Execution Payload timestamp is not consistence with and block slot time");
                }
              }

              if (!blockSignatureIsValidWithRespectToProposerIndex(block, postState)) {
                return reject("Block signature is invalid");
              }

              // We want to add blocks in the infoSet only when they come from gossip.
              // When dealing with locally produced block, we only want to check them against seen
              // block coming from gossip because we may have to do two equivocation checks if
              // BroadcastValidationLevel.CONSENSUS_EQUIVOCATION is used (one at the beginning of
              // the blocks import,
              // another after consensus validation)
              final EquivocationCheckResult secondEquivocationCheckResult =
                  isLocallyProduced
                      ? performBlockEquivocationCheck(false, block)
                      : performBlockEquivocationCheck(true, block);

              return equivocationCheckResultToInternalValidationResult(
                  secondEquivocationCheckResult);
            });
  }

  private InternalValidationResult equivocationCheckResultToInternalValidationResult(
      final EquivocationCheckResult equivocationCheckResult) {
    return switch (equivocationCheckResult) {
      case FIRST_BLOCK_FOR_SLOT_PROPOSER -> InternalValidationResult.ACCEPT;
      case EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER -> ignore(
          IGNORE_EQUIVOCATION_DETECTED, "Equivocating block detected. It will be dropped.");
      case BLOCK_ALREADY_SEEN_FOR_SLOT_PROPOSER -> ignore(
          IGNORE_ALREADY_SEEN,
          "Block is not the first with valid signature for its slot. It will be dropped.");
    };
  }

  private synchronized EquivocationCheckResult performBlockEquivocationCheck(
      final boolean add, final SignedBeaconBlock block) {
    final SlotAndProposer slotAndProposer = new SlotAndProposer(block);

    final Optional<Bytes32> maybePreviouslySeenBlockRoot =
        Optional.ofNullable(receivedValidBlockInfoSet.get(slotAndProposer));

    if (maybePreviouslySeenBlockRoot.isEmpty()) {
      if (add) {
        receivedValidBlockInfoSet.put(slotAndProposer, block.getRoot());
      }
      return FIRST_BLOCK_FOR_SLOT_PROPOSER;
    }

    if (maybePreviouslySeenBlockRoot.get().equals(block.getRoot())) {
      return BLOCK_ALREADY_SEEN_FOR_SLOT_PROPOSER;
    }

    return EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER;
  }

  EquivocationCheckResult performBlockEquivocationCheck(final SignedBeaconBlock block) {
    return performBlockEquivocationCheck(false, block);
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
