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
import static tech.pegasys.teku.spec.config.Constants.MAX_EQUIVOCATING_BLOCKS_PER_SLOT_AND_PROPOSER;
import static tech.pegasys.teku.spec.config.Constants.VALID_BLOCK_SET_SIZE;
import static tech.pegasys.teku.statetransition.validation.BlockGossipValidator.EquivocationCheckResult.EQUIVOCATING_BLOCK;
import static tech.pegasys.teku.statetransition.validation.BlockGossipValidator.EquivocationCheckResult.FIRST_BLOCK_FOR_SLOT_PROPOSER;
import static tech.pegasys.teku.statetransition.validation.BlockGossipValidator.EquivocationCheckResult.SAME_BLOCK_FOR_SLOT_PROPOSER;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ValidationResultSubCode.IGNORE_DUPLICATE;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ValidationResultSubCode.IGNORE_EQUIVOCATION_DETECTED;

import com.google.common.base.Objects;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode.ValidationResultSubCode;

public class BlockGossipValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final GossipValidationHelper gossipValidationHelper;
  private final ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher;

  private final Map<SlotAndProposer, Set<Bytes32>> receivedValidBlockInfoSet =
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
      LOG.trace("BlockValidator: Block is either too old. It will be dropped");
      return completedFuture(InternalValidationResult.IGNORE);
    }

    final Optional<ValidationResultSubCode> validationResultSubCode =
        switch (performBlockEquivocationCheck(block)) {
          case EQUIVOCATING_BLOCK -> Optional.of(IGNORE_EQUIVOCATION_DETECTED);
          case SAME_BLOCK_FOR_SLOT_PROPOSER -> Optional.of(
              ValidationResultSubCode.IGNORE_DUPLICATE);
          case FIRST_BLOCK_FOR_SLOT_PROPOSER -> Optional.empty();
        };

    if (validationResultSubCode.isPresent()) {
      return completedFuture(
          ignore(
              validationResultSubCode.get(),
              "Block is not the first with valid signature for its slot (%s) It will be dropped.",
              validationResultSubCode.get()));
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
                      ? performBlockEquivocationCheck(block)
                      : addAndPerformEquivocationCheck(block);

              return switch (secondEquivocationCheckResult) {
                case FIRST_BLOCK_FOR_SLOT_PROPOSER -> InternalValidationResult.ACCEPT;
                case EQUIVOCATING_BLOCK -> ignore(
                    IGNORE_EQUIVOCATION_DETECTED,
                    "Block is not the first with valid signature for its slot. It will be dropped.");
                case SAME_BLOCK_FOR_SLOT_PROPOSER -> ignore(
                    IGNORE_DUPLICATE,
                    "Block is not the first with valid signature for its slot. It will be dropped.");
              };
            });
  }

  private synchronized EquivocationCheckResult addAndPerformEquivocationCheck(
      final SignedBeaconBlock block) {
    final SlotAndProposer slotAndProposer = new SlotAndProposer(block);
    final Optional<Set<Bytes32>> maybeEquivocatingBlockRoots =
        Optional.ofNullable(receivedValidBlockInfoSet.get(slotAndProposer));

    if (maybeEquivocatingBlockRoots.isEmpty()) {
      final Set<Bytes32> newEquivocatingBlockRoots =
          LimitedSet.createNonSynchronized(MAX_EQUIVOCATING_BLOCKS_PER_SLOT_AND_PROPOSER);
      newEquivocatingBlockRoots.add(block.getRoot());
      receivedValidBlockInfoSet.put(slotAndProposer, newEquivocatingBlockRoots);
      return FIRST_BLOCK_FOR_SLOT_PROPOSER;
    }

    if (maybeEquivocatingBlockRoots.get().add(block.getRoot())) {
      return EQUIVOCATING_BLOCK;
    }

    return SAME_BLOCK_FOR_SLOT_PROPOSER;
  }

  synchronized EquivocationCheckResult performBlockEquivocationCheck(
      final SignedBeaconBlock block) {
    final SlotAndProposer slotAndProposer = new SlotAndProposer(block);
    final Optional<Set<Bytes32>> maybeEquivocatingBlockRoots =
        Optional.ofNullable(receivedValidBlockInfoSet.get(slotAndProposer));
    if (maybeEquivocatingBlockRoots.isEmpty()) {
      return FIRST_BLOCK_FOR_SLOT_PROPOSER;
    }
    if (maybeEquivocatingBlockRoots.get().contains(block.getRoot())) {
      return SAME_BLOCK_FOR_SLOT_PROPOSER;
    }

    return EQUIVOCATING_BLOCK;
  }

  enum EquivocationCheckResult {
    FIRST_BLOCK_FOR_SLOT_PROPOSER,
    SAME_BLOCK_FOR_SLOT_PROPOSER,
    EQUIVOCATING_BLOCK
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

  private static class SlotAndProposer {
    private final UInt64 slot;
    private final UInt64 proposerIndex;

    public SlotAndProposer(final SignedBeaconBlock block) {
      this.slot = block.getSlot();
      this.proposerIndex = block.getMessage().getProposerIndex();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SlotAndProposer)) {
        return false;
      }
      SlotAndProposer that = (SlotAndProposer) o;
      return Objects.equal(slot, that.slot) && Objects.equal(proposerIndex, that.proposerIndex);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(slot, proposerIndex);
    }
  }
}
