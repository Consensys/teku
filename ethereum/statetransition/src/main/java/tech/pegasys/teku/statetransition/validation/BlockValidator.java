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
import static tech.pegasys.teku.spec.config.Constants.MAXIMUM_GOSSIP_CLOCK_DISPARITY;
import static tech.pegasys.teku.spec.config.Constants.VALID_BLOCK_SET_SIZE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import com.google.common.base.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final Set<SlotAndProposer> receivedValidBlockInfoSet =
      LimitedSet.createSynchronized(VALID_BLOCK_SET_SIZE);

  public BlockValidator(final Spec spec, RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  public SafeFuture<InternalValidationResult> validate(
      final SignedBeaconBlock block, final Optional<BlobsSidecar> blobsSidecar) {

    if (!blockSlotIsGreaterThanLatestFinalizedSlot(block)
        || !blockIsFirstBlockWithValidSignatureForSlot(block)) {
      LOG.trace(
          "BlockValidator: Block is either too old or is not the first block with valid signature for "
              + "its slot. It will be dropped");
      return completedFuture(InternalValidationResult.IGNORE);
    }

    if (blockIsFromFutureSlot(block)) {
      LOG.trace("BlockValidator: Block is from the future. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }

    if (recentChainData.containsBlock(block.getRoot())) {
      LOG.trace("Block is already imported");
      return completedFuture(InternalValidationResult.IGNORE);
    }

    if (!recentChainData.containsBlock(block.getParentRoot())) {
      LOG.trace("Block parent is not available. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }

    if (!currentFinalizedCheckpointIsAncestorOfBlock(block)) {
      return completedFuture(reject("Block does not descend from finalized checkpoint"));
    }

    final Optional<UInt64> maybeParentBlockSlot =
        recentChainData.getSlotForBlockRoot(block.getParentRoot());
    if (maybeParentBlockSlot.isEmpty()) {
      LOG.trace(
          "BlockValidator: Parent block does not exist. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }
    final UInt64 parentBlockSlot = maybeParentBlockSlot.get();

    if (parentBlockSlot.isGreaterThanOrEqualTo(block.getSlot())) {
      return completedFuture(reject("Parent block is after child block."));
    }

    if (blobsSidecar.isPresent()) {
      if (blobsSidecar.get().getBeaconBlockRoot().equals(block.getRoot())) {
        return completedFuture(InternalValidationResult.IGNORE);
      }

      if (blobsSidecar.get().getBeaconBlockSlot().equals(block.getSlot())) {
        return completedFuture(InternalValidationResult.IGNORE);
      }
    }

    return getParentStateInBlockEpoch(block, parentBlockSlot)
        .thenApply(
            maybePostState -> {
              if (maybePostState.isEmpty()) {
                LOG.trace(
                    "Block was available but state wasn't. Must have been pruned by finalized.");
                return InternalValidationResult.IGNORE;
              }
              final BeaconState postState = maybePostState.get();

              if (!blockIsProposedByTheExpectedProposer(block, postState)) {
                return reject(
                    "Block proposed by incorrect proposer (%s)", block.getProposerIndex());
              }
              if (spec.atSlot(block.getSlot()).miscHelpers().isMergeTransitionComplete(postState)) {
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

              // TODO kzg validation

              return InternalValidationResult.ACCEPT;
            });
  }

  /**
   * Retrieve the state for the parent block, applying the epoch transition if required to be able
   * to calculate the expected proposer for block.
   */
  private SafeFuture<Optional<BeaconState>> getParentStateInBlockEpoch(
      final SignedBeaconBlock block, final UInt64 parentBlockSlot) {
    final UInt64 firstSlotInBlockEpoch =
        spec.computeStartSlotAtEpoch(spec.computeEpochAtSlot(block.getSlot()));
    return parentBlockSlot.isLessThan(firstSlotInBlockEpoch)
        ? recentChainData.retrieveStateAtSlot(
            new SlotAndBlockRoot(firstSlotInBlockEpoch, block.getParentRoot()))
        : recentChainData.retrieveBlockState(block.getParentRoot());
  }

  private boolean blockIsFromFutureSlot(SignedBeaconBlock block) {
    ReadOnlyStore store = recentChainData.getStore();
    final long disparityInSeconds = Math.round((float) MAXIMUM_GOSSIP_CLOCK_DISPARITY / 1000.0);
    final UInt64 maxOffset = UInt64.valueOf(disparityInSeconds);
    final UInt64 maxTime = store.getTimeSeconds().plus(maxOffset);
    UInt64 maxCurrSlot = spec.getCurrentSlot(maxTime, store.getGenesisTime());
    return block.getSlot().compareTo(maxCurrSlot) > 0;
  }

  private boolean blockSlotIsGreaterThanLatestFinalizedSlot(SignedBeaconBlock block) {
    UInt64 finalizedSlot =
        recentChainData.getStore().getFinalizedCheckpoint().getEpochStartSlot(spec);
    return block.getSlot().compareTo(finalizedSlot) > 0;
  }

  private boolean blockIsFirstBlockWithValidSignatureForSlot(SignedBeaconBlock block) {
    return !receivedValidBlockInfoSet.contains(new SlotAndProposer(block));
  }

  private boolean blockSignatureIsValidWithRespectToProposerIndex(
      SignedBeaconBlock block, BeaconState postState) {
    final Bytes32 domain =
        spec.getDomain(
            Domain.BEACON_PROPOSER,
            spec.getCurrentEpoch(postState),
            postState.getFork(),
            postState.getGenesisValidatorsRoot());
    final Bytes signingRoot = spec.computeSigningRoot(block.getMessage(), domain);
    final BLSSignature signature = block.getSignature();

    boolean signatureValid =
        spec.getValidatorPubKey(postState, block.getMessage().getProposerIndex())
            .map(publicKey -> BLS.verify(publicKey, signingRoot, signature))
            .orElse(false);

    return signatureValid && receivedValidBlockInfoSet.add(new SlotAndProposer(block));
  }

  private boolean blockIsProposedByTheExpectedProposer(
      SignedBeaconBlock block, BeaconState postState) {
    final int proposerIndex = spec.getBeaconProposerIndex(postState, block.getSlot());
    return proposerIndex == block.getMessage().getProposerIndex().longValue();
  }

  private boolean currentFinalizedCheckpointIsAncestorOfBlock(SignedBeaconBlock block) {
    return spec.blockDescendsFromLatestFinalizedBlock(
        block.getMessage(),
        recentChainData.getStore(),
        recentChainData.getForkChoiceStrategy().orElseThrow());
  }



  private static class SlotAndProposer {
    private final UInt64 slot;
    private final UInt64 proposerIndex;

    public SlotAndProposer(SignedBeaconBlock block) {
      this.slot = block.getSlot();
      this.proposerIndex = block.getMessage().getProposerIndex();
    }

    @Override
    public boolean equals(Object o) {
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
