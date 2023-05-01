/*
 * Copyright ConsenSys Software Inc., 2022
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
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import com.google.common.base.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final GossipValidationHelper gossipValidationHelper;
  private final Set<SlotAndProposer> receivedValidBlockInfoSet =
      LimitedSet.createSynchronized(VALID_BLOCK_SET_SIZE);

  public BlockValidator(
      final Spec spec,
      final RecentChainData recentChainData,
      final GossipValidationHelper gossipValidationHelper) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.gossipValidationHelper = gossipValidationHelper;
  }

  public SafeFuture<InternalValidationResult> validate(final SignedBeaconBlock block) {

    if (gossipValidationHelper.isSlotFinalized(block.getSlot())
        || !blockIsFirstBlockWithValidSignatureForSlot(block)) {
      LOG.trace(
          "BlockValidator: Block is either too old or is not the first block with valid signature for "
              + "its slot. It will be dropped");
      return completedFuture(InternalValidationResult.IGNORE);
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

    if (!currentFinalizedCheckpointIsAncestorOfBlock(block)) {
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

              if (!receivedValidBlockInfoSet.add(new SlotAndProposer(block))) {
                return ignore(
                    "Block is not the first with valid signature for its slot. It will be dropped.");
              }

              return InternalValidationResult.ACCEPT;
            });
  }

  private boolean blockIsFirstBlockWithValidSignatureForSlot(final SignedBeaconBlock block) {
    return !receivedValidBlockInfoSet.contains(new SlotAndProposer(block));
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

  private boolean currentFinalizedCheckpointIsAncestorOfBlock(final SignedBeaconBlock block) {
    return spec.blockDescendsFromLatestFinalizedBlock(
        block.getMessage(),
        recentChainData.getStore(),
        recentChainData.getForkChoiceStrategy().orElseThrow());
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
